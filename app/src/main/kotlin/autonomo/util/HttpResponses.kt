package autonomo.util

import autonomo.config.JsonSupport

data class ApiError(val message: String)

data class HttpResponse(
    val statusCode: Int,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap()
)

object HttpResponses {
    private val jsonHeaders = mapOf("Content-Type" to "application/json")

    fun ok(body: Any): HttpResponse = json(200, body)
    fun created(body: Any): HttpResponse = json(201, body)
    fun noContent(): HttpResponse = HttpResponse(204, null, emptyMap())
    fun badRequest(message: String): HttpResponse = error(400, message)
    fun unauthorized(message: String = "Unauthorized"): HttpResponse = error(401, message)
    fun forbidden(message: String = "Forbidden"): HttpResponse = error(403, message)
    fun notFound(message: String = "Not found"): HttpResponse = error(404, message)
    fun conflict(message: String): HttpResponse = error(409, message)
    fun serverError(message: String): HttpResponse = error(500, message)

    private fun json(status: Int, body: Any): HttpResponse {
        return HttpResponse(status, JsonSupport.mapper.writeValueAsString(body), jsonHeaders)
    }

    private fun error(status: Int, message: String): HttpResponse {
        return HttpResponse(status, JsonSupport.mapper.writeValueAsString(ApiError(message)), jsonHeaders)
    }
}
