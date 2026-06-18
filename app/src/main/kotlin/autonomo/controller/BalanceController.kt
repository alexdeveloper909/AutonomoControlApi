package autonomo.controller

import autonomo.model.UserContext
import autonomo.service.BalanceService
import autonomo.service.BalanceServicePort
import autonomo.service.WorkspaceAccessPort
import autonomo.service.WorkspaceAccessService
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses
import java.time.Year

class BalanceController(
    private val service: BalanceServicePort = BalanceService(),
    private val access: WorkspaceAccessPort = WorkspaceAccessService()
) {
    fun getBalance(workspaceId: String?, queryParams: Map<String, String>, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }

        val year = queryParams["year"]?.let { parseYear(it) }
            ?: if (queryParams.containsKey("year")) {
                return HttpResponses.badRequest("year is invalid")
            } else null
        val accountId = queryParams["accountId"]?.takeIf { it.isNotBlank() }

        return runCatching {
            HttpResponses.ok(service.getBalance(workspaceId, year, accountId))
        }.getOrElse { error ->
            HttpResponses.badRequest(error.message ?: "Invalid request")
        }
    }

    private fun parseYear(value: String): Int? =
        runCatching { Year.parse(value).value }.getOrNull()

    private fun ensureAccess(workspaceId: String, user: UserContext): HttpResponse? {
        return if (access.canAccess(workspaceId, user)) {
            null
        } else {
            HttpResponses.forbidden("User is not a member of the workspace")
        }
    }
}
