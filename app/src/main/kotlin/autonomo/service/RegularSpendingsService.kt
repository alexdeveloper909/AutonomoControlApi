package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.RegularSpending
import autonomo.domain.RegularSpendingRecurrence
import autonomo.model.RecordItem
import autonomo.model.RecordResponse
import autonomo.model.RecordsResponse
import autonomo.model.RegularSpendingOccurrence
import autonomo.model.RegularSpendingOccurrencesResponse
import autonomo.repository.WorkspaceRecordsRepository
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.util.RecordPayloadParser
import java.time.LocalDate

interface RegularSpendingsServicePort {
    fun listDefinitions(workspaceId: String): RecordsResponse
    fun listOccurrences(workspaceId: String, fromInclusive: LocalDate, toInclusive: LocalDate): RegularSpendingOccurrencesResponse
}

class RegularSpendingsService(
    private val repository: WorkspaceRecordsRepositoryPort = WorkspaceRecordsRepository()
) : RegularSpendingsServicePort {
    override fun listDefinitions(workspaceId: String): RecordsResponse {
        val items = repository.queryByWorkspaceRecordKeyPrefix(workspaceId, "REGULAR_SPENDING#")
        return RecordsResponse(items = items.map(::toResponse), nextToken = null)
    }

    override fun listOccurrences(
        workspaceId: String,
        fromInclusive: LocalDate,
        toInclusive: LocalDate
    ): RegularSpendingOccurrencesResponse {
        val records = repository.queryByWorkspaceRecordKeyPrefix(workspaceId, "REGULAR_SPENDING#")
        val occurrences = records.flatMap { record ->
            val spending = parseSpending(record)
            RegularSpendingRecurrence.occurrenceDates(spending, fromInclusive, toInclusive).map { payout ->
                RegularSpendingOccurrence(
                    recordId = record.recordId,
                    name = spending.name,
                    payoutDate = payout,
                    amount = spending.amount
                )
            }
        }.sortedWith(compareBy<RegularSpendingOccurrence> { it.payoutDate }.thenBy { it.name }.thenBy { it.recordId })

        return RegularSpendingOccurrencesResponse(from = fromInclusive, to = toInclusive, items = occurrences)
    }

    private fun parseSpending(record: RecordItem): RegularSpending {
        val payloadNode = JsonSupport.mapper.readTree(record.payloadJson)
        return RecordPayloadParser.parse(record.recordType, payloadNode) as RegularSpending
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
}

