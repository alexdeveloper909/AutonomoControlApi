package autonomo.controller

import autonomo.config.JsonSupport
import autonomo.model.RecordResponse
import autonomo.model.RecordType
import autonomo.model.RecordsResponse
import autonomo.service.RecordsServicePort
import autonomo.util.ApiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class RecordsControllerTest {
    @Test
    fun createRecordReturns201() {
        val service = FakeRecordsService()
        val controller = RecordsController(service)

        val body = """
            {
              "recordType": "INVOICE",
              "payload": {
                "invoiceDate": "2024-06-10",
                "number": "INV-42",
                "client": "Acme",
                "baseExclVat": 1000.00,
                "ivaRate": "STANDARD",
                "retencion": "STANDARD"
              }
            }
        """.trimIndent()

        val response = controller.createRecord("ws-1", body, "user-1")

        assertEquals(201, response.statusCode)
        assertTrue(response.body?.contains("\"recordType\":\"INVOICE\"") == true)
        assertEquals("ws-1", service.lastWorkspaceId)
        assertEquals("user-1", service.lastUserId)
    }

    @Test
    fun createRecordWithoutUserReturns401() {
        val controller = RecordsController(FakeRecordsService())

        val response = controller.createRecord("ws-1", "{}", null)

        assertEquals(401, response.statusCode)
    }

    @Test
    fun updateRecordRejectsBodyRecordTypeMismatch() {
        val controller = RecordsController(FakeRecordsService())

        val body = """
            {
              "recordType": "EXPENSE",
              "payload": { "documentDate": "2024-06-10" }
            }
        """.trimIndent()

        val response = controller.updateRecord("ws-1", "INVOICE", "2024-06-10", "rec-1", body, "user-1")

        assertEquals(400, response.statusCode)
        val error = JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("recordType must match the path", error.message)
    }

    @Test
    fun listRecordsRequiresMonthOrQuarter() {
        val controller = RecordsController(FakeRecordsService())

        val response = controller.listRecords("ws-1", emptyMap(), "user-1")

        assertEquals(400, response.statusCode)
        val error = JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("month or quarter is required", error.message)
    }

    private class FakeRecordsService : RecordsServicePort {
        var lastWorkspaceId: String? = null
        var lastUserId: String? = null

        override fun createRecord(workspaceId: String, userId: String, request: autonomo.model.RecordRequest): RecordResponse {
            lastWorkspaceId = workspaceId
            lastUserId = userId
            return sampleResponse(workspaceId)
        }

        override fun updateRecord(
            workspaceId: String,
            userId: String,
            recordType: RecordType,
            eventDate: LocalDate,
            recordId: String,
            request: autonomo.model.RecordRequest
        ): RecordResponse? = sampleResponse(workspaceId)

        override fun getRecord(workspaceId: String, recordKey: String): RecordResponse? = sampleResponse(workspaceId)

        override fun deleteRecord(workspaceId: String, recordKey: String) = Unit

        override fun listByMonth(workspaceId: String, month: YearMonth, recordType: RecordType?): RecordsResponse =
            RecordsResponse(emptyList())

        override fun listByQuarter(workspaceId: String, quarterKey: String, recordType: RecordType?): RecordsResponse =
            RecordsResponse(emptyList())

        private fun sampleResponse(workspaceId: String): RecordResponse {
            val payload = JsonSupport.mapper.readTree("{\"ok\":true}")
            return RecordResponse(
                workspaceId = workspaceId,
                recordKey = "INVOICE#2024-06-10#rec-1",
                recordId = "rec-1",
                recordType = RecordType.INVOICE,
                eventDate = LocalDate.parse("2024-06-10"),
                payload = payload,
                createdAt = Instant.parse("2024-06-21T09:30:00Z"),
                updatedAt = Instant.parse("2024-06-21T09:30:00Z"),
                createdBy = "user-1",
                updatedBy = "user-1"
            )
        }
    }
}
