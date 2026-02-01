package autonomo.repository

import autonomo.config.AppConfig
import autonomo.config.DynamoConfig
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.time.Instant

data class UserItem(
    val userId: String,
    val email: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val preferredLanguage: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

interface UsersRepositoryPort {
    fun ensureUser(userId: String, email: String?, givenName: String?, familyName: String?): UserItem
    fun setPreferredLanguage(userId: String, preferredLanguage: String, email: String?, givenName: String?, familyName: String?): UserItem
}

class UsersRepository(
    private val client: DynamoDbClient = DynamoConfig.client(),
    private val tableName: String = AppConfig.usersTable
) : UsersRepositoryPort {
    override fun ensureUser(userId: String, email: String?, givenName: String?, familyName: String?): UserItem {
        return updateUser(
            userId = userId,
            email = email,
            givenName = givenName,
            familyName = familyName,
            preferredLanguage = null
        )
    }

    override fun setPreferredLanguage(
        userId: String,
        preferredLanguage: String,
        email: String?,
        givenName: String?,
        familyName: String?
    ): UserItem {
        return updateUser(
            userId = userId,
            email = email,
            givenName = givenName,
            familyName = familyName,
            preferredLanguage = preferredLanguage
        )
    }

    private fun updateUser(
        userId: String,
        email: String?,
        givenName: String?,
        familyName: String?,
        preferredLanguage: String?
    ): UserItem {
        val now = Instant.now().toString()

        val updateParts = mutableListOf(
            "created_at = if_not_exists(created_at, :now)",
            "updated_at = :now"
        )
        val values = mutableMapOf(
            ":now" to AttributeValue.builder().s(now).build()
        )

        if (!email.isNullOrBlank()) {
            updateParts.add("email = :email")
            updateParts.add("email_lower = :email_lower")
            values[":email"] = AttributeValue.builder().s(email).build()
            values[":email_lower"] = AttributeValue.builder().s(email.lowercase()).build()
        }

        if (!givenName.isNullOrBlank()) {
            updateParts.add("given_name = :given_name")
            values[":given_name"] = AttributeValue.builder().s(givenName).build()
        }

        if (!familyName.isNullOrBlank()) {
            updateParts.add("family_name = :family_name")
            values[":family_name"] = AttributeValue.builder().s(familyName).build()
        }

        if (!preferredLanguage.isNullOrBlank()) {
            updateParts.add("preferred_language = :preferred_language")
            values[":preferred_language"] = AttributeValue.builder().s(preferredLanguage).build()
        }

        val response = client.updateItem(
            UpdateItemRequest.builder()
                .tableName(tableName)
                .key(
                    mapOf(
                        "user_id" to AttributeValue.builder().s(userId).build()
                    )
                )
                .updateExpression("SET " + updateParts.joinToString(", "))
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.ALL_NEW)
                .build()
        )
        return fromItem(response.attributes())
    }

    private fun fromItem(item: Map<String, AttributeValue>): UserItem {
        return UserItem(
            userId = item.getValue("user_id").s(),
            email = item["email"]?.s(),
            givenName = item["given_name"]?.s(),
            familyName = item["family_name"]?.s(),
            preferredLanguage = item["preferred_language"]?.s(),
            createdAt = item["created_at"]?.s(),
            updatedAt = item["updated_at"]?.s()
        )
    }
}

