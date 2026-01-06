package autonomo.local

import autonomo.config.AppConfig

fun main() {
    println("AutonomoControlApi local runner")
    println("ENV=${AppConfig.env}")
    println("DDB_TABLE_PREFIX=${AppConfig.dynamoTablePrefix}")
    println("USERS_TABLE=${AppConfig.usersTable}")
    println("WORKSPACES_TABLE=${AppConfig.workspacesTable}")
    println("WORKSPACE_SETTINGS_TABLE=${AppConfig.workspaceSettingsTable}")
    println("WORKSPACE_MEMBERS_TABLE=${AppConfig.workspaceMembersTable}")
    println("WORKSPACE_RECORDS_TABLE=${AppConfig.workspaceRecordsTable}")
    println("DYNAMODB_ENDPOINT=${AppConfig.dynamoEndpoint}")
}
