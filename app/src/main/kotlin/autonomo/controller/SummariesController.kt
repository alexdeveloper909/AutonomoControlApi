package autonomo.controller

import autonomo.domain.Settings
import autonomo.model.UserContext
import autonomo.service.SummariesService
import autonomo.service.SummariesServicePort
import autonomo.service.WorkspaceAccessPort
import autonomo.service.WorkspaceAccessService
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses

class SummariesController(
    private val service: SummariesServicePort = SummariesService(),
    private val access: WorkspaceAccessPort = WorkspaceAccessService()
) {
    fun monthSummaries(workspaceId: String?, body: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }
        val settings = parseSettings(body) ?: return HttpResponses.badRequest("Invalid settings")
        return runCatching {
            HttpResponses.ok(service.monthSummaries(workspaceId, settings))
        }.getOrElse { HttpResponses.badRequest(it.message ?: "Invalid request") }
    }

    fun quarterSummaries(workspaceId: String?, body: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }
        val settings = parseSettings(body) ?: return HttpResponses.badRequest("Invalid settings")
        return runCatching {
            HttpResponses.ok(service.quarterSummaries(workspaceId, settings))
        }.getOrElse { HttpResponses.badRequest(it.message ?: "Invalid request") }
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
