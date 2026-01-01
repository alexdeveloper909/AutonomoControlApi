package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.model.RecordItem
import autonomo.model.RecordType
import java.time.Instant
import java.time.LocalDate
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

class WorkspaceRecordsRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.workspaceRecordsTable
) {
    fun create(record: RecordItem) {
        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(toItem(record))
            .conditionExpression("attribute_not_exists(record_key)")
            .build()
        client.putItem(request)
    }

    fun update(record: RecordItem) {
        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(toItem(record))
            .conditionExpression("attribute_exists(record_key)")
            .build()
        client.putItem(request)
    }

    fun get(workspaceId: String, recordKey: String): RecordItem? {
        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build(),
                    "record_key" to AttributeValue.builder().s(recordKey).build()
                )
            )
            .build()
        val response = client.getItem(request)
        if (!response.hasItem()) return null
        return fromItem(response.item())
    }

    fun delete(workspaceId: String, recordKey: String) {
        val request = DeleteItemRequest.builder()
            .tableName(tableName)
            .key(
                mapOf(
                    "workspace_id" to AttributeValue.builder().s(workspaceId).build(),
                    "record_key" to AttributeValue.builder().s(recordKey).build()
                )
            )
            .build()
        client.deleteItem(request)
    }

    fun queryByMonth(
        workspaceMonth: String,
        recordType: RecordType?
    ): List<RecordItem> {
        val (keyCondition, values) = if (recordType == null) {
            "workspace_month = :pk" to mapOf(
                ":pk" to AttributeValue.builder().s(workspaceMonth).build()
            )
        } else {
            "workspace_month = :pk AND begins_with(record_key, :prefix)" to mapOf(
                ":pk" to AttributeValue.builder().s(workspaceMonth).build(),
                ":prefix" to AttributeValue.builder().s("${recordType.name}#").build()
            )
        }

        val request = QueryRequest.builder()
            .tableName(tableName)
            .indexName("by_month")
            .keyConditionExpression(keyCondition)
            .expressionAttributeValues(values)
            .build()

        return client.query(request).items().map(::fromItem)
    }

    fun queryByQuarter(
        workspaceQuarter: String,
        recordType: RecordType?
    ): List<RecordItem> {
        val (keyCondition, values) = if (recordType == null) {
            "workspace_quarter = :pk" to mapOf(
                ":pk" to AttributeValue.builder().s(workspaceQuarter).build()
            )
        } else {
            "workspace_quarter = :pk AND begins_with(record_key, :prefix)" to mapOf(
                ":pk" to AttributeValue.builder().s(workspaceQuarter).build(),
                ":prefix" to AttributeValue.builder().s("${recordType.name}#").build()
            )
        }

        val request = QueryRequest.builder()
            .tableName(tableName)
            .indexName("by_quarter")
            .keyConditionExpression(keyCondition)
            .expressionAttributeValues(values)
            .build()

        return client.query(request).items().map(::fromItem)
    }

    private fun toItem(record: RecordItem): Map<String, AttributeValue> {
        return mapOf(
            "workspace_id" to AttributeValue.builder().s(record.workspaceId).build(),
            "record_key" to AttributeValue.builder().s(record.recordKey).build(),
            "record_id" to AttributeValue.builder().s(record.recordId).build(),
            "record_type" to AttributeValue.builder().s(record.recordType.name).build(),
            "event_date" to AttributeValue.builder().s(record.eventDate.toString()).build(),
            "payload_json" to AttributeValue.builder().s(record.payloadJson).build(),
            "workspace_month" to AttributeValue.builder().s(record.workspaceMonth).build(),
            "workspace_quarter" to AttributeValue.builder().s(record.workspaceQuarter).build(),
            "created_at" to AttributeValue.builder().s(record.createdAt.toString()).build(),
            "updated_at" to AttributeValue.builder().s(record.updatedAt.toString()).build(),
            "created_by" to AttributeValue.builder().s(record.createdBy).build(),
            "updated_by" to AttributeValue.builder().s(record.updatedBy).build()
        )
    }

    private fun fromItem(item: Map<String, AttributeValue>): RecordItem {
        return RecordItem(
            workspaceId = item.getValue("workspace_id").s(),
            recordKey = item.getValue("record_key").s(),
            recordId = item.getValue("record_id").s(),
            recordType = RecordType.valueOf(item.getValue("record_type").s()),
            eventDate = LocalDate.parse(item.getValue("event_date").s()),
            payloadJson = item.getValue("payload_json").s(),
            workspaceMonth = item.getValue("workspace_month").s(),
            workspaceQuarter = item.getValue("workspace_quarter").s(),
            createdAt = Instant.parse(item.getValue("created_at").s()),
            updatedAt = Instant.parse(item.getValue("updated_at").s()),
            createdBy = item.getValue("created_by").s(),
            updatedBy = item.getValue("updated_by").s()
        )
    }

    fun isConditionalFailure(ex: Exception): Boolean {
        return ex is ConditionalCheckFailedException
    }
}
