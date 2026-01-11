package autonomo.controller

import autonomo.domain.Settings
import autonomo.model.UserContext
import autonomo.service.WorkspaceAccessPort
import autonomo.service.WorkspaceAccessService
import autonomo.service.WorkspaceSettingsService
import autonomo.service.WorkspaceSettingsServicePort
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses

class WorkspaceSettingsController(
    private val service: WorkspaceSettingsServicePort = WorkspaceSettingsService(),
    private val access: WorkspaceAccessPort = WorkspaceAccessService()
) {
    fun getSettings(workspaceId: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }

        val settings = service.getSettings(workspaceId) ?: return HttpResponses.notFound("workspace settings not found")
        return HttpResponses.ok(mapOf("workspaceId" to workspaceId, "settings" to settings))
    }

    fun putSettings(workspaceId: String?, body: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }
        val settings = parseSettings(body) ?: return HttpResponses.badRequest("Invalid settings")

        service.putSettings(workspaceId, settings, caller.userId)
        return HttpResponses.ok(mapOf("workspaceId" to workspaceId, "settings" to settings))
    }

    private fun parseSettings(body: String?): Settings? {
        if (body.isNullOrBlank()) return null
        return runCatching { autonomo.config.JsonSupport.mapper.readValue(body, Settings::class.java) }.getOrNull()
    }

    private fun ensureAccess(workspaceId: String, user: UserContext): HttpResponse? {
        return if (access.canAccess(workspaceId, user)) {
            null
        } else {
            HttpResponses.forbidden("User is not a member of the workspace")
        }
    }
}

