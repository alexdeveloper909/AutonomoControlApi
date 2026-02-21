package autonomo.controller

import autonomo.config.JsonSupport
import autonomo.model.UserContext
import autonomo.model.WorkspaceMember
import autonomo.service.RegularSpendingsServicePort
import autonomo.service.WorkspaceAccessPort
import autonomo.util.ApiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RegularSpendingsControllerTest {

    @Test
    fun listDefinitionsWithoutUserReturns401() {
        val controller = RegularSpendingsController(FakeService(), FakeAccessService(true))
        val res = controller.listDefinitions("ws-1", null)
        assertEquals(401, res.statusCode)
    }

    @Test
    fun listDefinitionsWithoutMembershipReturns403() {
        val controller = RegularSpendingsController(FakeService(), FakeAccessService(false))
        val res = controller.listDefinitions("ws-1", UserContext("user-1", "user@example.com"))
        assertEquals(403, res.statusCode)
    }

    @Test
    fun listOccurrencesRequiresFromAndTo() {
        val controller = RegularSpendingsController(FakeService(), FakeAccessService(true))
        val res = controller.listOccurrences("ws-1", emptyMap(), UserContext("user-1", "user@example.com"))
        assertEquals(400, res.statusCode)
        val err = JsonSupport.mapper.readValue(res.body, ApiError::class.java)
        assertEquals("from is required (YYYY-MM-DD)", err.message)
    }

    @Test
    fun listOccurrencesRejectsToBeforeFrom() {
        val controller = RegularSpendingsController(FakeService(), FakeAccessService(true))
        val res = controller.listOccurrences(
            "ws-1",
            mapOf("from" to "2026-03-01", "to" to "2026-02-01"),
            UserContext("user-1", "user@example.com")
        )
        assertEquals(400, res.statusCode)
        val err = JsonSupport.mapper.readValue(res.body, ApiError::class.java)
        assertEquals("to must be >= from", err.message)
    }

    @Test
    fun listOccurrencesPassesParsedDatesToService() {
        val service = FakeService()
        val controller = RegularSpendingsController(service, FakeAccessService(true))
        val res = controller.listOccurrences(
            "ws-1",
            mapOf("from" to "2026-02-01", "to" to "2026-02-28"),
            UserContext("user-1", "user@example.com")
        )
        assertEquals(200, res.statusCode)
        assertEquals(LocalDate.parse("2026-02-01"), service.lastFrom)
        assertEquals(LocalDate.parse("2026-02-28"), service.lastTo)
    }

    private class FakeService : RegularSpendingsServicePort {
        var lastFrom: LocalDate? = null
        var lastTo: LocalDate? = null

        override fun listDefinitions(workspaceId: String): autonomo.model.RecordsResponse =
            autonomo.model.RecordsResponse(items = emptyList(), nextToken = null)

        override fun listOccurrences(
            workspaceId: String,
            fromInclusive: LocalDate,
            toInclusive: LocalDate
        ): autonomo.model.RegularSpendingOccurrencesResponse {
            lastFrom = fromInclusive
            lastTo = toInclusive
            return autonomo.model.RegularSpendingOccurrencesResponse(from = fromInclusive, to = toInclusive, items = emptyList())
        }
    }

    private class FakeAccessService(private val allowed: Boolean) : WorkspaceAccessPort {
        override fun canAccess(workspaceId: String, user: UserContext): Boolean = allowed
        override fun canWrite(workspaceId: String, user: UserContext): Boolean = allowed
        override fun canAdmin(workspaceId: String, user: UserContext): Boolean = allowed
        override fun getActiveMember(workspaceId: String, user: UserContext): WorkspaceMember? {
            if (!allowed) return null
            return WorkspaceMember(
                workspaceId = workspaceId,
                memberKey = "USER#${user.userId}",
                userId = user.userId,
                emailLower = user.email?.lowercase(),
                role = "OWNER",
                status = "ACTIVE"
            )
        }
    }
}
