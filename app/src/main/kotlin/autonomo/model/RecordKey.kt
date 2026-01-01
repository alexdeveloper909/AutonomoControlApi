package autonomo.model

import java.time.LocalDate

object RecordKey {
    fun build(recordType: RecordType, eventDate: LocalDate, recordId: String): String {
        return "${recordType.name}#${eventDate}#${recordId}"
    }

    fun parse(value: String): ParsedRecordKey? {
        val parts = value.split("#")
        if (parts.size != 3) return null
        val recordType = runCatching { RecordType.valueOf(parts[0]) }.getOrNull() ?: return null
        val eventDate = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        val recordId = parts[2]
        if (recordId.isBlank()) return null
        return ParsedRecordKey(recordType, eventDate, recordId)
    }
}

data class ParsedRecordKey(
    val recordType: RecordType,
    val eventDate: LocalDate,
    val recordId: String
)
