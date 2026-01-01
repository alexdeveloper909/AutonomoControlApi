package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.model.WorkspaceMember
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest

interface WorkspaceMembersRepositoryPort {
    fun getByUserId(workspaceId: String, userId: String): WorkspaceMember?
    fun getByEmail(workspaceId: String, emailLower: String): WorkspaceMember?
}

class WorkspaceMembersRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.workspaceMembersTable
) : WorkspaceMembersRepositoryPort {
    override fun getByUserId(workspaceId: String, userId: String): WorkspaceMember? {
        val memberKey = "USER#$userId"
        return getByMemberKey(workspaceId, memberKey)
    }

    override fun getByEmail(workspaceId: String, emailLower: String): WorkspaceMember? {
        val memberKey = "EMAIL#${emailLower.lowercase()}"
        return getByMemberKey(workspaceId, memberKey)
    }

    private fun getByMemberKey(workspaceId: String, memberKey: String): WorkspaceMember? {
        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build(),
                    "member_key" to AttributeValue.builder().s(memberKey).build()
                )
            )
            .build()
        val response = client.getItem(request)
        if (!response.hasItem()) return null
        return fromItem(response.item())
    }

    private fun fromItem(item: Map<String, AttributeValue>): WorkspaceMember {
        return WorkspaceMember(
            workspaceId = item.getValue("workspace_id").s(),
            memberKey = item.getValue("member_key").s(),
            userId = item["user_id"]?.s(),
            emailLower = item["email_lower"]?.s(),
            role = item["role"]?.s(),
            status = item["status"]?.s()
        )
    }
}
