package autonomo.service

import autonomo.model.UserContext
import autonomo.repository.UserItem
import autonomo.repository.UsersRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UsersServiceTest {
    private class FakeUsersRepo : UsersRepositoryPort {
        var lastPreferredLanguage: String? = null

        override fun ensureUser(userId: String, email: String?, givenName: String?, familyName: String?): UserItem {
            return UserItem(userId = userId, email = email, givenName = givenName, familyName = familyName, preferredLanguage = lastPreferredLanguage)
        }

        override fun setPreferredLanguage(
            userId: String,
            preferredLanguage: String,
            email: String?,
            givenName: String?,
            familyName: String?
        ): UserItem {
            lastPreferredLanguage = preferredLanguage
            return UserItem(userId = userId, email = email, givenName = givenName, familyName = familyName, preferredLanguage = preferredLanguage)
        }
    }

    @Test
    fun setPreferredLanguage_acceptsSupportedLanguages() {
        val repo = FakeUsersRepo()
        val service = UsersService(users = repo)
        val user = UserContext(userId = "u-1", email = "a@b.com")

        val res = service.setPreferredLanguage(user, "UK")
        assertEquals("uk", res.preferredLanguage)
        assertEquals("uk", repo.lastPreferredLanguage)
    }

    @Test
    fun setPreferredLanguage_rejectsUnsupportedLanguage() {
        val repo = FakeUsersRepo()
        val service = UsersService(users = repo)
        val user = UserContext(userId = "u-1", email = null)

        assertThrows(IllegalArgumentException::class.java) {
            service.setPreferredLanguage(user, "xx")
        }
    }
}

