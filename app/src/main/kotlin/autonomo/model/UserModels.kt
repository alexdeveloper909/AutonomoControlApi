package autonomo.model

data class UserMeResponse(
    val userId: String,
    val email: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val preferredLanguage: String? = null
)

data class UserUpdateRequest(
    val preferredLanguage: String? = null
)

