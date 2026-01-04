package autonomo.handler

import autonomo.controller.RecordsController
import autonomo.controller.SummariesController
import autonomo.model.UserContext
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse

class RecordsLambda : RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private val controller = RecordsController()
    private val summariesController = SummariesController()

    override fun handleRequest(
        event: APIGatewayV2HTTPEvent,
        context: Context
    ): APIGatewayV2HTTPResponse {
        val response = route(event)
        return APIGatewayV2HTTPResponse.builder()
            .withStatusCode(response.statusCode)
            .withHeaders(response.headers)
            .withBody(response.body)
            .build()
    }

    private fun route(event: APIGatewayV2HTTPEvent): HttpResponse {
        val path = event.rawPath ?: ""
        val method = event.requestContext?.http?.method ?: "GET"
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        val user = userContext(event)

        if (segments.isEmpty()) {
            return HttpResponses.notFound("Route not found")
        }

        if (segments.size == 1 && segments[0] == "health") {
            return HttpResponses.ok(mapOf("status" to "ok"))
        }

        if (segments.size >= 3 && segments[0] == "workspaces" && segments[2] == "records") {
            val workspaceId = segments.getOrNull(1)
            return when {
                segments.size == 3 && method == "POST" ->
                    controller.createRecord(workspaceId, event.body, user)
                segments.size == 3 && method == "GET" ->
                    controller.listRecords(workspaceId, event.queryStringParameters ?: emptyMap(), user)
                segments.size == 6 && method == "GET" ->
                    controller.getRecord(workspaceId, segments[3], segments[4], segments[5], user)
                segments.size == 6 && method == "PUT" ->
                    controller.updateRecord(workspaceId, segments[3], segments[4], segments[5], event.body, user)
                segments.size == 6 && method == "DELETE" ->
                    controller.deleteRecord(workspaceId, segments[3], segments[4], segments[5], user)
                else -> HttpResponses.notFound("Route not found")
            }
        }

        if (segments.size == 4 && segments[0] == "workspaces" && segments[2] == "summaries") {
            val workspaceId = segments.getOrNull(1)
            return when {
                segments[3] == "months" && method == "POST" ->
                    summariesController.monthSummaries(workspaceId, event.body, user)
                segments[3] == "quarters" && method == "POST" ->
                    summariesController.quarterSummaries(workspaceId, event.body, user)
                else -> HttpResponses.notFound("Route not found")
            }
        }

        return HttpResponses.notFound("Route not found")
    }

    private fun userContext(event: APIGatewayV2HTTPEvent): UserContext? {
        val jwtClaims = event.requestContext?.authorizer?.jwt?.claims
        val userId = jwtClaims?.get("sub")?.toString()
            ?: jwtClaims?.get("username")?.toString()
            ?: return null
        val email = jwtClaims?.get("email")?.toString()
        return UserContext(userId = userId, email = email)
    }
}
