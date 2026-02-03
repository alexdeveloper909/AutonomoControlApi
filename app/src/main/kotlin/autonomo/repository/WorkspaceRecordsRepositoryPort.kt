package autonomo.repository

import autonomo.model.RecordItem
import autonomo.model.RecordType

interface WorkspaceRecordsRepositoryPort {
    fun create(record: RecordItem)
    fun update(record: RecordItem)
    fun get(workspaceId: String, recordKey: String): RecordItem?
    fun delete(workspaceId: String, recordKey: String)
    fun deleteByWorkspaceId(workspaceId: String)
    fun setTtlByWorkspaceId(workspaceId: String, ttlEpoch: Long)
    fun clearTtlByWorkspaceId(workspaceId: String)
    fun queryByWorkspaceRecordKeyPrefix(workspaceId: String, recordKeyPrefix: String): List<RecordItem>
    fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem>
    fun queryByQuarter(workspaceQuarter: String, recordType: RecordType?): List<RecordItem>
    fun isConditionalFailure(ex: Exception): Boolean
}
