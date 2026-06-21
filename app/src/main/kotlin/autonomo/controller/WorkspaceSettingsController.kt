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
        ensureWriteAccess(workspaceId, caller)?.let { return it }
        val parsed = parseSettings(body) ?: return HttpResponses.badRequest("Invalid settings")

        return runCatching {
            service.putSettings(
                workspaceId = workspaceId,
                settings = parsed.settings,
                updatedBy = caller.userId,
                preserveExistingBalanceAccounts = !parsed.includesBalanceAccounts,
                preserveExistingEntities = !parsed.includesEntities
            )
            val responseSettings = service.getSettings(workspaceId) ?: parsed.settings
            HttpResponses.ok(mapOf("workspaceId" to workspaceId, "settings" to responseSettings))
        }.getOrElse { error ->
            HttpResponses.badRequest(error.message ?: "Invalid settings")
        }
    }

    private data class ParsedSettings(
        val settings: Settings,
        val includesBalanceAccounts: Boolean,
        val includesEntities: Boolean
    )

    private fun parseSettings(body: String?): ParsedSettings? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val node = autonomo.config.JsonSupport.mapper.readTree(body)
            ParsedSettings(
                settings = autonomo.config.JsonSupport.mapper.treeToValue(node, Settings::class.java),
                includesBalanceAccounts = node.has("balanceAccounts"),
                includesEntities = node.has("entities")
            )
        }.getOrNull()
    }

    private fun ensureAccess(workspaceId: String, user: UserContext): HttpResponse? {
        return if (access.canAccess(workspaceId, user)) {
            null
        } else {
            HttpResponses.forbidden("User is not a member of the workspace")
        }
    }

    private fun ensureWriteAccess(workspaceId: String, user: UserContext): HttpResponse? {
        return if (access.canWrite(workspaceId, user)) {
            null
        } else {
            HttpResponses.forbidden("Workspace is read-only for this user")
        }
    }
}
