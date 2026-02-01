package autonomo.service

import autonomo.model.UserContext
import autonomo.model.WorkspaceMember
import autonomo.repository.WorkspaceMembersRepository
import autonomo.repository.WorkspaceMembersRepositoryPort

interface WorkspaceAccessPort {
    fun canAccess(workspaceId: String, user: UserContext): Boolean
    fun canWrite(workspaceId: String, user: UserContext): Boolean
    fun canAdmin(workspaceId: String, user: UserContext): Boolean
    fun getActiveMember(workspaceId: String, user: UserContext): WorkspaceMember?
}

class WorkspaceAccessService(
    private val repository: WorkspaceMembersRepositoryPort = WorkspaceMembersRepository()
) : WorkspaceAccessPort {
    override fun canAccess(workspaceId: String, user: UserContext): Boolean {
        return getActiveMember(workspaceId, user) != null
    }

    override fun canWrite(workspaceId: String, user: UserContext): Boolean {
        val member = getActiveMember(workspaceId, user) ?: return false
        return isWriteRole(member.role)
    }

    override fun canAdmin(workspaceId: String, user: UserContext): Boolean {
        val member = getActiveMember(workspaceId, user) ?: return false
        val role = member.role?.uppercase()
        val status = member.status?.uppercase()
        return role == "OWNER" || status == "OWNER"
    }

    override fun getActiveMember(workspaceId: String, user: UserContext): WorkspaceMember? {
        repository.getByUserId(workspaceId, user.userId)?.let { member ->
            if (isActive(member)) return member
        }

        val emailLower = user.email?.lowercase() ?: return null
        repository.getByEmail(workspaceId, emailLower)?.let { member ->
            if (isActive(member)) return member
        }

        return null
    }

    private fun isActive(member: WorkspaceMember): Boolean {
        val status = member.status?.uppercase()
        if (status == null) return true
        return status in ACTIVE_STATUSES
    }

    private fun isWriteRole(role: String?): Boolean {
        val normalized = role?.uppercase()
        if (normalized == null) return true // backwards compatible default
        return normalized in WRITE_ROLES
    }

    private companion object {
        val ACTIVE_STATUSES = setOf("ACTIVE", "ACCEPTED", "OWNER")
        val WRITE_ROLES = setOf("OWNER", "MEMBER")
    }
}
