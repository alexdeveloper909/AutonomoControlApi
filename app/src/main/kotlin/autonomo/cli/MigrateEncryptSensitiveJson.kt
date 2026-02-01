package autonomo.cli

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.security.SensitiveJsonCryptoProvider
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest

private enum class TargetTable {
    WORKSPACE_RECORDS,
    WORKSPACE_SETTINGS
}

private data class CliArgs(
    val tables: Set<TargetTable>,
    val apply: Boolean,
    val pageSize: Int,
    val maxUpdates: Int?
)

fun main(args: Array<String>) {
    val parsed = parseArgs(args)

    require(AppConfig.sensitiveJsonEncryptionEnabled) {
        "Migration requires SENSITIVE_JSON_ENCRYPTION_ENABLED=true and ENVELOPE_KMS_KEY_ARN to be set."
    }

    val crypto = SensitiveJsonCryptoProvider.instance
    val client = DynamoConfig.client()

    var totalScanned = 0
    var totalUpdated = 0

    fun canContinue(): Boolean = parsed.maxUpdates == null || totalUpdated < parsed.maxUpdates

    fun migrateWorkspaceRecords() {
        val tableName = AppConfig.workspaceRecordsTable
        var lastKey: Map<String, AttributeValue>? = null

        while (canContinue()) {
            val response = client.scan(
                ScanRequest.builder()
                    .tableName(tableName)
                    .projectionExpression("workspace_id, record_key, payload_json")
                    .limit(parsed.pageSize)
                    .exclusiveStartKey(lastKey)
                    .build()
            )

            val items = response.items()
            totalScanned += items.size

            for (item in items) {
                if (!canContinue()) break

                val workspaceId = item["workspace_id"]?.s() ?: continue
                val recordKey = item["record_key"]?.s() ?: continue
                val stored = item["payload_json"]?.s() ?: continue
                if (stored.isEmpty() || crypto.isEncrypted(stored)) continue

                val encrypted = crypto.encrypt(
                    context = "workspace_records",
                    pk = workspaceId,
                    sk = recordKey,
                    attributeName = "payload_json",
                    plaintextJson = stored
                )

                if (!parsed.apply) {
                    totalUpdated += 1
                    continue
                }

                try {
                    client.updateItem(
                        UpdateItemRequest.builder()
                            .tableName(tableName)
                            .key(
                                mapOf(
                                    "workspace_id" to AttributeValue.builder().s(workspaceId).build(),
                                    "record_key" to AttributeValue.builder().s(recordKey).build()
                                )
                            )
                            .updateExpression("SET payload_json = :v")
                            .conditionExpression(
                                "attribute_exists(payload_json) AND NOT begins_with(payload_json, :p)"
                            )
                            .expressionAttributeValues(
                                mapOf(
                                    ":v" to AttributeValue.builder().s(encrypted).build(),
                                    ":p" to AttributeValue.builder()
                                        .s("ENC1|")
                                        .build()
                                )
                            )
                            .build()
                    )
                    totalUpdated += 1
                } catch (ex: ConditionalCheckFailedException) {
                    // already migrated concurrently
                }
            }

            if (response.lastEvaluatedKey().isEmpty()) break
            lastKey = response.lastEvaluatedKey()
        }
    }

    fun migrateWorkspaceSettings() {
        val tableName = AppConfig.workspaceSettingsTable
        var lastKey: Map<String, AttributeValue>? = null

        while (canContinue()) {
            val response = client.scan(
                ScanRequest.builder()
                    .tableName(tableName)
                    .projectionExpression("workspace_id, settings_json")
                    .limit(parsed.pageSize)
                    .exclusiveStartKey(lastKey)
                    .build()
            )

            val items = response.items()
            totalScanned += items.size

            for (item in items) {
                if (!canContinue()) break

                val workspaceId = item["workspace_id"]?.s() ?: continue
                val stored = item["settings_json"]?.s() ?: continue
                if (stored.isEmpty() || crypto.isEncrypted(stored)) continue

                val encrypted = crypto.encrypt(
                    context = "workspace_settings",
                    pk = workspaceId,
                    sk = null,
                    attributeName = "settings_json",
                    plaintextJson = stored
                )

                if (!parsed.apply) {
                    totalUpdated += 1
                    continue
                }

                try {
                    client.updateItem(
                        UpdateItemRequest.builder()
                            .tableName(tableName)
                            .key(
                                mapOf(
                                    "workspace_id" to AttributeValue.builder().s(workspaceId).build()
                                )
                            )
                            .updateExpression("SET settings_json = :v")
                            .conditionExpression(
                                "attribute_exists(settings_json) AND NOT begins_with(settings_json, :p)"
                            )
                            .expressionAttributeValues(
                                mapOf(
                                    ":v" to AttributeValue.builder().s(encrypted).build(),
                                    ":p" to AttributeValue.builder()
                                        .s("ENC1|")
                                        .build()
                                )
                            )
                            .build()
                    )
                    totalUpdated += 1
                } catch (ex: ConditionalCheckFailedException) {
                    // already migrated concurrently
                }
            }

            if (response.lastEvaluatedKey().isEmpty()) break
            lastKey = response.lastEvaluatedKey()
        }
    }

    if (parsed.tables.contains(TargetTable.WORKSPACE_RECORDS)) migrateWorkspaceRecords()
    if (parsed.tables.contains(TargetTable.WORKSPACE_SETTINGS)) migrateWorkspaceSettings()

    val mode = if (parsed.apply) "APPLIED" else "DRY_RUN"
    println("Migration complete ($mode). scanned=$totalScanned updated=$totalUpdated")
}

