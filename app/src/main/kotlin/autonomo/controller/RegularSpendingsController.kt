package autonomo.controller

import autonomo.model.UserContext
import autonomo.service.RegularSpendingsService
import autonomo.service.RegularSpendingsServicePort
import autonomo.service.WorkspaceAccessPort
import autonomo.service.WorkspaceAccessService
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses
import java.time.LocalDate

class RegularSpendingsController(
    private val service: RegularSpendingsServicePort = RegularSpendingsService(),
    private val access: WorkspaceAccessPort = WorkspaceAccessService()
) {
    fun listDefinitions(workspaceId: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }

        val res = service.listDefinitions(workspaceId)
        return HttpResponses.ok(res)
    }

    fun listOccurrences(
        workspaceId: String?,
        queryParams: Map<String, String>,
        user: UserContext?
    ): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }

        val from = parseDate(queryParams["from"]) ?: return HttpResponses.badRequest("from is required (YYYY-MM-DD)")
        val to = parseDate(queryParams["to"]) ?: return HttpResponses.badRequest("to is required (YYYY-MM-DD)")
        if (to.isBefore(from)) return HttpResponses.badRequest("to must be >= from")

        val res = service.listOccurrences(workspaceId, from, to)
        return HttpResponses.ok(res)
    }

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun ensureAccess(workspaceId: String, user: UserContext): HttpResponse? {
        return if (access.canAccess(workspaceId, user)) {
            null
        } else {
            HttpResponses.forbidden("User is not a member of the workspace")
        }
    }
}

