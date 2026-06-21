package autonomo.controller

import autonomo.config.JsonSupport
import autonomo.model.BusinessEntitiesResponse
import autonomo.model.BusinessEntityResponse
import autonomo.model.BusinessEntityWriteRequest
import autonomo.model.UserContext
import autonomo.service.BusinessEntitiesService
import autonomo.service.BusinessEntitiesServicePort
import autonomo.service.BusinessEntityNotFoundException
import autonomo.service.WorkspaceAccessPort
import autonomo.service.WorkspaceAccessService
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses
import java.time.Year

class BusinessEntitiesController(
    private val service: BusinessEntitiesServicePort = BusinessEntitiesService(),
    private val access: WorkspaceAccessPort = WorkspaceAccessService()
) {
    fun listBusinessEntities(workspaceId: String?, queryParams: Map<String, String>, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }
        val includeArchived = queryParams["includeArchived"]?.equals("true", ignoreCase = true) == true
        return runCatching {
            HttpResponses.ok(BusinessEntitiesResponse(service.listBusinessEntities(workspaceId, includeArchived)))
        }.getOrElse(::toError)
    }

    fun createBusinessEntity(workspaceId: String?, body: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureWriteAccess(workspaceId, caller)?.let { return it }
        if (body.isNullOrBlank()) return HttpResponses.badRequest("body is required")
        return runCatching {
            val node = JsonSupport.mapper.readTree(body)
            require(!node.has("entityId")) { "entityId is server-generated" }
            val request = JsonSupport.mapper.treeToValue(node, BusinessEntityWriteRequest::class.java)
            HttpResponses.created(BusinessEntityResponse(service.createBusinessEntity(workspaceId, caller.userId, request)))
        }.getOrElse(::toError)
    }

    fun updateBusinessEntity(
        workspaceId: String?,
        entityId: String?,
        body: String?,
        user: UserContext?
    ): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        if (entityId.isNullOrBlank()) return HttpResponses.badRequest("entityId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureWriteAccess(workspaceId, caller)?.let { return it }
        if (body.isNullOrBlank()) return HttpResponses.badRequest("body is required")
        return runCatching {
            val node = JsonSupport.mapper.readTree(body)
            if (node.has("entityId") && node.get("entityId").asText() != entityId) {
                return HttpResponses.badRequest("entityId cannot be changed")
            }
            val request = JsonSupport.mapper.treeToValue(node, BusinessEntityWriteRequest::class.java)
            HttpResponses.ok(BusinessEntityResponse(service.updateBusinessEntity(workspaceId, caller.userId, entityId, request)))
        }.getOrElse(::toError)
    }

    fun archiveBusinessEntity(workspaceId: String?, entityId: String?, user: UserContext?): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        if (entityId.isNullOrBlank()) return HttpResponses.badRequest("entityId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureWriteAccess(workspaceId, caller)?.let { return it }
        return runCatching {
            HttpResponses.ok(BusinessEntityResponse(service.archiveBusinessEntity(workspaceId, caller.userId, entityId)))
        }.getOrElse(::toError)
    }

    fun ukrainianFopSummary(
        workspaceId: String?,
        entityId: String?,
        queryParams: Map<String, String>,
        user: UserContext?
    ): HttpResponse {
        if (workspaceId.isNullOrBlank()) return HttpResponses.badRequest("workspaceId is required")
        if (entityId.isNullOrBlank()) return HttpResponses.badRequest("entityId is required")
        val caller = user ?: return HttpResponses.unauthorized()
        ensureAccess(workspaceId, caller)?.let { return it }
        val year = queryParams["year"]?.let { runCatching { Year.parse(it).value }.getOrNull() }
            ?: return HttpResponses.badRequest("year is required")
        return runCatching {
            HttpResponses.ok(service.ukrainianFopSummary(workspaceId, entityId, year))
        }.getOrElse(::toError)
    }

    private fun toError(error: Throwable): HttpResponse =
        when (error) {
            is BusinessEntityNotFoundException -> HttpResponses.notFound(error.message ?: "business entity not found")
            is IllegalArgumentException -> HttpResponses.badRequest(error.message ?: "Invalid request")
            else -> HttpResponses.badRequest(error.message ?: "Invalid request")
        }

    private fun ensureAccess(workspaceId: String, user: UserContext): HttpResponse? {
        return if (access.canAccess(workspaceId, user)) null else HttpResponses.forbidden("User is not a member of the workspace")
    }

    private fun ensureWriteAccess(workspaceId: String, user: UserContext): HttpResponse? {
        return if (access.canWrite(workspaceId, user)) null else HttpResponses.forbidden("Workspace is read-only for this user")
    }
}
