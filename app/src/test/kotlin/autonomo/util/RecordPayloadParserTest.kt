package autonomo.util

import autonomo.domain.BudgetEntry
import autonomo.domain.Expense
import autonomo.domain.Invoice
import autonomo.domain.IvaRate
import autonomo.domain.Money
import autonomo.domain.MonthKey
import autonomo.domain.Rate
import autonomo.domain.RetencionRate
import autonomo.domain.StatePayment
import autonomo.domain.StatePaymentType
import autonomo.domain.Transfer
import autonomo.domain.TransferOp
import autonomo.model.RecordType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecordPayloadParserTest {
    @Test
    fun invoiceEventDateUsesPaymentDateWhenPresent() {
        val invoice = Invoice(
            invoiceDate = LocalDate.parse("2024-06-10"),
            number = "INV-1",
            client = "Acme",
            baseExclVat = Money(BigDecimal("100")),
            ivaRate = IvaRate.STANDARD,
            retencion = RetencionRate.ZERO,
            paymentDate = LocalDate.parse("2024-06-20")
        )

        val eventDate = RecordPayloadParser.eventDate(RecordType.INVOICE, invoice)

        assertEquals(LocalDate.parse("2024-06-20"), eventDate)
    }

    @Test
    fun expenseEventDateFallsBackToDocumentDate() {
        val expense = Expense(
            documentDate = LocalDate.parse("2024-06-05"),
            vendor = "Vendor",
            category = "Software",
            baseExclVat = Money(BigDecimal("50")),
            ivaRate = IvaRate.REDUCED,
            vatRecoverableFlag = true,
            deductibleShare = Rate.fromDecimal("0.5")
        )

        val eventDate = RecordPayloadParser.eventDate(RecordType.EXPENSE, expense)

        assertEquals(LocalDate.parse("2024-06-05"), eventDate)
    }

    @Test
    fun budgetEventDateUsesMonthKeyFirstDay() {
        val budget = BudgetEntry(
            monthKey = MonthKey.of(2024, 7),
            plannedSpend = Money(BigDecimal("1000")),
            earned = Money(BigDecimal("1200")),
            description = "Summer",
            budgetGoal = "Save"
        )

        val eventDate = RecordPayloadParser.eventDate(RecordType.BUDGET, budget)

        assertEquals(LocalDate.parse("2024-07-01"), eventDate)
    }

    @Test
    fun statePaymentEventDateUsesPaymentDate() {
        val statePayment = StatePayment(
            paymentDate = LocalDate.parse("2024-08-01"),
            type = StatePaymentType.Modelo303,
            amount = Money(BigDecimal("300"))
        )

        val eventDate = RecordPayloadParser.eventDate(RecordType.STATE_PAYMENT, statePayment)

        assertEquals(LocalDate.parse("2024-08-01"), eventDate)
    }

    @Test
    fun transferEventDateUsesDate() {
        val transfer = Transfer(
            date = LocalDate.parse("2024-09-01"),
            operation = TransferOp.Inflow,
            amount = Money(BigDecimal("150")),
            note = null
        )

        val eventDate = RecordPayloadParser.eventDate(RecordType.TRANSFER, transfer)

        assertEquals(LocalDate.parse("2024-09-01"), eventDate)
    }
}
