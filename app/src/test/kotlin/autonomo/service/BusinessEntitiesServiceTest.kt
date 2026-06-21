package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.BusinessEntity
import autonomo.domain.BusinessEntityType
import autonomo.domain.Money
import autonomo.domain.MonthKey
import autonomo.domain.Rate
import autonomo.domain.Settings
import autonomo.domain.SocialContributionExemptionReason
import autonomo.domain.UkrainianFopSocialContributionSettings
import autonomo.domain.UkrainianFopSocialContributionYearSettings
import autonomo.domain.UkrainianFopTaxRates
import autonomo.model.BusinessEntityWriteRequest
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.repository.WorkspaceSettingsRepositoryPort
import autonomo.util.RecordPayloadParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class BusinessEntitiesServiceTest {
    @Test
    fun createBusinessEntityGeneratesServerIdAndTimestamps() {
        val repo = FakeSettingsRepository(settings(entities = emptyList()))
        val service = BusinessEntitiesService(repo, FakeRecordsRepository())

        val entity = service.createBusinessEntity("ws-1", "user-1", writeRequest(name = "Ukrainian FOP"))

        assertTrue(entity.entityId.startsWith("ent_"))
        assertNotEquals("autonomo", entity.entityId)
        assertEquals("Ukrainian FOP", entity.name)
        assertTrue(entity.createdAt != null)
        assertTrue(entity.updatedAt != null)
        assertEquals(listOf(entity.entityId), repo.savedSettings!!.businessEntities().map { it.entityId })
    }

    @Test
    fun createBusinessEntityRejectsMissingInitialSocialContributionYear() {
        val service = BusinessEntitiesService(FakeSettingsRepository(settings(entities = emptyList())), FakeRecordsRepository())

        val error = assertThrows<IllegalArgumentException> {
            service.createBusinessEntity(
                "ws-1",
                "user-1",
                writeRequest(socialContribution = UkrainianFopSocialContributionSettings())
            )
        }

        assertEquals("Initial Ukrainian FOP social-contribution year is required", error.message)
    }

    @Test
    fun listBusinessEntitiesIncludesAutonomoAndHidesArchivedByDefault() {
        val active = fopEntity("ent_active", "Active FOP")
        val archived = fopEntity("ent_archived", "Archived FOP").copy(archivedAt = Instant.parse("2026-06-01T00:00:00Z"))
        val service = BusinessEntitiesService(FakeSettingsRepository(settings(entities = listOf(active, archived))), FakeRecordsRepository())

        val activeOnly = service.listBusinessEntities("ws-1", includeArchived = false)
        val all = service.listBusinessEntities("ws-1", includeArchived = true)

        assertEquals(2, activeOnly.size)
        assertEquals(3, all.size)
        assertTrue(activeOnly.any { it.toString().contains("autonomo") })
        assertFalse(activeOnly.any { it.toString().contains("Archived FOP") })
    }

    @Test
    fun updateBusinessEntityRejectsRemovingCurrencyUsedByInvoices() {
        val entity = fopEntity()
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(entity))),
            FakeRecordsRepository(itemsByPrefix = mapOf("BUSINESS_ENTITY_INVOICE#2026-" to listOf(invoiceItem())))
        )

        val error = assertThrows<IllegalArgumentException> {
            service.updateBusinessEntity(
                "ws-1",
                "user-1",
                "ent_fop",
                writeRequest(invoiceCurrencies = listOf("UAH"), confirmHistoricalSummaryChange = true)
            )
        }

        assertEquals("BusinessEntity.invoiceCurrencies cannot remove currencies used by existing invoices", error.message)
    }

    @Test
    fun ukrainianFopSummaryUsesPersistedSettingsAndEntityScopedInvoices() {
        val entity = fopEntity()
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(entity, fopEntity("ent_other", "Other FOP")))),
            FakeRecordsRepository(
                itemsByPrefix = mapOf(
                    "BUSINESS_ENTITY_INVOICE#2026-" to listOf(
                        invoiceItem(recordId = "rec-1"),
                        invoiceItem(recordId = "rec-2", entityId = "ent_other", number = "OTHER")
                    )
                )
            )
        )

        val response = service.ukrainianFopSummary("ws-1", "ent_fop", 2026)

        assertEquals(true, response.isComplete)
        assertEquals(1, response.summary.totals.invoiceCount)
        assertEquals(0, response.summary.totals.taxableRevenue.amount.compareTo(BigDecimal("40500.00")))
    }

    @Test
    fun ukrainianFopSummaryReturnsIncompleteWhenYearSettingsMissing() {
        val entity = fopEntity().copy(taxRatesByYear = emptyMap())
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(entity))),
            FakeRecordsRepository(itemsByPrefix = mapOf("BUSINESS_ENTITY_INVOICE#2026-" to listOf(invoiceItem())))
        )

        val response = service.ukrainianFopSummary("ws-1", "ent_fop", 2026)

        assertEquals(false, response.isComplete)
        assertTrue(response.summary.warningCodes.any { it.name == "MISSING_TAX_RATES" })
    }

    @Test
    fun ukrainianFopSummaryIncludesConfiguredZeroRevenueMonthsWhenSocialContributionEnabled() {
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(fopEntity()))),
            FakeRecordsRepository()
        )

        val response = service.ukrainianFopSummary("ws-1", "ent_fop", 2026)

        assertEquals(true, response.isComplete)
        assertEquals(12, response.summary.months.size)
        assertEquals(0, response.summary.totals.invoiceCount)
        assertEquals(0, response.summary.totals.socialContribution.amount.compareTo(BigDecimal("21120.00")))
    }

    @Test
    fun ukrainianFopSummarySupportsSocialContributionDisabilityExemption() {
        val entity = fopEntity().copy(
            socialContribution = UkrainianFopSocialContributionSettings(
                byYear = mapOf(2026 to disabledSocialContributionYear())
            )
        )
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(entity))),
            FakeRecordsRepository(itemsByPrefix = mapOf("BUSINESS_ENTITY_INVOICE#2026-" to listOf(invoiceItem())))
        )

        val response = service.ukrainianFopSummary("ws-1", "ent_fop", 2026)

        assertEquals(true, response.isComplete)
        assertEquals(0, response.summary.totals.socialContribution.amount.compareTo(BigDecimal("0.00")))
        assertEquals(SocialContributionExemptionReason.DISABILITY, response.summary.effectiveYearSettings.socialContribution!!.exemptionReason)
    }

    @Test
    fun ukrainianFopSummaryIgnoresStoredMonthlyAmountsWhenSocialContributionDisabled() {
        val disabledWithAmounts = UkrainianFopSocialContributionYearSettings(
            enabled = false,
            monthlyAmountsUah = MonthKey.janToDec(2026).associateWith { Money(BigDecimal("9999.00")) },
            exemptionReason = SocialContributionExemptionReason.DISABILITY
        )
        val entity = fopEntity().copy(
            socialContribution = UkrainianFopSocialContributionSettings(
                byYear = mapOf(2026 to disabledWithAmounts)
            )
        )
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(entity))),
            FakeRecordsRepository(itemsByPrefix = mapOf("BUSINESS_ENTITY_INVOICE#2026-" to listOf(invoiceItem())))
        )

        val response = service.ukrainianFopSummary("ws-1", "ent_fop", 2026)

        assertEquals(0, response.summary.totals.socialContribution.amount.compareTo(BigDecimal("0.00")))
    }

    @Test
    fun ukrainianFopSummaryUsesYearScopedSocialContributionAmounts() {
        val entity = fopEntity().copy(
            socialContribution = UkrainianFopSocialContributionSettings(
                byYear = mapOf(2026 to socialContributionYear(amount = "100.00"))
            )
        )
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(entity))),
            FakeRecordsRepository()
        )

        val response = service.ukrainianFopSummary("ws-1", "ent_fop", 2026)

        assertEquals(0, response.summary.totals.socialContribution.amount.compareTo(BigDecimal("1200.00")))
    }

    @Test
    fun ukrainianFopSummaryUsesYearScopedTaxRates() {
        val entity = fopEntity().copy(
            taxRatesByYear = mapOf(2026 to UkrainianFopTaxRates(Rate.fromDecimal("0.03"), Rate.fromDecimal("0.00"))),
            socialContribution = UkrainianFopSocialContributionSettings(
                byYear = mapOf(2026 to socialContributionYear(amount = "100.00"))
            )
        )
        val service = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(entity))),
            FakeRecordsRepository(itemsByPrefix = mapOf("BUSINESS_ENTITY_INVOICE#2026-" to listOf(invoiceItem())))
        )

        val response = service.ukrainianFopSummary("ws-1", "ent_fop", 2026)

        assertEquals(0, response.summary.totals.singleTax.amount.compareTo(BigDecimal("1215.00")))
        assertEquals(0, response.summary.totals.militaryLevy.amount.compareTo(BigDecimal("0.00")))
    }

    @Test
    fun createBusinessEntityRejectsDuplicateActiveNameButAllowsArchivedDuplicateName() {
        val activeDuplicateService = BusinessEntitiesService(
            FakeSettingsRepository(settings(entities = listOf(fopEntity(name = "FOP")))),
            FakeRecordsRepository()
        )

        val error = assertThrows<IllegalArgumentException> {
            activeDuplicateService.createBusinessEntity("ws-1", "user-1", writeRequest(name = " fop "))
        }
        assertEquals("Active business entity names must be unique", error.message)

        val archived = fopEntity(name = "FOP").copy(archivedAt = Instant.parse("2026-06-01T00:00:00Z"))
        val archivedDuplicateRepo = FakeSettingsRepository(settings(entities = listOf(archived)))
        val archivedDuplicateService = BusinessEntitiesService(archivedDuplicateRepo, FakeRecordsRepository())

        val created = archivedDuplicateService.createBusinessEntity("ws-1", "user-1", writeRequest(name = "FOP"))

        assertEquals("FOP", created.name)
        assertEquals(2, archivedDuplicateRepo.savedSettings!!.businessEntities().size)
    }

    private class FakeSettingsRepository(
        private val currentSettings: Settings
    ) : WorkspaceSettingsRepositoryPort {
        var savedSettings: Settings? = null
        override fun getSettings(workspaceId: String): Settings? = savedSettings ?: currentSettings
        override fun putSettings(workspaceId: String, settings: Settings, updatedBy: String) {
            savedSettings = settings
        }
        override fun deleteSettings(workspaceId: String) = Unit
        override fun setTtl(workspaceId: String, ttlEpoch: Long) = Unit
        override fun clearTtl(workspaceId: String) = Unit
    }

    private class FakeRecordsRepository(
        private val itemsByPrefix: Map<String, List<RecordItem>> = emptyMap()
    ) : WorkspaceRecordsRepositoryPort {
        override fun create(record: RecordItem) = Unit
        override fun update(record: RecordItem) = Unit
        override fun get(workspaceId: String, recordKey: String): RecordItem? = null
        override fun delete(workspaceId: String, recordKey: String) = Unit
        override fun deleteByWorkspaceId(workspaceId: String) = Unit
        override fun setTtlByWorkspaceId(workspaceId: String, ttlEpoch: Long) = Unit
        override fun clearTtlByWorkspaceId(workspaceId: String) = Unit
        override fun queryByWorkspaceRecordKeyPrefix(workspaceId: String, recordKeyPrefix: String): List<RecordItem> =
            itemsByPrefix[recordKeyPrefix].orEmpty()
        override fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem> = emptyList()
        override fun queryByQuarter(workspaceQuarter: String, recordType: RecordType?): List<RecordItem> = emptyList()
        override fun isConditionalFailure(ex: Exception): Boolean = false
    }

    private fun writeRequest(
        name: String = "FOP",
        invoiceCurrencies: List<String> = listOf("USD", "UAH"),
        socialContribution: UkrainianFopSocialContributionSettings = UkrainianFopSocialContributionSettings(
            byYear = mapOf(2026 to socialContributionYear())
        ),
        confirmHistoricalSummaryChange: Boolean = false
    ): BusinessEntityWriteRequest =
        BusinessEntityWriteRequest(
            type = BusinessEntityType.UKRAINIAN_FOP_GROUP3_SIMPLIFIED,
            name = name,
            taxCurrency = "UAH",
            invoiceCurrencies = invoiceCurrencies,
            taxRatesByYear = mapOf(2026 to UkrainianFopTaxRates()),
            socialContribution = socialContribution,
            confirmHistoricalSummaryChange = confirmHistoricalSummaryChange
        )

    private fun settings(entities: List<BusinessEntity>?): Settings =
        Settings(
            year = 2026,
            startDate = LocalDate.parse("2026-01-01"),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO,
            entities = entities
        )

    private fun fopEntity(entityId: String = "ent_fop", name: String = "FOP"): BusinessEntity =
        BusinessEntity(
            entityId = entityId,
            type = BusinessEntityType.UKRAINIAN_FOP_GROUP3_SIMPLIFIED,
            name = name,
            taxCurrency = "UAH",
            invoiceCurrencies = listOf("USD", "UAH"),
            taxRatesByYear = mapOf(2026 to UkrainianFopTaxRates()),
            socialContribution = UkrainianFopSocialContributionSettings(byYear = mapOf(2026 to socialContributionYear()))
        )

    private fun socialContributionYear(amount: String = "1760.00"): UkrainianFopSocialContributionYearSettings =
        UkrainianFopSocialContributionYearSettings(
            enabled = true,
            monthlyAmountsUah = MonthKey.janToDec(2026).associateWith { Money(BigDecimal(amount)) }
        )

    private fun disabledSocialContributionYear(): UkrainianFopSocialContributionYearSettings =
        UkrainianFopSocialContributionYearSettings(
            enabled = false,
            exemptionReason = SocialContributionExemptionReason.DISABILITY
        )

    private fun invoiceItem(
        recordId: String = "rec-1",
        entityId: String = "ent_fop",
        number: String = "FOP-1"
    ): RecordItem {
        val parsed = RecordPayloadParser.parse(RecordType.BUSINESS_ENTITY_INVOICE, invoicePayload(entityId, number))
        val payloadJson = RecordPayloadParser.toJson(parsed)
        val now = Instant.parse("2026-06-21T10:00:00Z")
        return RecordItem(
            workspaceId = "ws-1",
            recordKey = "BUSINESS_ENTITY_INVOICE#2026-06-15#$recordId",
            recordId = recordId,
            recordType = RecordType.BUSINESS_ENTITY_INVOICE,
            eventDate = LocalDate.parse("2026-06-15"),
            payloadJson = payloadJson,
            workspaceMonth = "WS#ws-1#M#2026-06",
            workspaceQuarter = "WS#ws-1#Q#2026-Q2",
            createdAt = now,
            updatedAt = now,
            createdBy = "user-1",
            updatedBy = "user-1"
        )
    }

    private fun invoicePayload(entityId: String, number: String) = JsonSupport.mapper.readTree(
        """
        {
          "entityId": "$entityId",
          "invoiceType": "UKRAINIAN_FOP",
          "invoiceDate": "2026-06-01",
          "receivedDate": "2026-06-15",
          "number": "$number",
          "client": "Acme",
          "amount": 1000.00,
          "currency": "USD",
          "taxCurrency": "UAH",
          "exchangeRateToTaxCurrency": 40.50,
          "exchangeRateSource": "NBU",
          "exchangeRateDate": "2026-06-15",
          "exchangeRateFetchedAt": "2026-06-15T10:00:00Z",
          "amountTaxCurrency": 40500.00
        }
        """.trimIndent()
    )
}
