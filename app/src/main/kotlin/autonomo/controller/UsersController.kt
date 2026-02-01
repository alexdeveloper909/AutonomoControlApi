package autonomo.controller

import autonomo.model.UserContext
import autonomo.model.UserUpdateRequest
import autonomo.service.UsersService
import autonomo.service.UsersServicePort
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses

class UsersController(
    private val service: UsersServicePort = UsersService()
) {
    fun getMe(user: UserContext?): HttpResponse {
        val caller = user ?: return HttpResponses.unauthorized()
        return runCatching {
            HttpResponses.ok(service.getMe(caller))
        }.getOrElse { HttpResponses.serverError(it.message ?: "Failed to load user") }
    }

    fun putMe(body: String?, user: UserContext?): HttpResponse {
        val caller = user ?: return HttpResponses.unauthorized()
        if (body.isNullOrBlank()) return HttpResponses.badRequest("body is required")

        return runCatching {
            val req = Json.readUpdateRequest(body)
            val preferredLanguage = req.preferredLanguage?.trim()?.takeIf { it.isNotEmpty() }
                ?: return HttpResponses.badRequest("preferredLanguage is required")
            HttpResponses.ok(service.setPreferredLanguage(caller, preferredLanguage))
        }.getOrElse { HttpResponses.badRequest(it.message ?: "Invalid request") }
    }

    private object Json {
        fun readUpdateRequest(body: String): UserUpdateRequest =
            autonomo.config.JsonSupport.mapper.readValue(body, UserUpdateRequest::class.java)
    }
}

