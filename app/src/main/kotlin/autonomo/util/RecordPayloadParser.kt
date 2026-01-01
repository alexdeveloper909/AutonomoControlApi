package autonomo.util

import autonomo.config.JsonSupport
import autonomo.domain.BudgetEntry
import autonomo.domain.Expense
import autonomo.domain.Invoice
import autonomo.domain.StatePayment
import autonomo.domain.Transfer
import autonomo.model.RecordType
import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

object RecordPayloadParser {
    fun parse(recordType: RecordType, payload: JsonNode): Any {
        return when (recordType) {
            RecordType.INVOICE -> JsonSupport.mapper.treeToValue(payload, Invoice::class.java)
            RecordType.EXPENSE -> JsonSupport.mapper.treeToValue(payload, Expense::class.java)
            RecordType.STATE_PAYMENT -> JsonSupport.mapper.treeToValue(payload, StatePayment::class.java)
            RecordType.TRANSFER -> JsonSupport.mapper.treeToValue(payload, Transfer::class.java)
            RecordType.BUDGET -> JsonSupport.mapper.treeToValue(payload, BudgetEntry::class.java)
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
        }
    }

    fun toJson(payload: Any): String = JsonSupport.mapper.writeValueAsString(payload)
}
