package autonomo.util

import autonomo.config.JsonSupport
import autonomo.domain.BalanceAccount
import autonomo.domain.BalanceMovement
import autonomo.domain.BudgetEntry
import autonomo.domain.BusinessEntityInvoiceType
import autonomo.domain.ExchangeRateSource
import autonomo.domain.Expense
import autonomo.domain.Invoice
import autonomo.domain.Money
import autonomo.domain.MonthKey
import autonomo.domain.RegularSpending
import autonomo.domain.RegularSpendingCadence
import autonomo.domain.RegularSpendingScheduleType
import autonomo.domain.StatePayment
import autonomo.domain.Transfer
import autonomo.domain.UkrainianFopInvoice
import autonomo.model.RecordType
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

object RecordPayloadParser {
    data class ParsedBusinessEntityInvoice(
        val invoice: UkrainianFopInvoice,
        val exchangeRateDate: LocalDate,
        val exchangeRateFetchedAt: Instant?
    )

    fun parse(recordType: RecordType, payload: JsonNode): Any {
        return when (recordType) {
            RecordType.INVOICE -> JsonSupport.mapper.treeToValue(payload, Invoice::class.java)
            RecordType.EXPENSE -> JsonSupport.mapper.treeToValue(payload, Expense::class.java)
            RecordType.STATE_PAYMENT -> JsonSupport.mapper.treeToValue(payload, StatePayment::class.java)
            RecordType.TRANSFER -> parseBalanceMovement(payload)
            RecordType.BUDGET -> parseBudgetEntry(payload)
            RecordType.REGULAR_SPENDING -> parseRegularSpending(payload)
            RecordType.BUSINESS_ENTITY_INVOICE -> parseBusinessEntityInvoice(payload)
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
            RecordType.TRANSFER -> when (payload) {
                is BalanceMovement -> payload.date
                is Transfer -> payload.date
                else -> throw IllegalArgumentException("Transfer payload is invalid")
            }
            RecordType.BUDGET -> (payload as BudgetEntry).monthKey.firstDay()
            RecordType.REGULAR_SPENDING -> (payload as RegularSpending).startDate
            RecordType.BUSINESS_ENTITY_INVOICE -> (payload as ParsedBusinessEntityInvoice).invoice.receivedDate
        }
    }

    fun toJson(payload: Any): String =
        when (payload) {
            is BalanceMovement -> JsonSupport.mapper.writeValueAsString(payload.toNormalizedJson())
            is BudgetEntry -> JsonSupport.mapper.writeValueAsString(payload.toNormalizedJson())
            is RegularSpending -> JsonSupport.mapper.writeValueAsString(payload.toNormalizedJson())
            is ParsedBusinessEntityInvoice -> JsonSupport.mapper.writeValueAsString(payload.toNormalizedJson())
            else -> JsonSupport.mapper.writeValueAsString(payload)
        }

    fun parseBalanceMovement(payload: JsonNode): BalanceMovement {
        val hasOperation = payload.hasNonNull("operation")
        val hasMovementType = payload.hasNonNull("movementType")
        val hasInternalFields = payload.hasNonNull("fromAccountId") || payload.hasNonNull("toAccountId")

        if (hasOperation && (hasMovementType || hasInternalFields)) {
            throw IllegalArgumentException("TRANSFER payload cannot mix operation with internal transfer fields")
        }

        return if (hasMovementType || hasInternalFields) {
            val movementType = payload.requiredField("movementType").asText()
            require(movementType == "InternalTransfer") { "movementType is invalid" }
            BalanceMovement.InternalTransfer(
                date = payload.requiredField("date").asLocalDate("date"),
                fromAccountId = payload.requiredField("fromAccountId").asText().trim(),
                toAccountId = payload.requiredField("toAccountId").asText().trim(),
                amount = payload.requiredField("amount").asMoney("amount"),
                note = payload.optionalText("note")
            )
        } else {
            val transfer = JsonSupport.mapper.treeToValue(payload, Transfer::class.java)
            BalanceMovement.External(
                date = transfer.date,
                operation = transfer.operation,
                amount = transfer.amount,
                accountId = transfer.accountId ?: BalanceAccount.MAIN_ACCOUNT_ID,
                note = transfer.note
            )
        }
    }

