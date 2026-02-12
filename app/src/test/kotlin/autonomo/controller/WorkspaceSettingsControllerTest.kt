package autonomo.controller

import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.Settings
import autonomo.model.UserContext
import autonomo.service.WorkspaceAccessPort
import autonomo.service.WorkspaceSettingsServicePort
import autonomo.util.ApiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WorkspaceSettingsControllerTest {
    @Test
    fun putSettingsReadOnlyReturns403() {
        val controller = WorkspaceSettingsController(
            service = FakeSettingsService(),
            access = FakeAccessService(canAccess = true, canWrite = false)
        )

        val response = controller.putSettings("ws-1", settingsJson(2025), UserContext("user-1", "user@example.com"))

        assertEquals(403, response.statusCode)
        val error = autonomo.config.JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("Workspace is read-only for this user", error.message)
    }

    @Test
    fun getSettingsReadOnlyStillReturns200() {
        val controller = WorkspaceSettingsController(
            service = FakeSettingsService(),
            access = FakeAccessService(canAccess = true, canWrite = false)
        )

        val response = controller.getSettings("ws-1", UserContext("user-1", "user@example.com"))

        assertEquals(200, response.statusCode)
    }

    private class FakeSettingsService : WorkspaceSettingsServicePort {
        override fun getSettings(workspaceId: String): Settings? = Settings(
            year = 2025,
            startDate = LocalDate.of(2025, 1, 1),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO
        )

        override fun putSettings(workspaceId: String, settings: Settings, updatedBy: String) = Unit
    }

    private class FakeAccessService(
        private val canAccess: Boolean,
        private val canWrite: Boolean
    ) : WorkspaceAccessPort {
        override fun canAccess(workspaceId: String, user: UserContext): Boolean = canAccess

        override fun canWrite(workspaceId: String, user: UserContext): Boolean = canWrite

        override fun canAdmin(workspaceId: String, user: UserContext): Boolean = false

        override fun getActiveMember(workspaceId: String, user: UserContext) = null
    }

    private fun settingsJson(year: Int): String {
        val settings = Settings(
            year = year,
            startDate = LocalDate.of(year, 1, 1),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO
        )
        return autonomo.config.JsonSupport.mapper.writeValueAsString(settings)
    }
}
