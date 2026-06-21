package autonomo.controller

import autonomo.domain.BusinessEntity
import autonomo.domain.BusinessEntityType
import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.Settings
import autonomo.domain.UkrainianFopSocialContributionSettings
import autonomo.domain.UkrainianFopTaxRates
import autonomo.model.BusinessEntityWriteRequest
import autonomo.model.UkrainianFopSummaryResponse
import autonomo.model.UserContext
import autonomo.service.BusinessEntitiesServicePort
import autonomo.service.WorkspaceAccessPort
import autonomo.util.ApiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BusinessEntitiesControllerTest {
    @Test
    fun createBusinessEntityRejectsClientSuppliedEntityId() {
        val controller = BusinessEntitiesController(FakeBusinessEntitiesService(), FakeAccessService(canAccess = true, canWrite = true))
        val body = """
            {
              "entityId": "autonomo",
              "type": "UKRAINIAN_FOP_GROUP3_SIMPLIFIED",
              "name": "FOP",
              "taxCurrency": "UAH",
              "invoiceCurrencies": ["USD", "UAH"],
              "taxRatesByYear": { "2026": { "singleTaxRate": 0.05, "militaryLevyRate": 0.01 } },
              "socialContribution": { "byYear": { "2026": { "enabled": false, "exemptionReason": "DISABILITY" } } }
            }
        """.trimIndent()

        val response = controller.createBusinessEntity("ws-1", body, UserContext("user-1", "user@example.com"))

        assertEquals(400, response.statusCode)
        val error = autonomo.config.JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("entityId is server-generated", error.message)
    }

    private class FakeBusinessEntitiesService : BusinessEntitiesServicePort {
        override fun listBusinessEntities(workspaceId: String, includeArchived: Boolean): List<Any> = emptyList()
        override fun createBusinessEntity(
            workspaceId: String,
            userId: String,
            request: BusinessEntityWriteRequest
        ): BusinessEntity = fopEntity()
        override fun updateBusinessEntity(
            workspaceId: String,
            userId: String,
            entityId: String,
            request: BusinessEntityWriteRequest
        ): BusinessEntity = fopEntity()
        override fun archiveBusinessEntity(workspaceId: String, userId: String, entityId: String): BusinessEntity = fopEntity()
        override fun ukrainianFopSummary(workspaceId: String, entityId: String, year: Int): UkrainianFopSummaryResponse =
            throw UnsupportedOperationException()
    }

    private class FakeAccessService(
        private val canAccess: Boolean,
        private val canWrite: Boolean
    ) : WorkspaceAccessPort {
        override fun canAccess(workspaceId: String, user: UserContext): Boolean = canAccess
        override fun canWrite(workspaceId: String, user: UserContext): Boolean = canWrite
        override fun canAdmin(workspaceId: String, user: UserContext): Boolean = canWrite
        override fun getActiveMember(workspaceId: String, user: UserContext) = null
    }

    private companion object {
        fun fopEntity(): BusinessEntity =
            BusinessEntity(
                entityId = "ent_fop",
                type = BusinessEntityType.UKRAINIAN_FOP_GROUP3_SIMPLIFIED,
                name = "FOP",
                taxCurrency = "UAH",
                invoiceCurrencies = listOf("USD", "UAH"),
                taxRatesByYear = mapOf(2026 to UkrainianFopTaxRates()),
                socialContribution = UkrainianFopSocialContributionSettings()
            )

        @Suppress("unused")
        fun settings(): Settings =
            Settings(
                year = 2026,
                startDate = LocalDate.parse("2026-01-01"),
                ivaStd = Rate.fromDecimal("0.21"),
                irpfRate = Rate.fromDecimal("0.20"),
                obligacion130 = true,
                openingBalance = Money.ZERO
            )
    }
}
