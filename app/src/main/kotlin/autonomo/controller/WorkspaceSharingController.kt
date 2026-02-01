package autonomo.controller

import autonomo.model.UserContext
import autonomo.model.WorkspaceShareRequest
import autonomo.service.ForbiddenWorkspaceShareException
import autonomo.service.WorkspaceSharingService
import autonomo.service.WorkspaceSharingServicePort
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses

class WorkspaceSharingController(
    private val service: WorkspaceSharingServicePort = WorkspaceSharingService()
) {
    fun shareReadOnly(workspaceId: String?, body: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        if (body.isNullOrBlank()) return HttpResponses.badRequest("body is required")

        return runCatching {
            val request = Json.readShareRequest(body)
            val response = service.shareReadOnly(workspaceId, request.email, caller)
            HttpResponses.ok(response)
        }.getOrElse { error ->
            when (error) {
                is ForbiddenWorkspaceShareException -> HttpResponses.forbidden(error.message ?: "Forbidden")
                is IllegalArgumentException -> HttpResponses.badRequest(error.message ?: "Invalid request")
                else -> HttpResponses.badRequest(error.message ?: "Invalid request")
            }
        }
    }

    private object Json {
        fun readShareRequest(body: String): WorkspaceShareRequest =
            autonomo.config.JsonSupport.mapper.readValue(body, WorkspaceShareRequest::class.java)
    }
}
