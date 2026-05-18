package autonomo.util

import autonomo.config.JsonSupport
import autonomo.domain.BudgetEntry
import autonomo.domain.Expense
import autonomo.domain.Invoice
import autonomo.domain.Money
import autonomo.domain.MonthKey
import autonomo.domain.RegularSpending
import autonomo.domain.RegularSpendingCadence
import autonomo.domain.RegularSpendingScheduleType
import autonomo.domain.StatePayment
import autonomo.domain.Transfer
import autonomo.model.RecordType
import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

object RecordPayloadParser {
    fun parse(recordType: RecordType, payload: JsonNode): Any {
        return when (recordType) {
            RecordType.INVOICE -> JsonSupport.mapper.treeToValue(payload, Invoice::class.java)
            RecordType.EXPENSE -> JsonSupport.mapper.treeToValue(payload, Expense::class.java)
            RecordType.STATE_PAYMENT -> JsonSupport.mapper.treeToValue(payload, StatePayment::class.java)
            RecordType.TRANSFER -> JsonSupport.mapper.treeToValue(payload, Transfer::class.java)
            RecordType.BUDGET -> parseBudgetEntry(payload)
            RecordType.REGULAR_SPENDING -> parseRegularSpending(payload)
        }
    }

    fun eventDate(recordType: RecordType, payload: Any): LocalDate {
        return when (recordType) {
            RecordType.INVOICE -> {
                val invoice = payload as Invoice
                invoice.paymentDate ?: invoice.invoiceDate
            }
            RecordType.EXPENSE -> {
                val expense = payload as Expense
                expense.paymentDate ?: expense.documentDate
            }
            RecordType.STATE_PAYMENT -> (payload as StatePayment).paymentDate
            RecordType.TRANSFER -> (payload as Transfer).date
            RecordType.BUDGET -> (payload as BudgetEntry).monthKey.firstDay()
            RecordType.REGULAR_SPENDING -> (payload as RegularSpending).startDate
        }
    }

    fun toJson(payload: Any): String =
        when (payload) {
            is BudgetEntry -> JsonSupport.mapper.writeValueAsString(payload.toNormalizedJson())
            is RegularSpending -> JsonSupport.mapper.writeValueAsString(payload.toNormalizedJson())
            else -> JsonSupport.mapper.writeValueAsString(payload)
        }

    private fun parseBudgetEntry(payload: JsonNode): BudgetEntry {
        val monthKey = payload.requiredField("monthKey").asMonthKey("monthKey")
        val spentNode = payload.get("spent") ?: payload.get("plannedSpend")
            ?: throw IllegalArgumentException("spent is required")
        val spent = spentNode.asMoney("spent")
        val earned = payload.requiredField("earned").asMoney("earned")
        val targetSpend = payload.get("targetSpend")?.takeUnless { it.isNull }?.asMoney("targetSpend")
        val exceptionalSpend = payload.get("exceptionalSpend")?.takeUnless { it.isNull }?.asMoney("exceptionalSpend")
        val notes = payload.optionalText("notes") ?: payload.optionalText("description")
        val exceptionalNotes = payload.optionalText("exceptionalNotes")

        return BudgetEntry(
            monthKey = monthKey,
            spent = spent,
            earned = earned,
            targetSpend = targetSpend,
            notes = notes,
            exceptionalSpend = exceptionalSpend,
            exceptionalNotes = exceptionalNotes
        )
    }

