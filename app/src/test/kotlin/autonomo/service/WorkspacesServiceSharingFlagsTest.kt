package autonomo.service

import autonomo.model.UserContext
import autonomo.model.WorkspaceMember
import autonomo.repository.WorkspaceMembershipsRepositoryPort
import autonomo.repository.WorkspacesRepositoryPort
import autonomo.repository.WorkspaceItem
import autonomo.repository.WorkspaceSettingsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspacesServiceSharingFlagsTest {
    @Test
    fun ownerWorkspaceIsMarkedSharedWhenHasReaders() {
        val wsRepo = FakeWorkspacesRepo(
            mapOf(
                "ws-1" to WorkspaceItem("ws-1", "Alpha", ownerUserId = "owner-1", createdAt = null, updatedAt = null)
            )
        )
        val memberships = FakeMembershipsRepo(
            byUserId = listOf(
                WorkspaceMember(
                    workspaceId = "ws-1",
                    memberKey = "USER#owner-1",
                    userId = "owner-1",
                    emailLower = "owner@example.com",
                    role = "OWNER",
                    status = "OWNER"
                )
            ),
            byWorkspaceId = mapOf(
                "ws-1" to listOf(
                    WorkspaceMember(
                        workspaceId = "ws-1",
                        memberKey = "EMAIL#reader@example.com",
                        userId = null,
                        emailLower = "reader@example.com",
                        role = "READER",
                        status = "ACTIVE"
                    )
                )
            )
        )

        val service = WorkspacesService(
            workspaces = wsRepo,
            memberships = memberships,
            settings = FakeSettingsRepo()
        )

        val res = service.listWorkspaces(UserContext("owner-1", "owner@example.com"))
        assertEquals(1, res.items.size)
        val item = res.items.first()
        assertEquals("READ_WRITE", item.accessMode)
        assertTrue(item.sharedByMe)
        assertEquals(false, item.sharedWithMe)
    }

    @Test
    fun readerWorkspaceIsReadOnlyAndMarkedSharedWithMe() {
        val wsRepo = FakeWorkspacesRepo(
            mapOf(
                "ws-1" to WorkspaceItem("ws-1", "Alpha", ownerUserId = "owner-1", createdAt = null, updatedAt = null)
            )
        )
        val memberships = FakeMembershipsRepo(
            byEmail = listOf(
                WorkspaceMember(
                    workspaceId = "ws-1",
                    memberKey = "EMAIL#reader@example.com",
                    userId = null,
                    emailLower = "reader@example.com",
                    role = "READER",
                    status = "ACTIVE"
                )
            )
        )

        val service = WorkspacesService(
            workspaces = wsRepo,
            memberships = memberships,
            settings = FakeSettingsRepo()
        )

        val res = service.listWorkspaces(UserContext("reader-1", "reader@example.com"))
        assertEquals(1, res.items.size)
        val item = res.items.first()
        assertEquals("READ_ONLY", item.accessMode)
        assertEquals(false, item.sharedByMe)
        assertTrue(item.sharedWithMe)
    }

    private class FakeWorkspacesRepo(private val byId: Map<String, WorkspaceItem>) : WorkspacesRepositoryPort {
        override fun put(workspaceId: String, name: String, ownerUserId: String) = Unit

        override fun get(workspaceId: String): WorkspaceItem? = byId[workspaceId]

        override fun delete(workspaceId: String) = Unit

        override fun batchGet(workspaceIds: List<String>): List<WorkspaceItem> =
            workspaceIds.mapNotNull { byId[it] }

        override fun listByOwnerUserId(ownerUserId: String): List<WorkspaceItem> = emptyList()
    }

    private class FakeMembershipsRepo(
        private val byUserId: List<WorkspaceMember> = emptyList(),
        private val byEmail: List<WorkspaceMember> = emptyList(),
        private val byWorkspaceId: Map<String, List<WorkspaceMember>> = emptyMap()
    ) : WorkspaceMembershipsRepositoryPort {
        override fun listByUserId(userId: String): List<WorkspaceMember> = byUserId

        override fun listByEmail(emailLower: String): List<WorkspaceMember> = byEmail

        override fun listByWorkspaceId(workspaceId: String): List<WorkspaceMember> = byWorkspaceId[workspaceId].orEmpty()

        override fun putMember(
            workspaceId: String,
            memberKey: String,
            userId: String?,
            emailLower: String?,
            role: String?,
            status: String?
        ) = Unit

        override fun deleteMember(workspaceId: String, memberKey: String) = Unit

        override fun deleteByWorkspaceId(workspaceId: String) = Unit
    }

    private class FakeSettingsRepo : WorkspaceSettingsRepositoryPort {
        override fun getSettings(workspaceId: String) = null

        override fun putSettings(workspaceId: String, settings: autonomo.domain.Settings, updatedBy: String) = Unit

        override fun deleteSettings(workspaceId: String) = Unit
    }
}
