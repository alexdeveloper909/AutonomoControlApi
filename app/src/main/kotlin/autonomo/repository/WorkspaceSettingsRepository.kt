package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.config.JsonSupport
import autonomo.domain.Settings
import autonomo.security.SensitiveJsonCrypto
import autonomo.security.SensitiveJsonCryptoProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import java.time.Instant

interface WorkspaceSettingsRepositoryPort {
    fun getSettings(workspaceId: String): Settings?
    fun putSettings(workspaceId: String, settings: Settings, updatedBy: String)
    fun deleteSettings(workspaceId: String)
    fun setTtl(workspaceId: String, ttlEpoch: Long)
    fun clearTtl(workspaceId: String)
}

class WorkspaceSettingsRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.workspaceSettingsTable,
    private val sensitiveJsonCrypto: SensitiveJsonCrypto = SensitiveJsonCryptoProvider.instance
) : WorkspaceSettingsRepositoryPort {
    override fun getSettings(workspaceId: String): Settings? {
        val response = client.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .key(
                    mapOf(
                        "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                    )
                )
                .build()
        )
        if (!response.hasItem()) return null
        val stored = response.item()["settings_json"]?.s() ?: return null
        val json = sensitiveJsonCrypto.decrypt(
            context = "workspace_settings",
            pk = workspaceId,
            sk = null,
            attributeName = "settings_json",
            storedValue = stored
        )
        return runCatching { JsonSupport.mapper.readValue(json, Settings::class.java) }.getOrNull()
    }

    override fun putSettings(workspaceId: String, settings: Settings, updatedBy: String) {
        val now = Instant.now().toString()
        val plaintextJson = JsonSupport.mapper.writeValueAsString(settings)
        val encryptedJson = sensitiveJsonCrypto.encrypt(
            context = "workspace_settings",
            pk = workspaceId,
            sk = null,
            attributeName = "settings_json",
            plaintextJson = plaintextJson
        )
        val item = mapOf(
            "workspace_id" to AttributeValue.builder().s(workspaceId).build(),
            "settings_json" to AttributeValue.builder().s(encryptedJson).build(),
            "updated_at" to AttributeValue.builder().s(now).build(),
            "updated_by" to AttributeValue.builder().s(updatedBy).build()
        )
        client.putItem(
            PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build()
        )
    }

    override fun deleteSettings(workspaceId: String) {
        client.deleteItem { b ->
            b.tableName(tableName)
            b.key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                )
            )
        }
    }

    override fun setTtl(workspaceId: String, ttlEpoch: Long) {
        client.updateItem { b ->
            b.tableName(tableName)
            b.key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                )
            )
            b.updateExpression("SET ttl_epoch = :ttl")
            b.expressionAttributeValues(
                mapOf(
                    ":ttl" to AttributeValue.builder().n(ttlEpoch.toString()).build()
                )
            )
        }
    }

    override fun clearTtl(workspaceId: String) {
        client.updateItem { b ->
            b.tableName(tableName)
            b.key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                )
            )
            b.updateExpression("REMOVE ttl_epoch")
        }
    }
}
