package autonomo.local

import autonomo.config.AppConfig

fun main() {
    println("AutonomoControlApi local runner")
    println("ENV=${AppConfig.env}")
    println("WORKSPACE_RECORDS_TABLE=${AppConfig.workspaceRecordsTable}")
    println("DYNAMODB_ENDPOINT=${AppConfig.dynamoEndpoint}")
}
