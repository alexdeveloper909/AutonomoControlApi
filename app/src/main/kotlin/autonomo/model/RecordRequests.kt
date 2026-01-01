package autonomo.model

import com.fasterxml.jackson.databind.JsonNode

data class RecordRequest(
    val recordType: RecordType,
    val recordId: String? = null,
    val payload: JsonNode
)
