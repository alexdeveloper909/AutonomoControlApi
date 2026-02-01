package autonomo.config

object AppConfig {
    val env: String = getenvNonBlank("ENV") ?: "local"
    private val envLower: String = env.lowercase()

    val region: String = getenvNonBlank("AWS_REGION")
        ?: getenvNonBlank("AWS_DEFAULT_REGION")
        ?: "eu-west-1"

    private val inferredTablePrefix: String? = when (envLower) {
        "dev", "prod" -> "autonomo-control-$envLower"
        else -> null
    }

    val dynamoTablePrefix: String? = getenvNonBlank("DDB_TABLE_PREFIX")
        ?: getenvNonBlank("DYNAMODB_TABLE_PREFIX")
        ?: getenvNonBlank("AUTONOMO_TABLE_PREFIX")
        ?: inferredTablePrefix

    val usersTable: String = resolveTableName("USERS_TABLE", "users", "users")
    val workspaceMembersTable: String = resolveTableName(
        "WORKSPACE_MEMBERS_TABLE",
        "workspace_members",
        "workspace_members"
    )
    val workspaceRecordsTable: String = resolveTableName(
        "WORKSPACE_RECORDS_TABLE",
        "workspace_records",
        "workspace_records"
    )
    val workspaceSettingsTable: String = resolveTableName(
        "WORKSPACE_SETTINGS_TABLE",
        "workspace_settings",
        "workspace_settings"
    )
    val workspacesTable: String = resolveTableName("WORKSPACES_TABLE", "workspaces", "workspaces")

    val dynamoEndpoint: String = getenvNonBlank("DYNAMODB_ENDPOINT") ?: "http://localhost:8000"

    val isLocal: Boolean = envLower == "local"

    val envelopeKmsKeyArn: String? =
        getenvNonBlank("ENVELOPE_KMS_KEY_ARN")
            ?: getenvNonBlank("ENVELOPE_KMS_KEY_ID")

    val sensitiveJsonEncryptionEnabled: Boolean =
        getenvNonBlank("SENSITIVE_JSON_ENCRYPTION_ENABLED")?.let(::parseBoolean) ?: (envelopeKmsKeyArn != null)

    private fun resolveTableName(envVar: String, localDefault: String, suffix: String): String {
        val explicit = getenvNonBlank(envVar)
        if (explicit != null) return explicit

        val prefix = dynamoTablePrefix?.trim()?.trimEnd('-')
        if (prefix.isNullOrBlank()) return localDefault

        return "$prefix-$suffix"
    }

    private fun getenvNonBlank(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseBoolean(value: String): Boolean {
        return when (value.trim().lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> throw IllegalArgumentException("invalid boolean value: '$value'")
        }
    }
}
