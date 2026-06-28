package autonomo.controller

import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.RetaProjectionMode
import autonomo.domain.RetaScenarioSettings
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

    @Test
    fun rentaSummaryReturns200() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(true))
        val body = settingsJson(year = 2024)

        val response = controller.rentaSummary("ws-1", body, UserContext("user-1", "user@example.com"))

        assertEquals(200, response.statusCode)
        assertTrue(response.body?.contains("\"renta\"") == true)
    }

    @Test
    fun retaSummaryReturns200ForWorkspaceMember() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(true))
        val body = retaRequestJson(year = 2026)

        val response = controller.retaSummary("ws-1", body, UserContext("user-1", "user@example.com"))

        assertEquals(200, response.statusCode)
        assertTrue(response.body?.contains("\"reta\"") == true)
    }

    @Test
    fun retaSummaryWithoutMembershipReturns403() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(false))

        val response = controller.retaSummary("ws-1", retaRequestJson(2026), UserContext("user-1", null))

        assertEquals(403, response.statusCode)
    }

    @Test
    fun retaSummaryRejectsInvalidRequestBody() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(true))

        val response = controller.retaSummary("ws-1", "{", UserContext("user-1", null))

        assertEquals(400, response.statusCode)
        val error = autonomo.config.JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("Invalid RETA summary request", error.message)
    }

    @Test
    fun ivaSummaryReturns200() {
        val controller = SummariesController(FakeSummariesService(), FakeAccessService(true))
        val body = settingsJson(year = 2024)

        val response = controller.ivaSummary("ws-1", body, UserContext("user-1", "user@example.com"))

        assertEquals(200, response.statusCode)
        assertTrue(response.body?.contains("\"iva\"") == true)
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

        override fun ivaSummary(workspaceId: String, settings: Settings) =
            autonomo.model.IvaSummaryResponse(
                settings = settings,
                iva = autonomo.domain.IvaYearEstimate(year = settings.year, quarters = emptyList())
            )

        override fun rentaSummary(workspaceId: String, settings: Settings) =
            autonomo.model.RentaSummaryResponse(
                settings = settings,
                renta = null,
                rentaProjected = null
            )

        override fun retaSummary(workspaceId: String, settings: Settings, scenario: RetaScenarioSettings) =
            autonomo.model.RetaSummaryResponse(
                settings = settings,
                reta = autonomo.domain.RetaPlanner.estimate(
                    settings = settings,
                    invoices = emptyList(),
                    expenses = emptyList(),
                    payments = emptyList(),
                    today = LocalDate.of(settings.year, 6, 28),
                    scenario = scenario,
                    planningOverride = settings.retaPlanning
                )
            )
    }

    private class FakeAccessService(private val allowed: Boolean) : WorkspaceAccessPort {
        override fun canAccess(workspaceId: String, user: UserContext): Boolean = allowed

        override fun canWrite(workspaceId: String, user: UserContext): Boolean = allowed

        override fun canAdmin(workspaceId: String, user: UserContext): Boolean = allowed

        override fun getActiveMember(workspaceId: String, user: UserContext) = null
    }

    private fun settingsJson(year: Int): String {
        val settings = Settings(
            year = year,
            startDate = LocalDate.of(year, 1, 1),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO,
            rentaPlanning = null
        )
        return autonomo.config.JsonSupport.mapper.writeValueAsString(settings)
    }

    private fun retaRequestJson(year: Int): String {
        val settings = Settings(
            year = year,
            startDate = LocalDate.of(year, 1, 1),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO,
            retaPlanning = autonomo.domain.RetaPlanningSettings(enabled = true)
        )
        val request = autonomo.model.RetaSummaryRequest(
            settings = settings,
            scenario = RetaScenarioSettings(
                projectionMode = RetaProjectionMode.MANUAL_FUTURE_MONTHLY_INCOME,
                manualFutureMonthlyActivityNet = Money.eur("3500")
            )
        )
        return autonomo.config.JsonSupport.mapper.writeValueAsString(request)
    }
}
