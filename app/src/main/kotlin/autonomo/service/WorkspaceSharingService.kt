package autonomo.service

import autonomo.model.UserContext
import autonomo.model.WorkspaceShareResponse
import autonomo.repository.WorkspaceMembershipsRepositoryPort
import autonomo.repository.WorkspaceMembersRepository

interface WorkspaceSharingServicePort {
    fun shareReadOnly(workspaceId: String, email: String, caller: UserContext): WorkspaceShareResponse
}

class WorkspaceSharingService(
    private val memberships: WorkspaceMembershipsRepositoryPort = WorkspaceMembersRepository(),
    private val access: WorkspaceAccessPort = WorkspaceAccessService()
) : WorkspaceSharingServicePort {
    override fun shareReadOnly(workspaceId: String, email: String, caller: UserContext): WorkspaceShareResponse {
        if (!access.canAdmin(workspaceId, caller)) {
            throw ForbiddenWorkspaceShareException("Only workspace owners can share workspaces")
        }

        val emailLower = email.trim().lowercase()
        require(emailLower.isNotBlank()) { "email is required" }
        require(emailLower.contains("@")) { "email is invalid" }
        require(!emailLower.contains(" ")) { "email is invalid" }

        memberships.putMember(
            workspaceId = workspaceId,
            memberKey = "EMAIL#$emailLower",
            userId = null,
            emailLower = emailLower,
            role = "READER",
            status = "ACTIVE"
        )

        return WorkspaceShareResponse(
            workspaceId = workspaceId,
            emailLower = emailLower,
            role = "READER",
            status = "ACTIVE"
        )
    }
}

class ForbiddenWorkspaceShareException(message: String) : RuntimeException(message)
