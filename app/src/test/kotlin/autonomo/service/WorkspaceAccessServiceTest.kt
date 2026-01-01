package autonomo.service

import autonomo.model.UserContext
import autonomo.model.WorkspaceMember
import autonomo.repository.WorkspaceMembersRepositoryPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspaceAccessServiceTest {
    @Test
    fun allowsActiveUserMembership() {
        val repo = FakeMembersRepository(
            memberByUserId = WorkspaceMember(
                workspaceId = "ws-1",
                memberKey = "USER#user-1",
                userId = "user-1",
                emailLower = null,
                role = "MEMBER",
                status = "ACTIVE"
            )
        )
        val service = WorkspaceAccessService(repo)

        assertTrue(service.canAccess("ws-1", UserContext("user-1", null)))
    }

    @Test
    fun deniesInvitedUserMembership() {
        val repo = FakeMembersRepository(
            memberByUserId = WorkspaceMember(
                workspaceId = "ws-1",
                memberKey = "USER#user-1",
                userId = "user-1",
                emailLower = null,
                role = "MEMBER",
                status = "INVITED"
            )
        )
        val service = WorkspaceAccessService(repo)

        assertFalse(service.canAccess("ws-1", UserContext("user-1", null)))
    }

    @Test
    fun fallsBackToEmailLookup() {
        val repo = FakeMembersRepository(
            memberByEmail = WorkspaceMember(
                workspaceId = "ws-1",
                memberKey = "EMAIL#user@example.com",
                userId = null,
                emailLower = "user@example.com",
                role = "MEMBER",
                status = "ACCEPTED"
            )
        )
        val service = WorkspaceAccessService(repo)

        assertTrue(service.canAccess("ws-1", UserContext("user-2", "User@Example.com")))
    }

    @Test
    fun deniesWhenNoMembershipFound() {
        val service = WorkspaceAccessService(FakeMembersRepository())

        assertFalse(service.canAccess("ws-1", UserContext("user-1", "user@example.com")))
    }

    private class FakeMembersRepository(
        private val memberByUserId: WorkspaceMember? = null,
        private val memberByEmail: WorkspaceMember? = null
    ) : WorkspaceMembersRepositoryPort {
        override fun getByUserId(workspaceId: String, userId: String): WorkspaceMember? = memberByUserId

        override fun getByEmail(workspaceId: String, emailLower: String): WorkspaceMember? = memberByEmail
    }
}
