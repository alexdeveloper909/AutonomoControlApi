package autonomo.repository

import autonomo.model.RecordItem
import autonomo.model.RecordType

interface WorkspaceRecordsRepositoryPort {
    fun create(record: RecordItem)
    fun update(record: RecordItem)
    fun get(workspaceId: String, recordKey: String): RecordItem?
    fun delete(workspaceId: String, recordKey: String)
    fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem>
    fun queryByQuarter(workspaceQuarter: String, recordType: RecordType?): List<RecordItem>
    fun isConditionalFailure(ex: Exception): Boolean
}

