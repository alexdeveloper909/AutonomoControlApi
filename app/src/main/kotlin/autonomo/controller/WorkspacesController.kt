package autonomo.controller

import autonomo.model.UserContext
import autonomo.model.WorkspaceCreateRequest
import autonomo.service.ForbiddenWorkspaceDeleteException
import autonomo.service.WorkspaceAccessPort
import autonomo.service.WorkspaceAccessService
import autonomo.service.WorkspacesService
import autonomo.service.WorkspacesServicePort
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses

class WorkspacesController(
    private val service: WorkspacesServicePort = WorkspacesService(),
    private val access: WorkspaceAccessPort = WorkspaceAccessService()
) {
    fun listWorkspaces(user: UserContext?): HttpResponse {
        val caller = user ?: return HttpResponses.unauthorized()
        val response = service.listWorkspaces(caller)
        return HttpResponses.ok(response)
    }

    fun createWorkspace(body: String?, user: UserContext?): HttpResponse {
        val caller = user ?: return HttpResponses.unauthorized()
        if (body.isNullOrBlank()) return HttpResponses.badRequest("body is required")

        return runCatching {
            val request = Json.readCreateRequest(body)
            val response = service.createWorkspace(caller, request)
            HttpResponses.created(response)
        }.getOrElse { HttpResponses.badRequest(it.message ?: "Invalid request") }
    }

    fun deleteWorkspace(workspaceId: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        if (!access.canAdmin(workspaceId, caller)) {
            return HttpResponses.forbidden("Only workspace owners can delete workspaces")
        }

        return runCatching {
            val deleted = service.deleteWorkspace(caller, workspaceId)
            if (!deleted) {
                HttpResponses.notFound("workspace not found")
            } else {
                HttpResponses.noContent()
            }
        }.getOrElse { error ->
            when (error) {
                is ForbiddenWorkspaceDeleteException -> HttpResponses.forbidden(error.message ?: "Forbidden")
                else -> HttpResponses.serverError(error.message ?: "Failed to delete workspace")
            }
        }
    }

    private object Json {
        fun readCreateRequest(body: String): WorkspaceCreateRequest =
            autonomo.config.JsonSupport.mapper.readValue(body, WorkspaceCreateRequest::class.java)
    }
}
