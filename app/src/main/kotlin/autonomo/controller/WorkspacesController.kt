package autonomo.controller

import autonomo.model.UserContext
import autonomo.model.WorkspaceCreateRequest
import autonomo.service.WorkspacesService
import autonomo.service.WorkspacesServicePort
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses

class WorkspacesController(
    private val service: WorkspacesServicePort = WorkspacesService()
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

    private object Json {
        fun readCreateRequest(body: String): WorkspaceCreateRequest =
            autonomo.config.JsonSupport.mapper.readValue(body, WorkspaceCreateRequest::class.java)
    }
}