private fun parseArgs(raw: Array<String>): CliArgs {
    var apply = false
    var pageSize = 100
    var maxUpdates: Int? = null
    var tables: Set<TargetTable> = setOf(TargetTable.WORKSPACE_RECORDS, TargetTable.WORKSPACE_SETTINGS)

    var idx = 0
    while (idx < raw.size) {
        when (val arg = raw[idx]) {
            "--apply" -> apply = true
            "--dry-run" -> apply = false
            "--page-size" -> {
                idx += 1
                pageSize = raw.getOrNull(idx)?.toIntOrNull()
                    ?: throw IllegalArgumentException("--page-size requires an integer")
            }
            "--max-updates" -> {
                idx += 1
                maxUpdates = raw.getOrNull(idx)?.toIntOrNull()
                    ?: throw IllegalArgumentException("--max-updates requires an integer")
            }
            "--tables" -> {
                idx += 1
                val value = raw.getOrNull(idx) ?: throw IllegalArgumentException("--tables requires a value")
                tables = when (value.trim().lowercase()) {
                    "all" -> setOf(TargetTable.WORKSPACE_RECORDS, TargetTable.WORKSPACE_SETTINGS)
                    "workspace_records" -> setOf(TargetTable.WORKSPACE_RECORDS)
                    "workspace_settings" -> setOf(TargetTable.WORKSPACE_SETTINGS)
                    else -> throw IllegalArgumentException("unknown --tables value: '$value'")
                }
            }
            "--help", "-h" -> {
                println(
                    """
                    Usage:
                      migrateEncryptSensitiveJson [--dry-run|--apply] [--tables all|workspace_records|workspace_settings] [--page-size N] [--max-updates N]

                    Notes:
                      - Requires ENVELOPE_KMS_KEY_ARN and SENSITIVE_JSON_ENCRYPTION_ENABLED=true.
                      - Uses table names from AppConfig (ENV + DDB_TABLE_PREFIX / per-table overrides).
                    """.trimIndent()
                )
                kotlin.system.exitProcess(0)
            }
            else -> throw IllegalArgumentException("unknown arg: $arg")
        }
        idx += 1
    }

    require(pageSize in 1..1000) { "--page-size must be between 1 and 1000" }
    require(maxUpdates == null || maxUpdates!! > 0) { "--max-updates must be > 0" }

    return CliArgs(
        tables = tables,
        apply = apply,
        pageSize = pageSize,
        maxUpdates = maxUpdates
    )
}