    private fun parseRegularSpending(payload: JsonNode): RegularSpending {
        val name = payload.requiredField("name").asText().trim()
        val startDate = payload.requiredField("startDate").asLocalDate("startDate")
        val amount = payload.requiredField("amount").asMoney("amount")
        val scheduleType = payload.get("scheduleType")
            ?.takeUnless { it.isNull }
            ?.asEnum<RegularSpendingScheduleType>("scheduleType")
            ?: RegularSpendingScheduleType.ONGOING

        val cadence = payload.get("cadence")
            ?.takeUnless { it.isNull }
            ?.asEnum<RegularSpendingCadence>("cadence")

        val paymentCount = payload.get("paymentCount")
            ?.takeUnless { it.isNull }
            ?.asPositiveInt("paymentCount")

        return when (scheduleType) {
            RegularSpendingScheduleType.ONGOING -> {
                if (paymentCount != null) {
                    throw IllegalArgumentException("paymentCount is not allowed for ongoing regular spendings")
                }
                RegularSpending(
                    name = name,
                    startDate = startDate,
                    cadence = cadence ?: throw IllegalArgumentException("cadence is required for ongoing regular spendings"),
                    amount = amount,
                    scheduleType = scheduleType,
                    paymentCount = null
                )
            }
            RegularSpendingScheduleType.FIXED_TERM -> {
                if (cadence != null && cadence != RegularSpendingCadence.MONTHLY) {
                    throw IllegalArgumentException("cadence must be omitted or MONTHLY for fixed-term regular spendings")
                }
                RegularSpending(
                    name = name,
                    startDate = startDate,
                    cadence = cadence,
                    amount = amount,
                    scheduleType = scheduleType,
                    paymentCount = paymentCount
                        ?: throw IllegalArgumentException("paymentCount is required for fixed-term regular spendings")
                )
            }
        }
    }

    private fun JsonNode.requiredField(name: String): JsonNode =
        get(name) ?: throw IllegalArgumentException("$name is required")

    private fun JsonNode.optionalText(name: String): String? =
        get(name)?.takeUnless { it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonNode.asMonthKey(fieldName: String): MonthKey {
        val text = asText()
        return runCatching { MonthKey(YearMonth.parse(text)) }
            .getOrElse { throw IllegalArgumentException("$fieldName must be YYYY-MM") }
    }

    private fun JsonNode.asLocalDate(fieldName: String): LocalDate {
        val text = asText()
        return runCatching { LocalDate.parse(text) }
            .getOrElse { throw IllegalArgumentException("$fieldName must be YYYY-MM-DD") }
    }

    private inline fun <reified T : Enum<T>> JsonNode.asEnum(fieldName: String): T {
        val text = asText()
        return runCatching { enumValueOf<T>(text) }
            .getOrElse { throw IllegalArgumentException("$fieldName is invalid") }
    }

    private fun JsonNode.asPositiveInt(fieldName: String): Int {
        if (!isIntegralNumber || !canConvertToInt()) {
            throw IllegalArgumentException("$fieldName must be an integer >= 1")
        }
        val value = intValue()
        if (value < 1) {
            throw IllegalArgumentException("$fieldName must be an integer >= 1")
        }
        return value
    }

    private fun JsonNode.asMoney(fieldName: String): Money {
        val amount = when {
            isNumber -> decimalValue()
            isTextual -> runCatching { BigDecimal(asText()) }
                .getOrElse { throw IllegalArgumentException("$fieldName must be a number") }
            isObject && has("amount") -> get("amount").asMoney(fieldName).amount
            else -> throw IllegalArgumentException("$fieldName must be a number")
        }
        return Money(amount)
    }

    private fun BudgetEntry.toNormalizedJson(): JsonNode {
        val obj = JsonSupport.mapper.createObjectNode()
        obj.put("monthKey", monthKey.toString())
        obj.put("spent", spent.amount)
        obj.put("earned", earned.amount)
        targetSpend?.let { obj.put("targetSpend", it.amount) }
        notes?.let { obj.put("notes", it) }
        exceptionalSpend?.let { obj.put("exceptionalSpend", it.amount) }
        exceptionalNotes?.let { obj.put("exceptionalNotes", it) }
        return obj
    }

    private fun RegularSpending.toNormalizedJson(): JsonNode {
        val obj = JsonSupport.mapper.createObjectNode()
        obj.put("name", name)
        obj.put("startDate", startDate.toString())
        obj.put("scheduleType", scheduleType.name)
        when (scheduleType) {
            RegularSpendingScheduleType.ONGOING -> {
                obj.put("cadence", cadence!!.name)
            }
            RegularSpendingScheduleType.FIXED_TERM -> {
                obj.put("paymentCount", paymentCount!!)
            }
        }
        obj.put("amount", amount.amount)
        return obj
    }
}
