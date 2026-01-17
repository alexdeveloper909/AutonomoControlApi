package autonomo.service

import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class RecordsServiceTest {
    @Test
    fun listByMonthCanSortByEventDateDescending() {
        val items = listOf(
            sampleItem(RecordType.EXPENSE, LocalDate.parse("2024-06-05"), "rec-1"),
            sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-2"),
            sampleItem(RecordType.STATE_PAYMENT, LocalDate.parse("2024-06-10"), "rec-3")
        )

        val repo = FakeRecordsRepository(itemsByMonth = mapOf("WS#ws-1#M#2024-06" to items))
        val service = RecordsService(repo)

        val response = service.listByMonth(
            workspaceId = "ws-1",
            month = YearMonth.parse("2024-06"),
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC)
        )

        assertEquals(listOf("rec-2", "rec-3", "rec-1"), response.items.map { it.recordId })
    }

    @Test
    fun listByMonthSupportsLimitAndNextToken() {
        val items = listOf(
            sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-1"),
            sampleItem(RecordType.EXPENSE, LocalDate.parse("2024-06-10"), "rec-2"),
            sampleItem(RecordType.TRANSFER, LocalDate.parse("2024-06-05"), "rec-3")
        )

        val repo = FakeRecordsRepository(itemsByMonth = mapOf("WS#ws-1#M#2024-06" to items))
        val service = RecordsService(repo)

        val firstPage = service.listByMonth(
            workspaceId = "ws-1",
            month = YearMonth.parse("2024-06"),
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 2)
        )

        assertEquals(listOf("rec-1", "rec-2"), firstPage.items.map { it.recordId })
        assertNotNull(firstPage.nextToken)

        val secondPage = service.listByMonth(
            workspaceId = "ws-1",
            month = YearMonth.parse("2024-06"),
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 2, nextToken = firstPage.nextToken)
        )

        assertEquals(listOf("rec-3"), secondPage.items.map { it.recordId })
        assertNull(secondPage.nextToken)
    }

    @Test
    fun listByMonthRejectsInvalidNextToken() {
        val items = listOf(
            sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-1")
        )

        val repo = FakeRecordsRepository(itemsByMonth = mapOf("WS#ws-1#M#2024-06" to items))
        val service = RecordsService(repo)

        assertThrows<InvalidNextTokenException> {
            service.listByMonth(
                workspaceId = "ws-1",
                month = YearMonth.parse("2024-06"),
                recordType = null,
                options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 1, nextToken = "nope")
            )
        }
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

    private fun sampleItem(recordType: RecordType, eventDate: LocalDate, recordId: String): RecordItem {
        val now = Instant.parse("2024-06-21T09:30:00Z")
        return RecordItem(
            workspaceId = "ws-1",
            recordKey = "${recordType.name}#$eventDate#$recordId",
            recordId = recordId,
            recordType = recordType,
            eventDate = eventDate,
            payloadJson = "{}",
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2",
            createdAt = now,
            updatedAt = now,
            createdBy = "user-1",
            updatedBy = "user-1"
        )
    }
}

