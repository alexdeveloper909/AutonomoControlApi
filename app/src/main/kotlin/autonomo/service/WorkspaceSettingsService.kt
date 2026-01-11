package autonomo.service

import autonomo.domain.Settings
import autonomo.repository.WorkspaceSettingsRepository
import autonomo.repository.WorkspaceSettingsRepositoryPort

interface WorkspaceSettingsServicePort {
    fun getSettings(workspaceId: String): Settings?
    fun putSettings(workspaceId: String, settings: Settings, updatedBy: String)
}

class WorkspaceSettingsService(
    private val repository: WorkspaceSettingsRepositoryPort = WorkspaceSettingsRepository()
) : WorkspaceSettingsServicePort {
    override fun getSettings(workspaceId: String): Settings? = repository.getSettings(workspaceId)

    override fun putSettings(workspaceId: String, settings: Settings, updatedBy: String) {
        repository.putSettings(workspaceId, settings, updatedBy)
    }
}

