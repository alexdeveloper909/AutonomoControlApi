package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.IvaRate
import autonomo.domain.MonthKey
import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.RetencionRate
import autonomo.domain.Settings
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
            openingBalance = Money.ZERO,
            expenseCategories = emptySet()
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
            openingBalance = Money.ZERO,
            expenseCategories = emptySet()
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

    private class FakeRecordsRepository(
        private val itemsByMonth: Map<String, List<RecordItem>> = emptyMap(),
        private val itemsByQuarter: Map<String, List<RecordItem>> = emptyMap()
    ) : WorkspaceRecordsRepositoryPort {
        override fun create(record: RecordItem) = throw UnsupportedOperationException()
        override fun update(record: RecordItem) = throw UnsupportedOperationException()
        override fun get(workspaceId: String, recordKey: String): RecordItem? = throw UnsupportedOperationException()
        override fun delete(workspaceId: String, recordKey: String) = throw UnsupportedOperationException()

        override fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem> =
            itemsByMonth[workspaceMonth].orEmpty()

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
}

