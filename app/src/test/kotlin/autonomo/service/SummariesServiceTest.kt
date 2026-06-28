package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.IvaRate
import autonomo.domain.MonthKey
import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.RetencionRate
import autonomo.domain.RentaPlanningSettings
import autonomo.domain.IrpfTerritory
import autonomo.domain.RetaBaseSelectionPolicy
import autonomo.domain.RetaGenericDeductionMode
import autonomo.domain.RetaPlanningSettings
import autonomo.domain.RetaProjectionMode
import autonomo.domain.RetaScenarioSettings
import autonomo.domain.RetaWarningCode
import autonomo.domain.Settings
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class SummariesServiceTest {
    @Test
    fun monthSummariesRegistersInvoiceIntoCorrectMonth() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO
        )

        val invoicePayload = mapOf(
            "invoiceDate" to "2024-06-10",
            "number" to "INV-1",
            "client" to "Acme",
            "baseExclVat" to 100.00,
            "ivaRate" to "STANDARD",
            "retencion" to "ZERO",
            "paymentDate" to "2024-06-20"
        )
        val payloadJson = JsonSupport.mapper.writeValueAsString(invoicePayload)
        val recordItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.INVOICE,
            eventDate = LocalDate.parse("2024-06-20"),
            payloadJson = payloadJson,
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )

        val repo = FakeRecordsRepository(
            itemsByMonth = mapOf("WS#ws-1#M#2024-06" to listOf(recordItem)),
            itemsByQuarter = mapOf("WS#ws-1#Q#2024-Q2" to listOf(recordItem))
        )
        val service = SummariesService(repo)

        val response = service.monthSummaries("ws-1", settings)
        val june = response.items.firstOrNull { it.monthKey == MonthKey.of(2024, 6) }
        assertNotNull(june)
        assertEquals(0, june!!.incomeBase.amount.compareTo(BigDecimal("100")))
        assertEquals(0, june.vatOutput.amount.compareTo(BigDecimal("21")))
    }

    @Test
    fun quarterSummariesRegistersInvoiceIntoCorrectQuarter() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO
        )

        val invoicePayload = mapOf(
            "invoiceDate" to "2024-06-10",
            "number" to "INV-1",
            "client" to "Acme",
            "baseExclVat" to 100.00,
            "ivaRate" to "STANDARD",
            "retencion" to "ZERO",
            "paymentDate" to "2024-06-20"
        )
        val payloadJson = JsonSupport.mapper.writeValueAsString(invoicePayload)
        val recordItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.INVOICE,
            eventDate = LocalDate.parse("2024-06-20"),
            payloadJson = payloadJson,
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )

        val repo = FakeRecordsRepository(
            itemsByMonth = mapOf("WS#ws-1#M#2024-06" to listOf(recordItem)),
            itemsByQuarter = mapOf("WS#ws-1#Q#2024-Q2" to listOf(recordItem))
        )
        val service = SummariesService(repo)

        val response = service.quarterSummaries("ws-1", settings)
        val q2 = response.items.firstOrNull { it.quarterKey.year == 2024 && it.quarterKey.quarter == 2 }
        assertNotNull(q2)
        assertEquals(0, q2!!.incomeBase.amount.compareTo(BigDecimal("100")))
        assertEquals(0, q2.vatOutput.amount.compareTo(BigDecimal("21")))
    }

    @Test
    fun quarterSummariesExposeCumulativeModelo130Due() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO
        )

        val invoicePayload = mapOf(
            "invoiceDate" to "2024-04-10",
            "number" to "INV-1",
            "client" to "Acme",
            "baseExclVat" to 1000.00,
            "ivaRate" to "STANDARD",
            "retencion" to "ZERO",
            "paymentDate" to "2024-04-20"
        )
        val modelo130PaymentPayload = mapOf(
            "paymentDate" to "2024-04-20",
            "type" to "Modelo130",
            "amount" to 50.00
        )
        val invoiceItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.INVOICE,
            eventDate = LocalDate.parse("2024-04-20"),
            payloadJson = JsonSupport.mapper.writeValueAsString(invoicePayload),
            workspaceMonth = "WS#ws-1#M#2024-04",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )
        val paymentItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.STATE_PAYMENT,
            eventDate = LocalDate.parse("2024-04-20"),
            payloadJson = JsonSupport.mapper.writeValueAsString(modelo130PaymentPayload),
            workspaceMonth = "WS#ws-1#M#2024-04",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )

        val repo = FakeRecordsRepository(
            itemsByQuarter = mapOf(
                "WS#ws-1#Q#2024-Q2" to listOf(invoiceItem, paymentItem)
            )
        )
        val service = SummariesService(repo)

        val response = service.quarterSummaries("ws-1", settings)

        val q2 = response.items.firstOrNull { it.quarterKey.year == 2024 && it.quarterKey.quarter == 2 }
        assertNotNull(q2)
        assertEquals(0, q2!!.modelo130DueThisQuarter.amount.compareTo(BigDecimal("150.0000")))
    }

    @Test
    fun ivaSummaryComputesYearEstimateFromQuarterRecords() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO
        )

        val invoicePayload = mapOf(
            "invoiceDate" to "2024-06-10",
            "number" to "INV-1",
            "client" to "Acme",
            "baseExclVat" to 100.00,
            "ivaRate" to "STANDARD",
            "retencion" to "ZERO",
            "paymentDate" to "2024-06-20"
        )
        val expensePayload = mapOf(
            "documentDate" to "2024-06-05",
            "vendor" to "Vendor",
            "category" to "Software",
            "baseExclVat" to 50.00,
            "ivaRate" to "STANDARD",
            "vatRecoverableFlag" to true,
            "deductibleShare" to 1.00,
            "paymentDate" to "2024-06-06"
        )
        val invoiceItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.INVOICE,
            eventDate = LocalDate.parse("2024-06-20"),
            payloadJson = JsonSupport.mapper.writeValueAsString(invoicePayload),
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )
        val expenseItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.EXPENSE,
            eventDate = LocalDate.parse("2024-06-06"),
            payloadJson = JsonSupport.mapper.writeValueAsString(expensePayload),
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )

        val repo = FakeRecordsRepository(
            itemsByQuarter = mapOf("WS#ws-1#Q#2024-Q2" to listOf(invoiceItem, expenseItem))
        )
        val service = SummariesService(repo)

        val response = service.ivaSummary("ws-1", settings)

        val q2 = response.iva.quarters.firstOrNull { it.quarterKey.year == 2024 && it.quarterKey.quarter == 2 }
        assertNotNull(q2)
        assertEquals(0, q2!!.outputVat.amount.compareTo(BigDecimal("21")))
        assertEquals(0, q2.inputVatDeductible.amount.compareTo(BigDecimal("10.5")))
        assertEquals(0, q2.vatPayable.amount.compareTo(BigDecimal("10.5000")))
    }

    @Test
    fun monthSummariesIgnoreBudgetRecords() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO
        )

        val budgetPayload = mapOf(
            "monthKey" to "2024-06",
            "spent" to 2000.00,
            "earned" to 2500.00,
            "targetSpend" to 1800.00,
            "exceptionalSpend" to 100.00
        )
        val budgetItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.BUDGET,
            eventDate = LocalDate.parse("2024-06-01"),
            payloadJson = JsonSupport.mapper.writeValueAsString(budgetPayload),
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )

        val repo = FakeRecordsRepository(
            itemsByMonth = mapOf("WS#ws-1#M#2024-06" to listOf(budgetItem))
        )
        val service = SummariesService(repo)

        val response = service.monthSummaries("ws-1", settings)
        val june = response.items.firstOrNull { it.monthKey == MonthKey.of(2024, 6) }
        assertNotNull(june)
        assertEquals(0, june!!.incomeBase.amount.compareTo(BigDecimal.ZERO))
        assertEquals(0, june.expenseDeductibleBase.amount.compareTo(BigDecimal.ZERO))
        assertEquals(0, june.vatOutput.amount.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun quarterSummariesIgnoreBudgetRecords() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO
        )

        val budgetPayload = mapOf(
            "monthKey" to "2024-06",
            "spent" to 2000.00,
            "earned" to 2500.00
        )
        val budgetItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.BUDGET,
            eventDate = LocalDate.parse("2024-06-01"),
            payloadJson = JsonSupport.mapper.writeValueAsString(budgetPayload),
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )

        val repo = FakeRecordsRepository(
            itemsByQuarter = mapOf("WS#ws-1#Q#2024-Q2" to listOf(budgetItem))
        )
        val service = SummariesService(repo)

        val response = service.quarterSummaries("ws-1", settings)
        val q2 = response.items.firstOrNull { it.quarterKey.year == 2024 && it.quarterKey.quarter == 2 }
        assertNotNull(q2)
        assertEquals(0, q2!!.incomeBase.amount.compareTo(BigDecimal.ZERO))
        assertEquals(0, q2.expenseDeductibleBase.amount.compareTo(BigDecimal.ZERO))
        assertEquals(0, q2.vatOutput.amount.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun rentaSummaryReturnsNullWhenDisabled() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO,
            rentaPlanning = RentaPlanningSettings(enabled = false)
        )

        val repo = FakeRecordsRepository()
        val service = SummariesService(repo)

        val response = service.rentaSummary("ws-1", settings)
        assertNull(response.renta)
        assertNull(response.rentaProjected)
    }

    @Test
    fun rentaSummaryComputesEstimateWhenEnabled() {
        val settings = Settings(
            year = 2024,
            startDate = LocalDate.parse("2024-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO,
            rentaPlanning = RentaPlanningSettings(enabled = true, taxYear = 2024, residence = IrpfTerritory.MADRID)
        )

        val invoicePayload = mapOf(
            "invoiceDate" to "2024-06-10",
            "number" to "INV-1",
            "client" to "Acme",
            "baseExclVat" to 1000.00,
            "ivaRate" to "STANDARD",
            "retencion" to "STANDARD",
            "paymentDate" to "2024-06-20"
        )
        val payloadJson = JsonSupport.mapper.writeValueAsString(invoicePayload)
        val recordItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.INVOICE,
            eventDate = LocalDate.parse("2024-06-20"),
            payloadJson = payloadJson,
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2"
        )

        val repo = FakeRecordsRepository(
            itemsByMonth = mapOf("WS#ws-1#M#2024-06" to listOf(recordItem))
        )
        val service = SummariesService(repo)

        val response = service.rentaSummary("ws-1", settings)
        assertNotNull(response.renta)
        assertEquals(2024, response.renta!!.taxYear)
    }

    @Test
    fun retaSummaryComputesEstimateFromFixtureRecords() {
        val settings = retaSettings()
        val invoiceItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.INVOICE,
            eventDate = LocalDate.parse("2026-03-20"),
            payloadJson = JsonSupport.mapper.writeValueAsString(invoicePayload("2026-03-10", "INV-RETA", "1000.00")),
            workspaceMonth = "WS#ws-1#M#2026-03",
            workspaceQuarter = "WS#ws-1#Q#2026-Q1"
        )
        val expenseItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.EXPENSE,
            eventDate = LocalDate.parse("2026-03-21"),
            payloadJson = JsonSupport.mapper.writeValueAsString(expensePayload("2026-03-20", "100.00")),
            workspaceMonth = "WS#ws-1#M#2026-03",
            workspaceQuarter = "WS#ws-1#Q#2026-Q1"
        )
        val seguridadSocialItem = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.STATE_PAYMENT,
            eventDate = LocalDate.parse("2026-03-31"),
            payloadJson = JsonSupport.mapper.writeValueAsString(statePaymentPayload("2026-03-31", "SeguridadSocial", "300.00")),
            workspaceMonth = "WS#ws-1#M#2026-03",
            workspaceQuarter = "WS#ws-1#Q#2026-Q1"
        )
        val ignoredBusinessInvoice = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.BUSINESS_ENTITY_INVOICE,
            eventDate = LocalDate.parse("2026-03-20"),
            payloadJson = "{}",
            workspaceMonth = "WS#ws-1#M#2026-03",
            workspaceQuarter = "WS#ws-1#Q#2026-Q1"
        )

        val repo = FakeRecordsRepository(
            itemsByMonth = mapOf(
                monthTypeKey("WS#ws-1#M#2026-03", RecordType.INVOICE) to listOf(invoiceItem),
                monthTypeKey("WS#ws-1#M#2026-03", RecordType.EXPENSE) to listOf(expenseItem),
                monthTypeKey("WS#ws-1#M#2026-03", RecordType.STATE_PAYMENT) to listOf(seguridadSocialItem),
                monthTypeKey("WS#ws-1#M#2026-03", RecordType.BUSINESS_ENTITY_INVOICE) to listOf(ignoredBusinessInvoice)
            )
        )
        val service = SummariesService(repo) { LocalDate.parse("2026-06-28") }

        val response = service.retaSummary(
            "ws-1",
            settings,
            RetaScenarioSettings(
                projectionMode = RetaProjectionMode.MANUAL_FUTURE_MONTHLY_INCOME,
                manualFutureMonthlyActivityNet = Money.eur("3500"),
                baseSelectionPolicy = RetaBaseSelectionPolicy.CUSTOM,
                customContributionBase = Money.eur("1437.91")
            )
        )

        assertEquals(settings, response.settings)
        assertEquals(2026, response.reta.year)
        assertEquals(RetaProjectionMode.MANUAL_FUTURE_MONTHLY_INCOME, response.reta.projectionMode)
        assertEquals(RetaGenericDeductionMode.GENERAL_7_PERCENT, response.reta.genericDeductionMode)
        assertEquals(0, response.reta.irpfActivityNetAnnual.amount.compareTo(BigDecimal("24130.0000")))
        assertEquals(0, response.reta.seguridadSocialPaidAnnual.amount.compareTo(BigDecimal("300.0000")))
        assertEquals(0, response.reta.retaBeforeGenericDeduction.amount.compareTo(BigDecimal("24430.0000")))
        assertEquals(0, response.reta.retaGenericDeductionAmount.amount.compareTo(BigDecimal("1710.1000")))
        assertEquals(0, response.reta.retaComputableAnnualEarnings.amount.compareTo(BigDecimal("22719.9000")))
        assertEquals(0, response.reta.retaAverageMonthlyEarnings.amount.compareTo(BigDecimal("1893.3200")))
        assertEquals(5, response.reta.tramo?.tramo)
        assertEquals(0, response.reta.selectedContributionBase?.amount?.compareTo(BigDecimal("1437.9100")))
        assertTrue(repo.monthQueries.none { it.recordType == RecordType.BUSINESS_ENTITY_INVOICE })
        assertTrue(repo.monthQueries.none { it.recordType == RecordType.TRANSFER })
        assertTrue(repo.monthQueries.none { it.recordType == RecordType.BUDGET })
        assertTrue(repo.monthQueries.none { it.recordType == RecordType.REGULAR_SPENDING })
    }

    @Test
    fun retaSummaryIgnoresNonAutonomoInvoices() {
        val settings = retaSettings()
        val fopInvoice = sampleItem(
            workspaceId = "ws-1",
            recordType = RecordType.INVOICE,
            eventDate = LocalDate.parse("2026-03-20"),
            payloadJson = JsonSupport.mapper.writeValueAsString(
                invoicePayload("2026-03-10", "INV-FOP", "9999.00", entityId = "ent_fop")
            ),
            workspaceMonth = "WS#ws-1#M#2026-03",
            workspaceQuarter = "WS#ws-1#Q#2026-Q1"
        )
        val repo = FakeRecordsRepository(
            itemsByMonth = mapOf(monthTypeKey("WS#ws-1#M#2026-03", RecordType.INVOICE) to listOf(fopInvoice))
        )
        val service = SummariesService(repo) { LocalDate.parse("2026-06-28") }

        val response = service.retaSummary("ws-1", settings, RetaScenarioSettings())

        assertEquals(0, response.reta.irpfActivityNetAnnual.amount.compareTo(BigDecimal.ZERO.setScale(4)))
        assertTrue(response.reta.warningCodes.contains(RetaWarningCode.MANUAL_FUTURE_MONTHLY_ACTIVITY_NET_REQUIRED))
        assertTrue(response.reta.warningCodes.contains(RetaWarningCode.VERY_LOW_OR_NEGATIVE_EARNINGS_VERIFY_MINIMUM_CONTRIBUTION))
    }

    @Test
    fun retaSummaryWithNoDataReturnsActionableWarning() {
        val settings = retaSettings()
        val repo = FakeRecordsRepository()
        val service = SummariesService(repo) { LocalDate.parse("2026-06-28") }

        val response = service.retaSummary("ws-1", settings, RetaScenarioSettings())

        assertEquals(0, response.reta.irpfActivityNetAnnual.amount.compareTo(BigDecimal.ZERO.setScale(4)))
        assertTrue(response.reta.warningCodes.contains(RetaWarningCode.MANUAL_FUTURE_MONTHLY_ACTIVITY_NET_REQUIRED))
        assertTrue(response.reta.warningCodes.contains(RetaWarningCode.VERY_LOW_OR_NEGATIVE_EARNINGS_VERIFY_MINIMUM_CONTRIBUTION))
        assertEquals(36, repo.monthQueries.size)
    }

    private class FakeRecordsRepository(
        private val itemsByMonth: Map<String, List<RecordItem>> = emptyMap(),
        private val itemsByQuarter: Map<String, List<RecordItem>> = emptyMap()
    ) : WorkspaceRecordsRepositoryPort {
        data class MonthQuery(val workspaceMonth: String, val recordType: RecordType?)

        val monthQueries = mutableListOf<MonthQuery>()

        override fun create(record: RecordItem) = throw UnsupportedOperationException()
        override fun update(record: RecordItem) = throw UnsupportedOperationException()
        override fun get(workspaceId: String, recordKey: String): RecordItem? = throw UnsupportedOperationException()
        override fun delete(workspaceId: String, recordKey: String) = throw UnsupportedOperationException()
        override fun deleteByWorkspaceId(workspaceId: String) = throw UnsupportedOperationException()
        override fun setTtlByWorkspaceId(workspaceId: String, ttlEpoch: Long) = throw UnsupportedOperationException()
        override fun clearTtlByWorkspaceId(workspaceId: String) = throw UnsupportedOperationException()

        override fun queryByWorkspaceRecordKeyPrefix(workspaceId: String, recordKeyPrefix: String): List<RecordItem> =
            emptyList()

        override fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem> {
            monthQueries += MonthQuery(workspaceMonth, recordType)
            return itemsByMonth["$workspaceMonth#${recordType?.name ?: "ALL"}"].orEmpty() +
                itemsByMonth[workspaceMonth].orEmpty()
        }

        override fun queryByQuarter(workspaceQuarter: String, recordType: RecordType?): List<RecordItem> =
            itemsByQuarter[workspaceQuarter].orEmpty()

        override fun isConditionalFailure(ex: Exception): Boolean = false
    }

    private fun sampleItem(
        workspaceId: String,
        recordType: RecordType,
        eventDate: LocalDate,
        payloadJson: String,
        workspaceMonth: String,
        workspaceQuarter: String
    ): RecordItem {
        val now = Instant.parse("2024-06-21T09:30:00Z")
        return RecordItem(
            workspaceId = workspaceId,
            recordKey = "${recordType.name}#$eventDate#rec-1",
            recordId = "rec-1",
            recordType = recordType,
            eventDate = eventDate,
            payloadJson = payloadJson,
            workspaceMonth = workspaceMonth,
            workspaceQuarter = workspaceQuarter,
            createdAt = now,
            updatedAt = now,
            createdBy = "user-1",
            updatedBy = "user-1"
        )
    }

    private fun retaSettings(): Settings =
        Settings(
            year = 2026,
            startDate = LocalDate.parse("2026-01-01"),
            ivaStd = IvaRate.STANDARD.toRate(),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = false,
            openingBalance = Money.ZERO,
            retaPlanning = RetaPlanningSettings(enabled = true)
        )

    private fun invoicePayload(
        invoiceDate: String,
        number: String,
        baseExclVat: String,
        entityId: String = "autonomo"
    ) = mapOf(
        "invoiceDate" to invoiceDate,
        "number" to number,
        "client" to "Acme",
        "baseExclVat" to BigDecimal(baseExclVat),
        "ivaRate" to "STANDARD",
        "retencion" to "ZERO",
        "paymentDate" to invoiceDate,
        "entityId" to entityId
    )

    private fun expensePayload(documentDate: String, baseExclVat: String) = mapOf(
        "documentDate" to documentDate,
        "vendor" to "Vendor",
        "category" to "Software",
        "baseExclVat" to BigDecimal(baseExclVat),
        "ivaRate" to "ZERO",
        "vatRecoverableFlag" to false,
        "deductibleShare" to BigDecimal("1.00"),
        "paymentDate" to documentDate
    )

    private fun statePaymentPayload(paymentDate: String, type: String, amount: String) = mapOf(
        "paymentDate" to paymentDate,
        "type" to type,
        "amount" to BigDecimal(amount)
    )

    private fun monthTypeKey(workspaceMonth: String, recordType: RecordType?): String =
        "$workspaceMonth#${recordType?.name ?: "ALL"}"
}
