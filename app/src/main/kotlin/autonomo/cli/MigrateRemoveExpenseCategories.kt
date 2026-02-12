package autonomo.cli

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import autonomo.config.JsonSupport
import autonomo.security.SensitiveJsonCryptoProvider
import com.fasterxml.jackson.databind.node.ObjectNode
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest

private data class RemoveExpenseCategoriesArgs(
    val apply: Boolean,
    val pageSize: Int,
    val maxUpdates: Int?
)

fun main(args: Array<String>) {
    val parsed = parseArgs(args)

    val crypto = SensitiveJsonCryptoProvider.instance
    val client = DynamoConfig.client()
    val tableName = AppConfig.workspaceSettingsTable

    var totalScanned = 0
    var totalMatched = 0
    var totalUpdated = 0
    var totalFailed = 0

    fun canContinue(): Boolean = parsed.maxUpdates == null || totalUpdated < parsed.maxUpdates

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
            if (stored.isEmpty()) continue

            val plaintext = runCatching {
                crypto.decrypt(
                    context = "workspace_settings",
                    pk = workspaceId,
                    sk = null,
                    attributeName = "settings_json",
                    storedValue = stored
                )
            }.getOrElse {
                totalFailed += 1
                continue
            }

            val node = runCatching { JsonSupport.mapper.readTree(plaintext) }.getOrElse {
                totalFailed += 1
                continue
            }

            val obj = node as? ObjectNode ?: continue
            if (!obj.has("expenseCategories")) continue

            totalMatched += 1
            obj.remove("expenseCategories")

            val updatedPlaintext = JsonSupport.mapper.writeValueAsString(obj)
            val updatedStored = crypto.encrypt(
                context = "workspace_settings",
                pk = workspaceId,
                sk = null,
                attributeName = "settings_json",
                plaintextJson = updatedPlaintext
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
                        .conditionExpression("attribute_exists(settings_json) AND settings_json = :old")
                        .expressionAttributeValues(
                            mapOf(
                                ":v" to AttributeValue.builder().s(updatedStored).build(),
                                ":old" to AttributeValue.builder().s(stored).build()
                            )
                        )
                        .build()
                )
                totalUpdated += 1
            } catch (ex: ConditionalCheckFailedException) {
                // settings changed concurrently; skip
            }
        }

        if (response.lastEvaluatedKey().isEmpty()) break
        lastKey = response.lastEvaluatedKey()
    }

    val mode = if (parsed.apply) "APPLIED" else "DRY_RUN"
    println("Migration complete ($mode). scanned=$totalScanned matched=$totalMatched updated=$totalUpdated failed=$totalFailed")
}

private fun parseArgs(raw: Array<String>): RemoveExpenseCategoriesArgs {
    var apply = false
    var pageSize = 100
    var maxUpdates: Int? = null

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
            "--help", "-h" -> {
                println(
                    """
                    Usage:
                      migrateRemoveExpenseCategories [--dry-run|--apply] [--page-size N] [--max-updates N]

                    Notes:
                      - Removes Settings.expenseCategories from workspace_settings.settings_json (encrypted or plaintext).
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

    return RemoveExpenseCategoriesArgs(
        apply = apply,
        pageSize = pageSize,
        maxUpdates = maxUpdates
    )
}
