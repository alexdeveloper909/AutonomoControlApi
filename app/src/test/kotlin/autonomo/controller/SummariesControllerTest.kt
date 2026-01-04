package autonomo.controller

import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.Settings
import autonomo.model.UserContext
import autonomo.service.SummariesServicePort
import autonomo.service.WorkspaceAccessPort
import autonomo.util.ApiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SummariesControllerTest {
    @Test
    fun monthSummariesReturns200() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(true))
        val body = settingsJson(year = 2024)

        val response = controller.monthSummaries("ws-1", body, UserContext("user-1", "user@example.com"))

        assertEquals(200, response.statusCode)
        assertTrue(response.body?.contains("\"items\"") == true)
    }

    @Test
    fun monthSummariesWithoutUserReturns401() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(true))

        val response = controller.monthSummaries("ws-1", settingsJson(2024), null)

        assertEquals(401, response.statusCode)
    }

    @Test
    fun monthSummariesWithoutMembershipReturns403() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(false))

        val response = controller.monthSummaries("ws-1", settingsJson(2024), UserContext("user-1", null))

        assertEquals(403, response.statusCode)
    }

    @Test
    fun monthSummariesRejectsInvalidSettings() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(true))

        val response = controller.monthSummaries("ws-1", "{", UserContext("user-1", null))

        assertEquals(400, response.statusCode)
        val error = autonomo.config.JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("Invalid settings", error.message)
    }

    private class FakeSummariesService : SummariesServicePort {
        override fun monthSummaries(workspaceId: String, settings: Settings) =
            autonomo.model.MonthSummariesResponse(
                settings = settings,
                items = emptyList()
            )

        override fun quarterSummaries(workspaceId: String, settings: Settings) =
            autonomo.model.QuarterSummariesResponse(
                settings = settings,
                items = emptyList()
            )
    }

    private class FakeAccessService(private val allowed: Boolean) : WorkspaceAccessPort {
        override fun canAccess(workspaceId: String, user: UserContext): Boolean = allowed
    }

    private fun settingsJson(year: Int): String {
        val settings = Settings(
            year = year,
            startDate = LocalDate.of(year, 1, 1),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO,
            expenseCategories = emptySet()
        )
        return autonomo.config.JsonSupport.mapper.writeValueAsString(settings)
    }
}
