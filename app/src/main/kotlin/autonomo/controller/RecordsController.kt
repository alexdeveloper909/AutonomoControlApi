package autonomo.controller

import autonomo.model.RecordKey
import autonomo.model.RecordRequest
import autonomo.model.RecordType
import autonomo.service.RecordsService
import autonomo.service.RecordsServicePort
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses
import java.time.LocalDate
import java.time.YearMonth

class RecordsController(
    private val service: RecordsServicePort = RecordsService()
) {
    fun createRecord(workspaceId: String?, body: String?, userId: String?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = userId ?: return HttpResponses.unauthorized()
        if (body.isNullOrBlank()) return HttpResponses.badRequest("body is required")

        return runCatching {
            val request = Json.readRequest(body)
            val response = service.createRecord(workspaceId, caller, request)
            HttpResponses.created(response)
        }.getOrElse { error ->
            when (error) {
                is autonomo.service.RecordConflictException -> HttpResponses.conflict(error.message ?: "Conflict")
                else -> HttpResponses.badRequest(error.message ?: "Invalid request")
            }
        }
    }

    fun updateRecord(
        workspaceId: String?,
        recordTypeParam: String?,
        eventDateParam: String?,
        recordId: String?,
        body: String?,
        userId: String?
    ): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = userId ?: return HttpResponses.unauthorized()
        if (body.isNullOrBlank()) return HttpResponses.badRequest("body is required")
        val recordType = parseRecordType(recordTypeParam) ?: return HttpResponses.badRequest("recordType is invalid")
        val eventDate = parseEventDate(eventDateParam) ?: return HttpResponses.badRequest("eventDate is invalid")
        if (recordId.isNullOrBlank()) return HttpResponses.badRequest("recordId is required")

        return runCatching {
            val request = Json.readRequest(body)
            if (request.recordType != recordType) {
                return HttpResponses.badRequest("recordType must match the path")
            }
            if (request.recordId != null && request.recordId != recordId) {
                return HttpResponses.badRequest("recordId must match the path when provided")
            }
            val response = service.updateRecord(workspaceId, caller, recordType, eventDate, recordId, request)
                ?: return HttpResponses.notFound("record not found")
            HttpResponses.ok(response)
        }.getOrElse { HttpResponses.badRequest(it.message ?: "Invalid request") }
    }

    fun getRecord(
        workspaceId: String?,
        recordTypeParam: String?,
        eventDateParam: String?,
        recordId: String?,
        userId: String?
    ): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = userId ?: return HttpResponses.unauthorized()
        val recordType = parseRecordType(recordTypeParam) ?: return HttpResponses.badRequest("recordType is invalid")
        val eventDate = parseEventDate(eventDateParam) ?: return HttpResponses.badRequest("eventDate is invalid")
        if (recordId.isNullOrBlank()) return HttpResponses.badRequest("recordId is required")

        val recordKey = RecordKey.build(recordType, eventDate, recordId)
        val response = service.getRecord(workspaceId, recordKey) ?: return HttpResponses.notFound("record not found")
        return HttpResponses.ok(response)
    }

    fun deleteRecord(
        workspaceId: String?,
        recordTypeParam: String?,
        eventDateParam: String?,
        recordId: String?,
        userId: String?
    ): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        userId ?: return HttpResponses.unauthorized()
        val recordType = parseRecordType(recordTypeParam) ?: return HttpResponses.badRequest("recordType is invalid")
        val eventDate = parseEventDate(eventDateParam) ?: return HttpResponses.badRequest("eventDate is invalid")
        if (recordId.isNullOrBlank()) return HttpResponses.badRequest("recordId is required")

        val recordKey = RecordKey.build(recordType, eventDate, recordId)
        service.deleteRecord(workspaceId, recordKey)
        return HttpResponses.noContent()
    }

    fun listRecords(
        workspaceId: String?,
        queryParams: Map<String, String>,
        userId: String?
    ): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        userId ?: return HttpResponses.unauthorized()

        val month = queryParams["month"]
        val quarter = queryParams["quarter"]
        if (!month.isNullOrBlank() && !quarter.isNullOrBlank()) {
            return HttpResponses.badRequest("Use either month or quarter")
        }

        val recordType = queryParams["recordType"]?.let { parseRecordType(it) }
            ?: if (queryParams.containsKey("recordType")) {
                return HttpResponses.badRequest("recordType is invalid")
            } else null

        return when {
            !month.isNullOrBlank() -> {
                val yearMonth = parseMonth(month) ?: return HttpResponses.badRequest("month is invalid")
                val response = service.listByMonth(workspaceId, yearMonth, recordType)
                HttpResponses.ok(response)
            }
            !quarter.isNullOrBlank() -> {
                val quarterKey = parseQuarter(quarter) ?: return HttpResponses.badRequest("quarter is invalid")
                val response = service.listByQuarter(workspaceId, quarterKey, recordType)
                HttpResponses.ok(response)
            }
            else -> HttpResponses.badRequest("month or quarter is required")
        }
    }

    private fun parseMonth(value: String): YearMonth? {
        return runCatching { YearMonth.parse(value) }.getOrNull()
    }

    private fun parseQuarter(value: String): String? {
        return if (QUARTER_REGEX.matches(value)) value else null
    }

    private fun parseEventDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun parseRecordType(value: String?): RecordType? {
        if (value.isNullOrBlank()) return null
        return runCatching { RecordType.valueOf(value.uppercase()) }.getOrNull()
    }

    private object Json {
        fun readRequest(body: String): RecordRequest =
            autonomo.config.JsonSupport.mapper.readValue(body, RecordRequest::class.java)
    }

    private companion object {
        val QUARTER_REGEX = Regex("\\d{4}-Q[1-4]")
    }
}
