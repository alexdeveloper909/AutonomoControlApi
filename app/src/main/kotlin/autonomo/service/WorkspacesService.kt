package autonomo.service

import autonomo.model.UserContext
import autonomo.model.WorkspaceCreateRequest
import autonomo.model.WorkspaceCreateResponse
import autonomo.model.WorkspaceSummary
import autonomo.model.WorkspacesListResponse
import autonomo.model.WorkspaceMember
import autonomo.repository.WorkspaceMembersRepository
import autonomo.repository.WorkspaceMembershipsRepositoryPort
import autonomo.repository.WorkspaceRecordsRepository
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.repository.WorkspacesRepository
import autonomo.repository.WorkspacesRepositoryPort
import autonomo.repository.WorkspaceSettingsRepository
import autonomo.repository.WorkspaceSettingsRepositoryPort
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import java.util.UUID

interface WorkspacesServicePort {
    fun listWorkspaces(user: UserContext): WorkspacesListResponse
    fun createWorkspace(user: UserContext, request: WorkspaceCreateRequest): WorkspaceCreateResponse
    fun deleteWorkspace(user: UserContext, workspaceId: String): Boolean
}

class WorkspacesService(
    private val workspaces: WorkspacesRepositoryPort = WorkspacesRepository(),
    private val memberships: WorkspaceMembershipsRepositoryPort = WorkspaceMembersRepository(),
    private val settings: WorkspaceSettingsRepositoryPort = WorkspaceSettingsRepository(),
    private val records: WorkspaceRecordsRepositoryPort = WorkspaceRecordsRepository()
) : WorkspacesServicePort {
    override fun listWorkspaces(user: UserContext): WorkspacesListResponse {
        val memberItems = listActiveMemberships(user)
        val workspaceIds = memberItems.map { it.workspaceId }.distinct()
        val items = workspaces.batchGet(workspaceIds)
        val memberByWorkspace = memberItems.associateBy { it.workspaceId }

        val sharedByWorkspaceForOwners = memberItems
            .filter { isOwnerMembership(it) }
            .associate { ownerMembership ->
                ownerMembership.workspaceId to hasActiveReadOnlyMembers(ownerMembership.workspaceId)
            }

        val summaries = items
            .map { ws ->
                val membership = memberByWorkspace[ws.workspaceId]
                val role = membership?.role?.uppercase()
                val accessMode = if (role == "READER") "READ_ONLY" else "READ_WRITE"
                val sharedWithMe = role == "READER"
                val sharedByMe = isOwnerMembership(membership) && (sharedByWorkspaceForOwners[ws.workspaceId] == true)
                WorkspaceSummary(
                    workspaceId = ws.workspaceId,
                    name = ws.name,
                    role = membership?.role,
                    status = membership?.status,
                    accessMode = accessMode,
                    sharedByMe = sharedByMe,
                    sharedWithMe = sharedWithMe
                )
            }
            .sortedBy { it.name.lowercase() }

        return WorkspacesListResponse(items = summaries)
    }

    override fun createWorkspace(user: UserContext, request: WorkspaceCreateRequest): WorkspaceCreateResponse {
        val name = request.name.trim()
        require(name.isNotBlank()) { "name is required" }

        val workspaceId = UUID.randomUUID().toString()
        try {
            workspaces.put(workspaceId, name, user.userId)
        } catch (e: ConditionalCheckFailedException) {
            throw IllegalStateException("workspace already exists")
        }

        memberships.putMember(
            workspaceId = workspaceId,
            memberKey = "USER#${user.userId}",
            userId = user.userId,
            emailLower = user.email?.lowercase(),
            role = "OWNER",
            status = "OWNER"
        )
        settings.putSettings(workspaceId, request.settings, updatedBy = user.userId)

        return WorkspaceCreateResponse(
            workspace = WorkspaceSummary(
                workspaceId = workspaceId,
                name = name,
                role = "OWNER",
                status = "OWNER",
                accessMode = "READ_WRITE",
                sharedByMe = false,
                sharedWithMe = false
            ),
            settings = request.settings
        )
    }

    override fun deleteWorkspace(user: UserContext, workspaceId: String): Boolean {
        val ws = workspaces.get(workspaceId) ?: return false
        if (ws.ownerUserId != user.userId) {
            throw ForbiddenWorkspaceDeleteException("Only workspace owners can delete workspaces")
        }

        records.deleteByWorkspaceId(workspaceId)
        memberships.deleteByWorkspaceId(workspaceId)
        settings.deleteSettings(workspaceId)
        workspaces.delete(workspaceId)
        return true
    }

    private fun listActiveMemberships(user: UserContext): List<WorkspaceMember> {
        val byUserId = memberships.listByUserId(user.userId).filter { isActive(it) }
        val byEmail = user.email?.lowercase()?.let { memberships.listByEmail(it).filter { m -> isActive(m) } }.orEmpty()

        val combined = (byUserId + byEmail)
        return combined
            .groupBy { it.workspaceId }
            .map { (_, memberships) ->
                memberships.firstOrNull { it.memberKey.startsWith("USER#") } ?: memberships.first()
            }
    }

    private fun isActive(member: WorkspaceMember): Boolean {
        val status = member.status?.uppercase()
        if (status == null) return true
        return status in ACTIVE_STATUSES
    }

    private fun hasActiveReadOnlyMembers(workspaceId: String): Boolean {
        val members = memberships.listByWorkspaceId(workspaceId)
        return members.any { m ->
            val role = m.role?.uppercase()
            val status = m.status?.uppercase()
            val isActive = status == null || status in ACTIVE_STATUSES
            isActive && role == "READER"
        }
    }

    private fun isOwnerMembership(member: WorkspaceMember?): Boolean {
        if (member == null) return false
        val role = member.role?.uppercase()
        val status = member.status?.uppercase()
        return role == "OWNER" || status == "OWNER"
    }

    private companion object {
        val ACTIVE_STATUSES = setOf("ACTIVE", "ACCEPTED", "OWNER")
    }
}

class ForbiddenWorkspaceDeleteException(message: String) : RuntimeException(message)
