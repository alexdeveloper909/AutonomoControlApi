package autonomo.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate

data class RecordItem(
    val workspaceId: String,
    val recordKey: String,
    val recordId: String,
    val recordType: RecordType,
    val eventDate: LocalDate,
    val payloadJson: String,
    val workspaceMonth: String,
    val workspaceQuarter: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String,
    val updatedBy: String
)

data class RecordResponse(
    val workspaceId: String,
    val recordKey: String,
    val recordId: String,
    val recordType: RecordType,
    val eventDate: LocalDate,
    val payload: JsonNode,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String,
    val updatedBy: String
)

data class RecordsResponse(
    val items: List<RecordResponse>
)