    private fun parseBusinessEntityInvoice(payload: JsonNode): ParsedBusinessEntityInvoice {
        val invoiceType = payload.requiredField("invoiceType").asText()
        require(invoiceType == BusinessEntityInvoiceType.UKRAINIAN_FOP.name) {
            "invoiceType is invalid"
        }

        val receivedDate = payload.requiredField("receivedDate").asLocalDate("receivedDate")
        val exchangeRateDate = payload.requiredField("exchangeRateDate").asLocalDate("exchangeRateDate")
        require(exchangeRateDate == receivedDate) {
            "exchangeRateDate must match receivedDate"
        }

        val exchangeRateSource = payload.requiredField("exchangeRateSource").asEnum<ExchangeRateSource>("exchangeRateSource")
        val fetchedAt = payload.get("exchangeRateFetchedAt")
            ?.takeUnless { it.isNull }
            ?.let { node ->
                runCatching { Instant.parse(node.asText()) }
                    .getOrElse { throw IllegalArgumentException("exchangeRateFetchedAt must be an ISO instant") }
            }
        if (exchangeRateSource == ExchangeRateSource.MANUAL) {
            require(fetchedAt == null) {
                "exchangeRateFetchedAt must be absent for manual exchange rates"
            }
        }

        val invoice = UkrainianFopInvoice(
            entityId = payload.requiredField("entityId").asText().trim(),
            invoiceDate = payload.requiredField("invoiceDate").asLocalDate("invoiceDate"),
            receivedDate = receivedDate,
            number = payload.requiredField("number").asText(),
            client = payload.requiredField("client").asText(),
            amount = payload.requiredField("amount").asMoney("amount"),
            currency = payload.requiredField("currency").asText().trim().uppercase(),
            taxCurrency = payload.requiredField("taxCurrency").asText().trim().uppercase(),
            exchangeRateToTaxCurrency = payload.requiredField("exchangeRateToTaxCurrency").asBigDecimal("exchangeRateToTaxCurrency"),
            exchangeRateSource = exchangeRateSource,
            amountTaxCurrencySnapshot = payload.requiredField("amountTaxCurrency").asMoney("amountTaxCurrency")
        )

        return ParsedBusinessEntityInvoice(
            invoice = invoice,
            exchangeRateDate = exchangeRateDate,
            exchangeRateFetchedAt = fetchedAt
        )
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

    private fun JsonNode.asBigDecimal(fieldName: String): BigDecimal {
        return when {
            isNumber -> decimalValue()
            isTextual -> runCatching { BigDecimal(asText()) }
                .getOrElse { throw IllegalArgumentException("$fieldName must be a number") }
            else -> throw IllegalArgumentException("$fieldName must be a number")
        }
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

    private fun BalanceMovement.toNormalizedJson(): JsonNode {
        val obj = JsonSupport.mapper.createObjectNode()
        obj.put("date", date.toString())
        when (this) {
            is BalanceMovement.External -> {
                obj.put("operation", operation.name)
                obj.put("amount", amount.amount)
                obj.put("accountId", accountId)
            }
            is BalanceMovement.InternalTransfer -> {
                obj.put("movementType", "InternalTransfer")
                obj.put("fromAccountId", fromAccountId)
                obj.put("toAccountId", toAccountId)
                obj.put("amount", amount.amount)
            }
        }
        note?.let { obj.put("note", it) }
        return obj
    }

    private fun ParsedBusinessEntityInvoice.toNormalizedJson(): JsonNode {
        val obj = JsonSupport.mapper.createObjectNode()
        obj.put("entityId", invoice.entityId)
        obj.put("invoiceType", invoice.invoiceType.name)
        obj.put("invoiceDate", invoice.invoiceDate.toString())
        obj.put("receivedDate", invoice.receivedDate.toString())
        obj.put("number", invoice.number)
        obj.put("client", invoice.client)
        obj.put("amount", invoice.amount.amount)
        obj.put("currency", invoice.currency)
        obj.put("taxCurrency", invoice.taxCurrency)
        obj.put("exchangeRateToTaxCurrency", invoice.exchangeRateToTaxCurrency)
        obj.put("exchangeRateSource", invoice.exchangeRateSource!!.name)
        obj.put("exchangeRateDate", exchangeRateDate.toString())
        exchangeRateFetchedAt?.let { obj.put("exchangeRateFetchedAt", it.toString()) }
        obj.put("amountTaxCurrency", invoice.amountTaxCurrency.amount)
        return obj
    }
}
