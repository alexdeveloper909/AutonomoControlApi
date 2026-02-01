package autonomo.model

import autonomo.domain.Settings

data class WorkspaceSummary(
    val workspaceId: String,
    val name: String,
    val role: String?,
    val status: String?,
    val accessMode: String,
    val sharedByMe: Boolean = false,
    val sharedWithMe: Boolean = false
)

data class WorkspacesListResponse(
    val items: List<WorkspaceSummary>
)

data class WorkspaceCreateRequest(
    val name: String,
    val settings: Settings
)

data class WorkspaceCreateResponse(
    val workspace: WorkspaceSummary,
    val settings: Settings
)

data class WorkspaceShareRequest(
    val email: String
)

data class WorkspaceShareResponse(
    val workspaceId: String,
    val emailLower: String,
    val role: String,
    val status: String
)
