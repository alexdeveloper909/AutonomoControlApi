package autonomo.controller

import autonomo.config.JsonSupport
import autonomo.model.UserContext
import autonomo.model.UserMeResponse
import autonomo.service.UsersServicePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsersControllerTest {
    private class FakeUsersService : UsersServicePort {
        var lastSetPreferredLanguage: String? = null

        override fun getMe(user: UserContext): UserMeResponse {
            return UserMeResponse(
                userId = user.userId,
                email = user.email,
                givenName = null,
                familyName = null,
                preferredLanguage = "en"
            )
        }

        override fun setPreferredLanguage(user: UserContext, preferredLanguage: String): UserMeResponse {
            lastSetPreferredLanguage = preferredLanguage
            return UserMeResponse(
                userId = user.userId,
                email = user.email,
                givenName = null,
                familyName = null,
                preferredLanguage = preferredLanguage
            )
        }
    }

    private fun errorMessage(resBody: String?): String? {
        if (resBody.isNullOrBlank()) return null
        val node = JsonSupport.mapper.readTree(resBody)
        return node.get("message")?.asText()
    }

    @Test
    fun getMe_unauthorizedWhenNoUser() {
        val controller = UsersController(service = FakeUsersService())
        val res = controller.getMe(null)
        assertEquals(401, res.statusCode)
        assertNotNull(errorMessage(res.body))
    }

    @Test
    fun getMe_ok() {
        val controller = UsersController(service = FakeUsersService())
        val res = controller.getMe(UserContext(userId = "u-1", email = "a@b.com"))
        assertEquals(200, res.statusCode)
        val node = JsonSupport.mapper.readTree(res.body)
        assertEquals("u-1", node.get("userId")?.asText())
        assertEquals("a@b.com", node.get("email")?.asText())
        assertEquals("en", node.get("preferredLanguage")?.asText())
    }

    @Test
    fun putMe_requiresBody() {
        val controller = UsersController(service = FakeUsersService())
        val res = controller.putMe(null, UserContext(userId = "u-1", email = null))
        assertEquals(400, res.statusCode)
        assertTrue(errorMessage(res.body)?.contains("body is required") == true)
    }

    @Test
    fun putMe_requiresPreferredLanguage() {
        val controller = UsersController(service = FakeUsersService())
        val res = controller.putMe("{}", UserContext(userId = "u-1", email = null))
        assertEquals(400, res.statusCode)
        assertTrue(errorMessage(res.body)?.contains("preferredLanguage is required") == true)
    }

    @Test
    fun putMe_rejectsInvalidLanguage() {
        val controller = UsersController(
            service = object : UsersServicePort {
                override fun getMe(user: UserContext): UserMeResponse = UserMeResponse(userId = user.userId)
                override fun setPreferredLanguage(user: UserContext, preferredLanguage: String): UserMeResponse {
                    throw IllegalArgumentException("preferredLanguage is invalid")
                }
            }
        )
        val res = controller.putMe("""{"preferredLanguage":"xx"}""", UserContext(userId = "u-1", email = null))
        assertEquals(400, res.statusCode)
    }

    @Test
    fun putMe_setsPreferredLanguage() {
        val service = FakeUsersService()
        val controller = UsersController(service = service)

        val res = controller.putMe("""{"preferredLanguage":"es"}""", UserContext(userId = "u-1", email = "a@b.com"))
        assertEquals(200, res.statusCode)
        assertEquals("es", service.lastSetPreferredLanguage)

        val node = JsonSupport.mapper.readTree(res.body)
        assertEquals("u-1", node.get("userId")?.asText())
        assertEquals("es", node.get("preferredLanguage")?.asText())
    }
}
