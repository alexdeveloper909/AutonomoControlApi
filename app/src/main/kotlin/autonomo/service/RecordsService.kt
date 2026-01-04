package autonomo.service

import autonomo.config.JsonSupport
import autonomo.model.RecordItem
import autonomo.model.RecordKey
import autonomo.model.RecordRequest
import autonomo.model.RecordResponse
import autonomo.model.RecordType
import autonomo.model.RecordsResponse
import autonomo.repository.WorkspaceRecordsRepository
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.util.RecordPayloadParser
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

interface RecordsServicePort {
    fun createRecord(workspaceId: String, userId: String, request: RecordRequest): RecordResponse
    fun updateRecord(
        workspaceId: String,
        userId: String,
        recordType: RecordType,
        eventDate: LocalDate,
        recordId: String,
        request: RecordRequest
    ): RecordResponse?
    fun getRecord(workspaceId: String, recordKey: String): RecordResponse?
    fun deleteRecord(workspaceId: String, recordKey: String)
    fun listByMonth(workspaceId: String, month: YearMonth, recordType: RecordType?): RecordsResponse
    fun listByQuarter(workspaceId: String, quarterKey: String, recordType: RecordType?): RecordsResponse
}

class RecordsService(
    private val repository: WorkspaceRecordsRepositoryPort = WorkspaceRecordsRepository()
) : RecordsServicePort {
    override fun createRecord(
        workspaceId: String,
        userId: String,
        request: RecordRequest
    ): RecordResponse {
        val payload = RecordPayloadParser.parse(request.recordType, request.payload)
        val eventDate = RecordPayloadParser.eventDate(request.recordType, payload)
        val recordId = request.recordId ?: UUID.randomUUID().toString()
        val recordKey = RecordKey.build(request.recordType, eventDate, recordId)
        val now = Instant.now()
        val item = RecordItem(
            workspaceId = workspaceId,
            recordKey = recordKey,
            recordId = recordId,
            recordType = request.recordType,
            eventDate = eventDate,
            payloadJson = RecordPayloadParser.toJson(payload),
            workspaceMonth = workspaceMonthKey(workspaceId, eventDate),
            workspaceQuarter = workspaceQuarterKey(workspaceId, eventDate),
            createdAt = now,
            updatedAt = now,
            createdBy = userId,
            updatedBy = userId
        )
        try {
            repository.create(item)
        } catch (ex: Exception) {
            if (repository.isConditionalFailure(ex)) {
                throw RecordConflictException("record already exists")
            }
            throw ex
        }
        return toResponse(item)
    }

    override fun updateRecord(
        workspaceId: String,
        userId: String,
        recordType: RecordType,
        eventDate: LocalDate,
        recordId: String,
        request: RecordRequest
    ): RecordResponse? {
        val existing = repository.get(workspaceId, RecordKey.build(recordType, eventDate, recordId))
            ?: return null

        val payload = RecordPayloadParser.parse(recordType, request.payload)
        val derivedEventDate = RecordPayloadParser.eventDate(recordType, payload)
        require(derivedEventDate == eventDate) {
            "eventDate must match the existing recordKey"
        }

        val now = Instant.now()
        val updated = existing.copy(
            payloadJson = RecordPayloadParser.toJson(payload),
            updatedAt = now,
            updatedBy = userId
        )
        repository.update(updated)
        return toResponse(updated)
    }

    override fun getRecord(workspaceId: String, recordKey: String): RecordResponse? {
        val item = repository.get(workspaceId, recordKey) ?: return null
        return toResponse(item)
    }

    override fun deleteRecord(workspaceId: String, recordKey: String) {
        repository.delete(workspaceId, recordKey)
    }

    override fun listByMonth(
        workspaceId: String,
        month: YearMonth,
        recordType: RecordType?
    ): RecordsResponse {
        val items = repository.queryByMonth(workspaceMonthKey(workspaceId, month.atDay(1)), recordType)
        return RecordsResponse(items.map(::toResponse))
    }

    override fun listByQuarter(
        workspaceId: String,
        quarterKey: String,
        recordType: RecordType?
    ): RecordsResponse {
        val items = repository.queryByQuarter(workspaceQuarterKey(workspaceId, quarterKey), recordType)
        return RecordsResponse(items.map(::toResponse))
    }

    private fun toResponse(item: RecordItem): RecordResponse {
        return RecordResponse(
            workspaceId = item.workspaceId,
            recordKey = item.recordKey,
            recordId = item.recordId,
            recordType = item.recordType,
            eventDate = item.eventDate,
            payload = JsonSupport.mapper.readTree(item.payloadJson),
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            createdBy = item.createdBy,
            updatedBy = item.updatedBy
        )
    }

    private fun workspaceMonthKey(workspaceId: String, eventDate: LocalDate): String {
        val month = eventDate.withDayOfMonth(1).toString().substring(0, 7)
        return "WS#$workspaceId#M#$month"
    }

    private fun workspaceQuarterKey(workspaceId: String, eventDate: LocalDate): String {
        val quarter = ((eventDate.monthValue - 1) / 3) + 1
        return "WS#$workspaceId#Q#${eventDate.year}-Q$quarter"
    }

    private fun workspaceQuarterKey(workspaceId: String, quarterKey: String): String {
        return "WS#$workspaceId#Q#$quarterKey"
    }
}

class RecordConflictException(message: String) : RuntimeException(message)
