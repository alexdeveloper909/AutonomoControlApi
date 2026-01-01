package autonomo.service

import autonomo.model.UserContext
import autonomo.model.WorkspaceMember
import autonomo.repository.WorkspaceMembersRepository
import autonomo.repository.WorkspaceMembersRepositoryPort

interface WorkspaceAccessPort {
    fun canAccess(workspaceId: String, user: UserContext): Boolean
}

class WorkspaceAccessService(
    private val repository: WorkspaceMembersRepositoryPort = WorkspaceMembersRepository()
) : WorkspaceAccessPort {
    override fun canAccess(workspaceId: String, user: UserContext): Boolean {
        repository.getByUserId(workspaceId, user.userId)?.let { member ->
            return isActive(member)
        }

        val emailLower = user.email?.lowercase() ?: return false
        repository.getByEmail(workspaceId, emailLower)?.let { member ->
            return isActive(member)
        }

        return false
    }

    private fun isActive(member: WorkspaceMember): Boolean {
        val status = member.status?.uppercase()
        if (status == null) return true
        return status in ACTIVE_STATUSES
    }

    private companion object {
        val ACTIVE_STATUSES = setOf("ACTIVE", "ACCEPTED", "OWNER")
    }
}
