package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.config.JsonSupport
import autonomo.domain.Settings
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import java.time.Instant

interface WorkspaceSettingsRepositoryPort {
    fun getSettings(workspaceId: String): Settings?
    fun putSettings(workspaceId: String, settings: Settings, updatedBy: String)
}

class WorkspaceSettingsRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.workspaceSettingsTable
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
        val json = response.item()["settings_json"]?.s() ?: return null
        return runCatching { JsonSupport.mapper.readValue(json, Settings::class.java) }.getOrNull()
    }

    override fun putSettings(workspaceId: String, settings: Settings, updatedBy: String) {
        val now = Instant.now().toString()
        val item = mapOf(
            "workspace_id" to AttributeValue.builder().s(workspaceId).build(),
            "settings_json" to AttributeValue.builder().s(JsonSupport.mapper.writeValueAsString(settings)).build(),
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
}

