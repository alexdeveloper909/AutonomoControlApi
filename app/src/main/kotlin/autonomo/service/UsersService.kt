package autonomo.service

import autonomo.model.UserContext
import autonomo.model.UserMeResponse
import autonomo.repository.UsersRepository
import autonomo.repository.UsersRepositoryPort

interface UsersServicePort {
    fun getMe(user: UserContext): UserMeResponse
    fun setPreferredLanguage(user: UserContext, preferredLanguage: String): UserMeResponse
}

class UsersService(
    private val users: UsersRepositoryPort = UsersRepository()
) : UsersServicePort {
    override fun getMe(user: UserContext): UserMeResponse {
        val item = users.ensureUser(
            userId = user.userId,
            email = user.email,
            givenName = null,
            familyName = null
        )
        return UserMeResponse(
            userId = item.userId,
            email = item.email,
            givenName = item.givenName,
            familyName = item.familyName,
            preferredLanguage = item.preferredLanguage
        )
    }

    override fun setPreferredLanguage(user: UserContext, preferredLanguage: String): UserMeResponse {
        val normalized = preferredLanguage.trim().lowercase()
        require(normalized in SUPPORTED_LANGUAGES) { "preferredLanguage is invalid" }

        val item = users.setPreferredLanguage(
            userId = user.userId,
            preferredLanguage = normalized,
            email = user.email,
            givenName = null,
            familyName = null
        )
        return UserMeResponse(
            userId = item.userId,
            email = item.email,
            givenName = item.givenName,
            familyName = item.familyName,
            preferredLanguage = item.preferredLanguage
        )
    }

    private companion object {
        val SUPPORTED_LANGUAGES = setOf("en", "es", "uk", "ar", "ro")
    }
}

