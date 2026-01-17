package autonomo.controller

import autonomo.config.JsonSupport
import autonomo.model.RecordResponse
import autonomo.model.RecordType
import autonomo.model.RecordsResponse
import autonomo.model.UserContext
import autonomo.service.RecordsListOptions
import autonomo.service.RecordsServicePort
import autonomo.service.WorkspaceAccessPort
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
        val controller = RecordsController(service, FakeAccessService(true))

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

        val response = controller.createRecord("ws-1", body, UserContext("user-1", "user@example.com"))

        assertEquals(201, response.statusCode)
        assertTrue(response.body?.contains("\"recordType\":\"INVOICE\"") == true)
        assertEquals("ws-1", service.lastWorkspaceId)
        assertEquals("user-1", service.lastUserId)
    }

    @Test
    fun createRecordWithoutUserReturns401() {
        val controller = RecordsController(FakeRecordsService(), FakeAccessService(true))

        val response = controller.createRecord("ws-1", "{}", null)

        assertEquals(401, response.statusCode)
    }

    @Test
    fun updateRecordRejectsBodyRecordTypeMismatch() {
        val controller = RecordsController(FakeRecordsService(), FakeAccessService(true))

        val body = """
            {
              "recordType": "EXPENSE",
              "payload": { "documentDate": "2024-06-10" }
            }
        """.trimIndent()

        val response = controller.updateRecord(
            "ws-1",
            "INVOICE",
            "2024-06-10",
            "rec-1",
            body,
            UserContext("user-1", "user@example.com")
        )

        assertEquals(400, response.statusCode)
        val error = JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("recordType must match the path", error.message)
    }

    @Test
    fun listRecordsRequiresMonthOrQuarter() {
        val controller = RecordsController(FakeRecordsService(), FakeAccessService(true))

        val response = controller.listRecords("ws-1", emptyMap(), UserContext("user-1", "user@example.com"))

        assertEquals(400, response.statusCode)
        val error = JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("month or quarter is required", error.message)
    }

    @Test
    fun listRecordsRejectsInvalidSort() {
        val controller = RecordsController(FakeRecordsService(), FakeAccessService(true))

        val response = controller.listRecords(
            "ws-1",
            mapOf("month" to "2024-06", "sort" to "nope"),
            UserContext("user-1", "user@example.com")
        )

        assertEquals(400, response.statusCode)
        val error = JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("sort is invalid", error.message)
    }

    @Test
    fun listRecordsRejectsInvalidLimit() {
        val controller = RecordsController(FakeRecordsService(), FakeAccessService(true))

        val response = controller.listRecords(
            "ws-1",
            mapOf("month" to "2024-06", "limit" to "0"),
            UserContext("user-1", "user@example.com")
        )

        assertEquals(400, response.statusCode)
        val error = JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("limit is invalid", error.message)
    }

    @Test
    fun listRecordsRejectsNextTokenWithoutLimit() {
        val controller = RecordsController(FakeRecordsService(), FakeAccessService(true))

        val response = controller.listRecords(
            "ws-1",
            mapOf("month" to "2024-06", "nextToken" to "abc"),
            UserContext("user-1", "user@example.com")
        )

        assertEquals(400, response.statusCode)
        val error = JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("limit is required when nextToken is provided", error.message)
    }

    @Test
    fun createRecordWithoutMembershipReturns403() {
        val controller = RecordsController(FakeRecordsService(), FakeAccessService(false))

        val response = controller.createRecord("ws-1", "{}", UserContext("user-1", "user@example.com"))

        assertEquals(403, response.statusCode)
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

        override fun listByMonth(
            workspaceId: String,
            month: YearMonth,
            recordType: RecordType?,
            options: RecordsListOptions
        ): RecordsResponse = RecordsResponse(emptyList(), nextToken = null)

        override fun listByQuarter(
            workspaceId: String,
            quarterKey: String,
            recordType: RecordType?,
            options: RecordsListOptions
        ): RecordsResponse = RecordsResponse(emptyList(), nextToken = null)

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

    private class FakeAccessService(private val allowed: Boolean) : WorkspaceAccessPort {
        override fun canAccess(workspaceId: String, user: UserContext): Boolean = allowed
    }
}
