package autonomo.service

import autonomo.model.UserContext
import autonomo.model.WorkspaceMember
import autonomo.repository.WorkspaceMembershipsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WorkspaceSharingServiceTest {
    @Test
    fun shareReadOnlyRequiresOwner() {
        val repo = FakeMembershipsRepo()
        val service = WorkspaceSharingService(
            memberships = repo,
            access = FakeAccess(canAdmin = false)
        )

        assertThrows(ForbiddenWorkspaceShareException::class.java) {
            service.shareReadOnly("ws-1", "user@example.com", UserContext("user-1", "user-1@example.com"))
        }
    }

    @Test
    fun shareReadOnlyWritesEmailMembership() {
        val repo = FakeMembershipsRepo()
        val service = WorkspaceSharingService(
            memberships = repo,
            access = FakeAccess(canAdmin = true)
        )

        val res = service.shareReadOnly("ws-1", "User@Example.com", UserContext("owner-1", "owner@example.com"))

        assertEquals("ws-1", res.workspaceId)
        assertEquals("user@example.com", res.emailLower)
        assertEquals("READER", res.role)
        assertEquals("ACTIVE", res.status)
        assertEquals("EMAIL#user@example.com", repo.lastMemberKey)
        assertEquals("user@example.com", repo.lastEmailLower)
        assertEquals("READER", repo.lastRole)
    }

    private class FakeMembershipsRepo : WorkspaceMembershipsRepositoryPort {
        var lastWorkspaceId: String? = null
        var lastMemberKey: String? = null
        var lastEmailLower: String? = null
        var lastRole: String? = null
        var lastStatus: String? = null

        override fun listByUserId(userId: String): List<WorkspaceMember> = emptyList()

        override fun listByEmail(emailLower: String): List<WorkspaceMember> = emptyList()

        override fun listByWorkspaceId(workspaceId: String): List<WorkspaceMember> = emptyList()

        override fun putMember(
            workspaceId: String,
            memberKey: String,
            userId: String?,
            emailLower: String?,
            role: String?,
            status: String?
        ) {
            lastWorkspaceId = workspaceId
            lastMemberKey = memberKey
            lastEmailLower = emailLower
            lastRole = role
            lastStatus = status
        }

        override fun deleteMember(workspaceId: String, memberKey: String) = Unit

        override fun deleteByWorkspaceId(workspaceId: String) = Unit
    }

    private class FakeAccess(private val canAdmin: Boolean) : WorkspaceAccessPort {
        override fun canAccess(workspaceId: String, user: UserContext): Boolean = true

        override fun canWrite(workspaceId: String, user: UserContext): Boolean = true

        override fun canAdmin(workspaceId: String, user: UserContext): Boolean = canAdmin

        override fun getActiveMember(workspaceId: String, user: UserContext) = null
    }
}
