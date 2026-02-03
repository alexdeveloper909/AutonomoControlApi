package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import java.time.Instant

data class WorkspaceItem(
    val workspaceId: String,
    val name: String,
    val ownerUserId: String,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val deletedBy: String?,
    val ttlEpoch: Long?
)

interface WorkspacesRepositoryPort {
    fun put(workspaceId: String, name: String, ownerUserId: String)
    fun get(workspaceId: String): WorkspaceItem?
    fun delete(workspaceId: String)
    fun markDeleted(workspaceId: String, deletedAt: String, deletedBy: String, ttlEpoch: Long)
    fun restore(workspaceId: String)
    fun batchGet(workspaceIds: List<String>): List<WorkspaceItem>
    fun listByOwnerUserId(ownerUserId: String): List<WorkspaceItem>
}

class WorkspacesRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.workspacesTable
) : WorkspacesRepositoryPort {
    override fun put(workspaceId: String, name: String, ownerUserId: String) {
        val now = Instant.now().toString()
        val item = mapOf(
            "workspace_id" to AttributeValue.builder().s(workspaceId).build(),
            "name" to AttributeValue.builder().s(name).build(),
            "owner_user_id" to AttributeValue.builder().s(ownerUserId).build(),
            "created_at" to AttributeValue.builder().s(now).build(),
            "updated_at" to AttributeValue.builder().s(now).build()
        )
        client.putItem(
            PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(workspace_id)")
                .build()
        )
    }

    override fun get(workspaceId: String): WorkspaceItem? {
        val response = client.getItem { b ->
            b.tableName(tableName)
            b.key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                )
            )
        }
        if (!response.hasItem()) return null
        return fromItem(response.item())
    }

    override fun delete(workspaceId: String) {
        client.deleteItem { b ->
            b.tableName(tableName)
            b.key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                )
            )
        }
    }

    override fun markDeleted(workspaceId: String, deletedAt: String, deletedBy: String, ttlEpoch: Long) {
        val now = Instant.now().toString()
        client.updateItem { b ->
            b.tableName(tableName)
            b.key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                )
            )
            b.updateExpression("SET deleted_at = :d, deleted_by = :db, ttl_epoch = :ttl, updated_at = :u")
            b.expressionAttributeValues(
                mapOf(
                    ":d" to AttributeValue.builder().s(deletedAt).build(),
                    ":db" to AttributeValue.builder().s(deletedBy).build(),
                    ":ttl" to AttributeValue.builder().n(ttlEpoch.toString()).build(),
                    ":u" to AttributeValue.builder().s(now).build()
                )
            )
        }
    }

    override fun restore(workspaceId: String) {
        val now = Instant.now().toString()
        client.updateItem { b ->
            b.tableName(tableName)
            b.key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                )
            )
            b.updateExpression("REMOVE deleted_at, deleted_by, ttl_epoch SET updated_at = :u")
            b.expressionAttributeValues(
                mapOf(
                    ":u" to AttributeValue.builder().s(now).build()
                )
            )
        }
    }

    override fun batchGet(workspaceIds: List<String>): List<WorkspaceItem> {
        if (workspaceIds.isEmpty()) return emptyList()

        val keys = workspaceIds.distinct().take(100).map { id ->
            mapOf(
                "workspace_id" to AttributeValue.builder().s(id).build()
            )
        }
        val response = client.batchGetItem(
            BatchGetItemRequest.builder()
                .requestItems(
                    mapOf(
                        tableName to KeysAndAttributes.builder().keys(keys).build()
                    )
                )
                .build()
        )
        return response.responses()[tableName].orEmpty().map { fromItem(it) }
    }

    override fun listByOwnerUserId(ownerUserId: String): List<WorkspaceItem> {
        val response = client.query(
            QueryRequest.builder()
                .tableName(tableName)
                .indexName(GSI_BY_OWNER_USER_ID)
                .keyConditionExpression("owner_user_id = :o")
                .expressionAttributeValues(
                    mapOf(
                        ":o" to AttributeValue.builder().s(ownerUserId).build()
                    )
                )
                .build()
        )
        return response.items().map { fromItem(it) }
    }

    private fun fromItem(item: Map<String, AttributeValue>): WorkspaceItem {
        return WorkspaceItem(
            workspaceId = item.getValue("workspace_id").s(),
            name = item.getValue("name").s(),
            ownerUserId = item.getValue("owner_user_id").s(),
            createdAt = item["created_at"]?.s(),
            updatedAt = item["updated_at"]?.s(),
            deletedAt = item["deleted_at"]?.s(),
            deletedBy = item["deleted_by"]?.s(),
            ttlEpoch = item["ttl_epoch"]?.n()?.toLongOrNull()
        )
    }

    private companion object {
        const val GSI_BY_OWNER_USER_ID = "by_owner_user_id"
    }
}
