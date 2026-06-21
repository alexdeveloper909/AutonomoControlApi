package autonomo.util

import autonomo.config.JsonSupport
import autonomo.domain.BalanceAccount
import autonomo.domain.BalanceMovement
import autonomo.domain.BudgetEntry
import autonomo.domain.Expense
import autonomo.domain.Invoice
import autonomo.domain.IvaRate
import autonomo.domain.Money
import autonomo.domain.MonthKey
import autonomo.domain.Percentage
import autonomo.domain.Rate
import autonomo.domain.RegularSpending
import autonomo.domain.RegularSpendingCadence
import autonomo.domain.RegularSpendingScheduleType
import autonomo.domain.RetencionRate
import autonomo.domain.StatePayment
import autonomo.domain.StatePaymentType
import autonomo.domain.TransferOp
import autonomo.model.RecordType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
            spent = Money(BigDecimal("1000")),
            earned = Money(BigDecimal("1200")),
            targetSpend = Money(BigDecimal("900")),
            notes = "Summer",
            exceptionalSpend = Money(BigDecimal("100")),
            exceptionalNotes = "Flights"
        )

        val eventDate = RecordPayloadParser.eventDate(RecordType.BUDGET, budget)

        assertEquals(LocalDate.parse("2024-07-01"), eventDate)
    }

    @Test
    fun parsesBudgetPayloadWithNewFields() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "monthKey": "2026-04",
              "spent": 4111.00,
              "earned": 5717.00,
              "targetSpend": 3000.00,
              "notes": "Heavy spending month.",
              "exceptionalSpend": 809.00,
              "exceptionalNotes": "Lenses and insurance."
            }
            """.trimIndent()
        )

        val budget = RecordPayloadParser.parse(RecordType.BUDGET, payload) as BudgetEntry

        assertEquals(MonthKey.of(2026, 4), budget.monthKey)
        assertMoneyEquals("4111.00", budget.spent)
        assertMoneyEquals("5717.00", budget.earned)
        assertMoneyEquals("3000.00", budget.targetSpend!!)
        assertEquals("Heavy spending month.", budget.notes)
        assertMoneyEquals("809.00", budget.exceptionalSpend!!)
        assertEquals("Lenses and insurance.", budget.exceptionalNotes)
    }

    @Test
    fun parsesBudgetPayloadWithLegacyFields() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "monthKey": "2024-07",
              "plannedSpend": 2000.00,
              "earned": 2500.00,
              "description": "Summer budget",
              "budgetGoal": "Save for tax"
            }
            """.trimIndent()
        )

        val budget = RecordPayloadParser.parse(RecordType.BUDGET, payload) as BudgetEntry

        assertEquals(MonthKey.of(2024, 7), budget.monthKey)
        assertMoneyEquals("2000.00", budget.spent)
        assertMoneyEquals("2500.00", budget.earned)
        assertEquals("Summer budget", budget.notes)
    }

    @Test
    fun parsesBudgetPayloadPreferringSpentOverLegacyPlannedSpend() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "monthKey": "2024-07",
              "spent": 2100.00,
              "plannedSpend": 2000.00,
              "earned": 2500.00
            }
            """.trimIndent()
        )

        val budget = RecordPayloadParser.parse(RecordType.BUDGET, payload) as BudgetEntry

        assertMoneyEquals("2100.00", budget.spent)
    }

    @Test
    fun serializesBudgetPayloadWithNormalizedSpent() {
        val budget = BudgetEntry(
            monthKey = MonthKey.of(2026, 4),
            spent = Money(BigDecimal("4111.00")),
            earned = Money(BigDecimal("5717.00")),
            targetSpend = Money(BigDecimal("3000.00")),
            notes = "Heavy spending month.",
            exceptionalSpend = Money(BigDecimal("809.00")),
            exceptionalNotes = "Lenses and insurance."
        )

        val json = JsonSupport.mapper.readTree(RecordPayloadParser.toJson(budget))

        assertEquals(0, json.get("spent").decimalValue().compareTo(BigDecimal("4111.00")))
        assertFalse(json.has("plannedSpend"))
        assertEquals(0, json.get("targetSpend").decimalValue().compareTo(BigDecimal("3000.00")))
        assertEquals("Heavy spending month.", json.get("notes").asText())
        assertEquals(0, json.get("exceptionalSpend").decimalValue().compareTo(BigDecimal("809.00")))
        assertEquals("Lenses and insurance.", json.get("exceptionalNotes").asText())
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
        val transfer = BalanceMovement.External(
            date = LocalDate.parse("2024-09-01"),
            operation = TransferOp.Inflow,
            amount = Money(BigDecimal("150")),
            note = null
        )

        val eventDate = RecordPayloadParser.eventDate(RecordType.TRANSFER, transfer)

        assertEquals(LocalDate.parse("2024-09-01"), eventDate)
    }

    @Test
    fun parsesExternalTransferWithAccountId() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "operation": "Inflow",
              "amount": 1500.00,
              "accountId": "cash",
              "note": "Cash sale"
            }
            """.trimIndent()
        )

        val movement = RecordPayloadParser.parse(RecordType.TRANSFER, payload) as BalanceMovement.External

        assertEquals("cash", movement.accountId)
        assertEquals(TransferOp.Inflow, movement.operation)
        assertMoneyEquals("1500.00", movement.amount)
    }

    @Test
    fun parsesLegacyExternalTransferWithoutAccountIdAsMain() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "operation": "Outflow",
              "amount": 25.00
            }
            """.trimIndent()
        )

        val movement = RecordPayloadParser.parse(RecordType.TRANSFER, payload) as BalanceMovement.External

        assertEquals(BalanceAccount.MAIN_ACCOUNT_ID, movement.accountId)
        assertEquals(TransferOp.Outflow, movement.operation)
    }

    @Test
    fun parsesInternalTransferPayload() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "movementType": "InternalTransfer",
              "fromAccountId": "main",
              "toAccountId": "cash",
              "amount": 300.00,
              "note": "ATM"
            }
            """.trimIndent()
        )

        val movement = RecordPayloadParser.parse(RecordType.TRANSFER, payload) as BalanceMovement.InternalTransfer
        val json = JsonSupport.mapper.readTree(RecordPayloadParser.toJson(movement))

        assertEquals("main", movement.fromAccountId)
        assertEquals("cash", movement.toAccountId)
        assertEquals("InternalTransfer", json.get("movementType").asText())
        assertEquals(false, json.has("operation"))
    }

    @Test
    fun rejectsAmbiguousTransferPayload() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "operation": "Outflow",
              "fromAccountId": "main",
              "toAccountId": "cash",
              "amount": 300.00
            }
            """.trimIndent()
        )

        val error = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            RecordPayloadParser.parse(RecordType.TRANSFER, payload)
        }

        assertEquals("TRANSFER payload cannot mix operation with internal transfer fields", error.message)
    }

    @Test
    fun parsesLegacyExpensePayload() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "documentDate": "2024-06-05",
              "vendor": "Vendor",
              "category": "Software",
              "baseExclVat": 100.00,
              "ivaRate": "STANDARD",
              "vatRecoverableFlag": false,
              "deductibleShare": 0.50
            }
            """.trimIndent()
        )

        val expense = RecordPayloadParser.parse(RecordType.EXPENSE, payload) as Expense

        assertMoneyEquals("0.00", expense.vatRecoverable)
        assertMoneyEquals("71.00", expense.expenseForIrpf)
        assertPercentageEquals("0.50", expense.irpfDeductiblePercentage)
    }

    @Test
    fun parsesNewExpensePayload() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "documentDate": "2024-06-05",
              "vendor": "Vendor",
              "category": "Software",
              "baseExclVat": 100.00,
              "ivaRate": "STANDARD",
              "vatRecoverableFlag": true,
              "deductibleShare": 1.00,
              "ivaDeductiblePercentage": 0.50,
              "irpfDeductiblePercentage": 0.75
            }
            """.trimIndent()
        )

        val expense = RecordPayloadParser.parse(RecordType.EXPENSE, payload) as Expense

        assertPercentageEquals("0.50", expense.effectiveIvaDeductiblePercentage)
        assertPercentageEquals("0.75", expense.irpfDeductiblePercentage)
        assertMoneyEquals("10.5000", expense.vatRecoverable)
        assertMoneyEquals("85.5000", expense.expenseForIrpf)
    }

    @Test
    fun mixedExpensePayloadPrefersNewPercentages() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "documentDate": "2024-06-05",
              "vendor": "Vendor",
              "category": "Software",
              "baseExclVat": 100.00,
              "ivaRate": "STANDARD",
              "vatRecoverableFlag": false,
              "deductibleShare": 0.25,
              "ivaDeductiblePercentage": 1.00,
              "irpfDeductiblePercentage": 0.80
            }
            """.trimIndent()
        )

        val expense = RecordPayloadParser.parse(RecordType.EXPENSE, payload) as Expense

        assertPercentageEquals("1.00", expense.effectiveIvaDeductiblePercentage)
        assertPercentageEquals("0.80", expense.irpfDeductiblePercentage)
        assertMoneyEquals("21.0000", expense.vatRecoverable)
        assertMoneyEquals("80.0000", expense.expenseForIrpf)
    }

    @Test
    fun rejectsInvalidExpensePercentages() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "documentDate": "2024-06-05",
              "vendor": "Vendor",
              "category": "Software",
              "baseExclVat": 100.00,
              "ivaRate": "STANDARD",
              "vatRecoverableFlag": true,
              "deductibleShare": 1.00,
              "ivaDeductiblePercentage": 1.20
            }
            """.trimIndent()
        )

        assertThrows(Exception::class.java) {
            RecordPayloadParser.parse(RecordType.EXPENSE, payload)
        }
    }

    @Test
    fun parsesLegacyRegularSpendingAsOngoing() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "name": "Gym",
              "startDate": "2026-06-01",
              "cadence": "MONTHLY",
              "amount": 29.99
            }
            """.trimIndent()
        )

        val spending = RecordPayloadParser.parse(RecordType.REGULAR_SPENDING, payload) as RegularSpending

        assertEquals(RegularSpendingScheduleType.ONGOING, spending.scheduleType)
        assertEquals(RegularSpendingCadence.MONTHLY, spending.cadence)
        assertEquals(null, spending.paymentCount)
        assertEquals(LocalDate.parse("2026-06-01"), RecordPayloadParser.eventDate(RecordType.REGULAR_SPENDING, spending))
    }

    @Test
    fun parsesFixedTermRegularSpending() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "name": "Installment",
              "startDate": "2026-06-15",
              "scheduleType": "FIXED_TERM",
              "paymentCount": 6,
              "amount": 120.00
            }
            """.trimIndent()
        )

        val spending = RecordPayloadParser.parse(RecordType.REGULAR_SPENDING, payload) as RegularSpending

        assertEquals(RegularSpendingScheduleType.FIXED_TERM, spending.scheduleType)
        assertEquals(null, spending.cadence)
        assertEquals(6, spending.paymentCount)
        assertMoneyEquals("120.00", spending.amount)
    }

    @Test
    fun rejectsInvalidFixedTermRegularSpending() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "name": "Installment",
              "startDate": "2026-06-15",
              "scheduleType": "FIXED_TERM",
              "cadence": "YEARLY",
              "paymentCount": 6,
              "amount": 120.00
            }
            """.trimIndent()
        )

        assertThrows(IllegalArgumentException::class.java) {
            RecordPayloadParser.parse(RecordType.REGULAR_SPENDING, payload)
        }
    }

    @Test
    fun rejectsPaymentCountForOngoingRegularSpending() {
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "name": "Gym",
              "startDate": "2026-06-01",
              "scheduleType": "ONGOING",
              "cadence": "MONTHLY",
              "paymentCount": 6,
              "amount": 29.99
            }
            """.trimIndent()
        )

        assertThrows(IllegalArgumentException::class.java) {
            RecordPayloadParser.parse(RecordType.REGULAR_SPENDING, payload)
        }
    }

    @Test
    fun serializesFixedTermRegularSpendingWithoutCadence() {
        val spending = RegularSpending(
            name = "Installment",
            startDate = LocalDate.parse("2026-06-15"),
            cadence = null,
            amount = Money(BigDecimal("120.00")),
            scheduleType = RegularSpendingScheduleType.FIXED_TERM,
            paymentCount = 6
        )

        val json = JsonSupport.mapper.readTree(RecordPayloadParser.toJson(spending))

        assertEquals("FIXED_TERM", json.get("scheduleType").asText())
        assertEquals(6, json.get("paymentCount").asInt())
        assertFalse(json.has("cadence"))
    }

    @Test
    fun parsesUkrainianFopInvoiceAndUsesReceivedDateAsEventDate() {
        val parsed = RecordPayloadParser.parse(
            RecordType.BUSINESS_ENTITY_INVOICE,
            ukrainianFopPayload()
        ) as RecordPayloadParser.ParsedBusinessEntityInvoice

        assertEquals("ent_fop", parsed.invoice.entityId)
        assertEquals(LocalDate.parse("2026-06-15"), parsed.invoice.receivedDate)
        assertMoneyEquals("40500.00", parsed.invoice.amountTaxCurrency)
        assertEquals(LocalDate.parse("2026-06-15"), RecordPayloadParser.eventDate(RecordType.BUSINESS_ENTITY_INVOICE, parsed))
    }

    @Test
    fun serializesUkrainianFopInvoiceWithTransportSnapshotFields() {
        val parsed = RecordPayloadParser.parse(
            RecordType.BUSINESS_ENTITY_INVOICE,
            ukrainianFopPayload()
        )

        val json = JsonSupport.mapper.readTree(RecordPayloadParser.toJson(parsed))

        assertEquals("UKRAINIAN_FOP", json.get("invoiceType").asText())
        assertEquals("2026-06-15", json.get("exchangeRateDate").asText())
        assertEquals("2026-06-15T10:00:00Z", json.get("exchangeRateFetchedAt").asText())
        assertEquals(0, json.get("amountTaxCurrency").decimalValue().compareTo(BigDecimal("40500.00")))
        assertFalse(json.has("amountTaxCurrencySnapshot"))
    }

    @Test
    fun rejectsManualUkrainianFopExchangeRateWithFetchedAt() {
        val payload = ukrainianFopPayload(
            overrides = """
              "exchangeRateSource": "MANUAL",
              "exchangeRateFetchedAt": "2026-06-15T10:00:00Z"
            """.trimIndent()
        )

        val error = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            RecordPayloadParser.parse(RecordType.BUSINESS_ENTITY_INVOICE, payload)
        }

        assertEquals("exchangeRateFetchedAt must be absent for manual exchange rates", error.message)
    }

    @Test
    fun rejectsUkrainianFopExchangeRateDateDifferentFromReceivedDate() {
        val payload = ukrainianFopPayload(
            overrides = """
              "exchangeRateDate": "2026-06-14"
            """.trimIndent()
        )

        val error = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            RecordPayloadParser.parse(RecordType.BUSINESS_ENTITY_INVOICE, payload)
        }

        assertEquals("exchangeRateDate must match receivedDate", error.message)
    }

    private fun assertMoneyEquals(expected: String, actual: Money) {
        assertEquals(0, actual.amount.compareTo(BigDecimal(expected)))
    }

    private fun assertPercentageEquals(expected: String, actual: Percentage) {
        assertEquals(0, actual.value.compareTo(BigDecimal(expected)))
    }

    private fun ukrainianFopPayload(overrides: String? = null) = JsonSupport.mapper.readTree(
        """
        {
          "entityId": "ent_fop",
          "invoiceType": "UKRAINIAN_FOP",
          "invoiceDate": "2026-06-01",
          "receivedDate": "2026-06-15",
          "number": "FOP-1",
          "client": "Acme",
          "amount": 1000.00,
          "currency": "USD",
          "taxCurrency": "UAH",
          "exchangeRateToTaxCurrency": 40.50,
          "exchangeRateSource": "NBU",
          "exchangeRateDate": "2026-06-15",
          "exchangeRateFetchedAt": "2026-06-15T10:00:00Z",
          "amountTaxCurrency": 40500.00
          ${overrides?.let { ",$it" }.orEmpty()}
        }
        """.trimIndent()
    )
}
