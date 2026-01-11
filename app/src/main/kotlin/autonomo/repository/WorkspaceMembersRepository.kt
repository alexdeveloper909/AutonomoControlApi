package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.model.WorkspaceMember
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

interface WorkspaceMembersRepositoryPort {
    fun getByUserId(workspaceId: String, userId: String): WorkspaceMember?
    fun getByEmail(workspaceId: String, emailLower: String): WorkspaceMember?
}

interface WorkspaceMembershipsRepositoryPort {
    fun listByUserId(userId: String): List<WorkspaceMember>
    fun listByEmail(emailLower: String): List<WorkspaceMember>
    fun putMember(workspaceId: String, memberKey: String, userId: String?, emailLower: String?, role: String?, status: String?)
}

class WorkspaceMembersRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.workspaceMembersTable
) : WorkspaceMembersRepositoryPort, WorkspaceMembershipsRepositoryPort {
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

    override fun listByUserId(userId: String): List<WorkspaceMember> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .indexName(GSI_BY_USER_ID)
            .keyConditionExpression("user_id = :u")
            .expressionAttributeValues(
                mapOf(
                    ":u" to AttributeValue.builder().s(userId).build()
                )
            )
            .build()
        val response = client.query(request)
        return response.items().map { fromItem(it) }
    }

    override fun listByEmail(emailLower: String): List<WorkspaceMember> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .indexName(GSI_BY_EMAIL)
            .keyConditionExpression("email_lower = :e")
            .expressionAttributeValues(
                mapOf(
                    ":e" to AttributeValue.builder().s(emailLower.lowercase()).build()
                )
            )
            .build()
        val response = client.query(request)
        return response.items().map { fromItem(it) }
    }

    override fun putMember(
        workspaceId: String,
        memberKey: String,
        userId: String?,
        emailLower: String?,
        role: String?,
        status: String?
    ) {
        val item = buildMap<String, AttributeValue> {
            put("workspace_id", AttributeValue.builder().s(workspaceId).build())
            put("member_key", AttributeValue.builder().s(memberKey).build())
            userId?.let { put("user_id", AttributeValue.builder().s(it).build()) }
            emailLower?.let { put("email_lower", AttributeValue.builder().s(it.lowercase()).build()) }
            role?.let { put("role", AttributeValue.builder().s(it).build()) }
            status?.let { put("status", AttributeValue.builder().s(it).build()) }
        }
        client.putItem(
            PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build()
        )
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

    private companion object {
        const val GSI_BY_USER_ID = "by_user_id"
        const val GSI_BY_EMAIL = "by_email"
    }
}
