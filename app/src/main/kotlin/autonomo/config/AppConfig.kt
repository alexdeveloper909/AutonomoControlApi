package autonomo.config

object AppConfig {
    val env: String = System.getenv("ENV") ?: "local"
    val region: String = System.getenv("AWS_REGION")
        ?: System.getenv("AWS_DEFAULT_REGION")
        ?: "eu-west-1"
    val workspaceRecordsTable: String = System.getenv("WORKSPACE_RECORDS_TABLE")
        ?: "workspace_records"
    val dynamoEndpoint: String = System.getenv("DYNAMODB_ENDPOINT") ?: "http://localhost:8000"

    val isLocal: Boolean = env.equals("local", ignoreCase = true)
}
