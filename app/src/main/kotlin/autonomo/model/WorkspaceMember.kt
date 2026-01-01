package autonomo.model

data class WorkspaceMember(
    val workspaceId: String,
    val memberKey: String,
    val userId: String?,
    val emailLower: String?,
    val role: String?,
    val status: String?
)
