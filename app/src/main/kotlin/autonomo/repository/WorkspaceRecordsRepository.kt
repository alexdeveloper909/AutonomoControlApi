package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.security.SensitiveJsonCrypto
import autonomo.security.SensitiveJsonCryptoProvider
import java.time.Instant
import java.time.LocalDate
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.WriteRequest

class WorkspaceRecordsRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.workspaceRecordsTable,
    private val sensitiveJsonCrypto: SensitiveJsonCrypto = SensitiveJsonCryptoProvider.instance
) : WorkspaceRecordsRepositoryPort {
    override fun create(record: RecordItem) {
        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(toItem(record))
            .conditionExpression("attribute_not_exists(record_key)")
            .build()
        client.putItem(request)
    }

    override fun update(record: RecordItem) {
        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(toItem(record))
            .conditionExpression("attribute_exists(record_key)")
            .build()
        client.putItem(request)
    }

    override fun get(workspaceId: String, recordKey: String): RecordItem? {
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

    override fun delete(workspaceId: String, recordKey: String) {
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

    override fun deleteByWorkspaceId(workspaceId: String) {
        var startKey: Map<String, AttributeValue>? = null
        do {
            val response = client.query(
                QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("workspace_id = :pk")
                    .expressionAttributeValues(
                        mapOf(
                            ":pk" to AttributeValue.builder().s(workspaceId).build()
                        )
                    )
                    .projectionExpression("workspace_id, record_key")
                    .exclusiveStartKey(startKey)
                    .build()
            )

            val keys = response.items().mapNotNull { item ->
                val pk = item["workspace_id"] ?: return@mapNotNull null
                val sk = item["record_key"] ?: return@mapNotNull null
                mapOf(
                    "workspace_id" to pk,
                    "record_key" to sk
                )
            }
            batchDelete(keys)

            startKey = response.lastEvaluatedKey()
        } while (!startKey.isNullOrEmpty())
    }

    override fun queryByWorkspaceRecordKeyPrefix(
        workspaceId: String,
        recordKeyPrefix: String
    ): List<RecordItem> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("workspace_id = :pk AND begins_with(record_key, :prefix)")
            .expressionAttributeValues(
                mapOf(
                    ":pk" to AttributeValue.builder().s(workspaceId).build(),
                    ":prefix" to AttributeValue.builder().s(recordKeyPrefix).build()
                )
            )
            .build()

        return client.query(request).items().map(::fromItem)
    }

    override fun queryByMonth(
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

    override fun queryByQuarter(
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
        val payloadEncrypted = sensitiveJsonCrypto.encrypt(
            context = "workspace_records",
            pk = record.workspaceId,
            sk = record.recordKey,
            attributeName = "payload_json",
            plaintextJson = record.payloadJson
        )
        return mapOf(
            "workspace_id" to AttributeValue.builder().s(record.workspaceId).build(),
            "record_key" to AttributeValue.builder().s(record.recordKey).build(),
            "record_id" to AttributeValue.builder().s(record.recordId).build(),
            "record_type" to AttributeValue.builder().s(record.recordType.name).build(),
            "event_date" to AttributeValue.builder().s(record.eventDate.toString()).build(),
            "payload_json" to AttributeValue.builder().s(payloadEncrypted).build(),
            "workspace_month" to AttributeValue.builder().s(record.workspaceMonth).build(),
            "workspace_quarter" to AttributeValue.builder().s(record.workspaceQuarter).build(),
            "created_at" to AttributeValue.builder().s(record.createdAt.toString()).build(),
            "updated_at" to AttributeValue.builder().s(record.updatedAt.toString()).build(),
            "created_by" to AttributeValue.builder().s(record.createdBy).build(),
            "updated_by" to AttributeValue.builder().s(record.updatedBy).build()
        )
    }

    private fun fromItem(item: Map<String, AttributeValue>): RecordItem {
        val workspaceId = item.getValue("workspace_id").s()
        val recordKey = item.getValue("record_key").s()
        val payloadJson = sensitiveJsonCrypto.decrypt(
            context = "workspace_records",
            pk = workspaceId,
            sk = recordKey,
            attributeName = "payload_json",
            storedValue = item.getValue("payload_json").s()
        )
        return RecordItem(
            workspaceId = workspaceId,
            recordKey = recordKey,
            recordId = item.getValue("record_id").s(),
            recordType = RecordType.valueOf(item.getValue("record_type").s()),
            eventDate = LocalDate.parse(item.getValue("event_date").s()),
            payloadJson = payloadJson,
            workspaceMonth = item.getValue("workspace_month").s(),
            workspaceQuarter = item.getValue("workspace_quarter").s(),
            createdAt = Instant.parse(item.getValue("created_at").s()),
            updatedAt = Instant.parse(item.getValue("updated_at").s()),
            createdBy = item.getValue("created_by").s(),
            updatedBy = item.getValue("updated_by").s()
        )
    }

    override fun isConditionalFailure(ex: Exception): Boolean {
        return ex is ConditionalCheckFailedException
    }

    private fun batchDelete(keys: List<Map<String, AttributeValue>>) {
        if (keys.isEmpty()) return

        val chunks = keys.chunked(25)
        chunks.forEach { chunk ->
            var requestItems = mapOf(
                tableName to chunk.map { key ->
                    WriteRequest.builder()
                        .deleteRequest(DeleteRequest.builder().key(key).build())
                        .build()
                }
            )

            repeat(5) { attempt ->
                val res = client.batchWriteItem(
                    BatchWriteItemRequest.builder()
                        .requestItems(requestItems)
                        .build()
                )
                val unprocessed = res.unprocessedItems()
                val next = unprocessed[tableName].orEmpty()
                if (next.isEmpty()) return
                requestItems = mapOf(tableName to next)
                Thread.sleep(50L * (attempt + 1))
            }

            throw IllegalStateException("Failed to delete all workspace records (DynamoDB batch write left unprocessed items)")
        }
    }
}
