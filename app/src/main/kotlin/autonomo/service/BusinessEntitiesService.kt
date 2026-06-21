package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.AUTONOMO_ENTITY_ID
import autonomo.domain.BusinessEntity
import autonomo.domain.BusinessEntitySelectionOption
import autonomo.domain.BusinessEntityType
import autonomo.domain.DefaultDomainService
import autonomo.domain.DomainValidation
import autonomo.domain.MonthKey
import autonomo.domain.SocialContributionExemptionReason
import autonomo.domain.UkrainianFopInvoice
import autonomo.model.BusinessEntityWriteRequest
import autonomo.model.RecordType
import autonomo.model.UkrainianFopSummaryResponse
import autonomo.repository.WorkspaceRecordsRepository
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.repository.WorkspaceSettingsRepository
import autonomo.repository.WorkspaceSettingsRepositoryPort
import autonomo.util.RecordPayloadParser
import java.time.Instant
import java.util.UUID

class BusinessEntityNotFoundException(message: String) : RuntimeException(message)

interface BusinessEntitiesServicePort {
    fun listBusinessEntities(workspaceId: String, includeArchived: Boolean): List<Any>
    fun createBusinessEntity(workspaceId: String, userId: String, request: BusinessEntityWriteRequest): BusinessEntity
    fun updateBusinessEntity(
        workspaceId: String,
        userId: String,
        entityId: String,
        request: BusinessEntityWriteRequest
    ): BusinessEntity
    fun archiveBusinessEntity(workspaceId: String, userId: String, entityId: String): BusinessEntity
    fun ukrainianFopSummary(workspaceId: String, entityId: String, year: Int): UkrainianFopSummaryResponse
}

class BusinessEntitiesService(
    private val settingsRepository: WorkspaceSettingsRepositoryPort = WorkspaceSettingsRepository(),
    private val recordsRepository: WorkspaceRecordsRepositoryPort = WorkspaceRecordsRepository()
) : BusinessEntitiesServicePort {
    override fun listBusinessEntities(workspaceId: String, includeArchived: Boolean): List<Any> {
        val settings = settingsRepository.getSettings(workspaceId)
            ?: throw BusinessEntityNotFoundException("workspace settings not found")
        return listOf(BusinessEntitySelectionOption.autonomo()) +
            settings.businessEntities().filter { includeArchived || it.archivedAt == null }
    }

    override fun createBusinessEntity(
        workspaceId: String,
        userId: String,
        request: BusinessEntityWriteRequest
    ): BusinessEntity {
        val settings = settingsRepository.getSettings(workspaceId)
            ?: throw BusinessEntityNotFoundException("workspace settings not found")
        validateInitialConfiguration(request)
        val now = Instant.now()
        val entity = request.toEntity(
            entityId = "ent_${UUID.randomUUID().toString().replace("-", "")}",
            createdAt = now,
            updatedAt = now,
            archivedAt = null
        )
        val updatedSettings = settings.copy(entities = settings.businessEntities() + entity)
        DomainValidation.validateSettings(updatedSettings)
        settingsRepository.putSettings(workspaceId, updatedSettings, userId)
        return entity
    }

    override fun updateBusinessEntity(
        workspaceId: String,
        userId: String,
        entityId: String,
        request: BusinessEntityWriteRequest
    ): BusinessEntity {
        require(entityId != AUTONOMO_ENTITY_ID) { "Built-in Autonomo entity cannot be updated" }
        val settings = settingsRepository.getSettings(workspaceId)
            ?: throw BusinessEntityNotFoundException("workspace settings not found")
        val existing = settings.businessEntities().firstOrNull { it.entityId == entityId }
            ?: throw BusinessEntityNotFoundException("business entity not found")
        val changedYears = changedYearSettings(existing, request)
        if (changedYears.isNotEmpty() && hasInvoicesForEntityYear(workspaceId, entityId, changedYears)) {
            require(request.confirmHistoricalSummaryChange) {
                "confirmHistoricalSummaryChange is required when updating year settings with existing invoices"
            }
        }
        val usedCurrencies = usedInvoiceCurrencies(workspaceId, entityId)
        val updated = request.toEntity(
            entityId = existing.entityId,
            createdAt = existing.createdAt,
            updatedAt = Instant.now(),
            archivedAt = existing.archivedAt
        )
        DomainValidation.validateBusinessEntityUpdate(existing, updated, usedCurrencies)
        val entities = settings.businessEntities().map { if (it.entityId == entityId) updated else it }
        val updatedSettings = settings.copy(entities = entities)
        DomainValidation.validateSettings(updatedSettings)
        settingsRepository.putSettings(workspaceId, updatedSettings, userId)
        return updated
    }

    override fun archiveBusinessEntity(workspaceId: String, userId: String, entityId: String): BusinessEntity {
        require(entityId != AUTONOMO_ENTITY_ID) { "Built-in Autonomo entity cannot be archived" }
        val settings = settingsRepository.getSettings(workspaceId)
            ?: throw BusinessEntityNotFoundException("workspace settings not found")
        val existing = settings.businessEntities().firstOrNull { it.entityId == entityId }
            ?: throw BusinessEntityNotFoundException("business entity not found")
        val archived = existing.copy(updatedAt = Instant.now(), archivedAt = existing.archivedAt ?: Instant.now())
        DomainValidation.validateBusinessEntityUpdate(existing, archived)
        val entities = settings.businessEntities().map { if (it.entityId == entityId) archived else it }
        val updatedSettings = settings.copy(entities = entities)
        DomainValidation.validateSettings(updatedSettings)
        settingsRepository.putSettings(workspaceId, updatedSettings, userId)
        return archived
    }

    override fun ukrainianFopSummary(workspaceId: String, entityId: String, year: Int): UkrainianFopSummaryResponse {
        require(entityId != AUTONOMO_ENTITY_ID) { "Spanish summaries already have dedicated endpoints" }
        val settings = settingsRepository.getSettings(workspaceId)
            ?: throw BusinessEntityNotFoundException("workspace settings not found")
        val entity = settings.businessEntities().firstOrNull { it.entityId == entityId }
            ?: throw BusinessEntityNotFoundException("business entity not found")
        require(entity.type == BusinessEntityType.UKRAINIAN_FOP_GROUP3_SIMPLIFIED) {
            "Business entity must be Ukrainian FOP"
        }

        val domainService = DefaultDomainService()
        recordsRepository.queryByWorkspaceRecordKeyPrefix(
            workspaceId,
            "${RecordType.BUSINESS_ENTITY_INVOICE.name}#$year-"
        ).forEach { item ->
            val parsed = RecordPayloadParser.parse(item.recordType, JsonSupport.mapper.readTree(item.payloadJson))
                as RecordPayloadParser.ParsedBusinessEntityInvoice
            if (parsed.invoice.entityId == entityId) {
                domainService.registerBusinessEntityInvoice(parsed.invoice, entity)
            }
        }
        val summary = domainService.ukrainianFopYearSummary(settings, entityId, year)
        return UkrainianFopSummaryResponse(
            entity = entity,
            summary = summary,
            isComplete = summary.warningCodes.isEmpty()
        )
    }

    private fun BusinessEntityWriteRequest.toEntity(
        entityId: String,
        createdAt: Instant?,
        updatedAt: Instant?,
        archivedAt: Instant?
    ): BusinessEntity =
        BusinessEntity(
            entityId = entityId,
            type = type,
            name = name,
            taxCurrency = taxCurrency.trim().uppercase(),
            invoiceCurrencies = invoiceCurrencies.map { it.trim().uppercase() },
            taxRatesByYear = taxRatesByYear,
            socialContribution = socialContribution,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archivedAt = archivedAt
        )

    private fun validateInitialConfiguration(request: BusinessEntityWriteRequest) {
        require(request.type == BusinessEntityType.UKRAINIAN_FOP_GROUP3_SIMPLIFIED) {
            "Unsupported business entity type"
        }
        require(request.taxRatesByYear.isNotEmpty()) {
            "Initial Ukrainian FOP tax-rate year is required"
        }
        request.taxRatesByYear.keys.forEach { year ->
            val social = request.socialContribution.byYear[year]
                ?: throw IllegalArgumentException("Initial Ukrainian FOP social-contribution year is required")
            if (social.enabled) {
                val expectedMonths = MonthKey.janToDec(year).toSet()
                require(social.monthlyAmountsUah.keys.containsAll(expectedMonths)) {
                    "Initial Ukrainian FOP social-contribution monthly amounts are required"
                }
            } else {
                require(social.exemptionReason == SocialContributionExemptionReason.DISABILITY) {
                    "Disabled social contribution requires exemptionReason=DISABILITY"
                }
            }
        }
    }

    private fun usedInvoiceCurrencies(workspaceId: String, entityId: String): Set<String> =
        allBusinessEntityInvoices(workspaceId)
            .filter { it.entityId == entityId }
            .map { it.currency }
            .toSet()

    private fun hasInvoicesForEntityYear(workspaceId: String, entityId: String, years: Set<Int>): Boolean =
        years.any { year ->
            recordsRepository.queryByWorkspaceRecordKeyPrefix(
                workspaceId,
                "${RecordType.BUSINESS_ENTITY_INVOICE.name}#$year-"
            ).any { item ->
                val parsed = RecordPayloadParser.parse(item.recordType, JsonSupport.mapper.readTree(item.payloadJson))
                    as RecordPayloadParser.ParsedBusinessEntityInvoice
                parsed.invoice.entityId == entityId
            }
        }

    private fun changedYearSettings(existing: BusinessEntity, request: BusinessEntityWriteRequest): Set<Int> {
        val taxYears = existing.taxRatesByYear.keys + request.taxRatesByYear.keys
        val socialYears = existing.socialContribution.byYear.keys + request.socialContribution.byYear.keys
        return (taxYears + socialYears).filter { year ->
            existing.taxRatesByYear[year] != request.taxRatesByYear[year] ||
                existing.socialContribution.byYear[year] != request.socialContribution.byYear[year]
        }.toSet()
    }

    private fun allBusinessEntityInvoices(workspaceId: String): List<UkrainianFopInvoice> =
        (2000..2099).flatMap { year ->
            recordsRepository.queryByWorkspaceRecordKeyPrefix(
                workspaceId,
                "${RecordType.BUSINESS_ENTITY_INVOICE.name}#$year-"
            )
        }.mapNotNull { item ->
            runCatching {
                val parsed = RecordPayloadParser.parse(item.recordType, JsonSupport.mapper.readTree(item.payloadJson))
                    as RecordPayloadParser.ParsedBusinessEntityInvoice
                parsed.invoice
            }.getOrNull()
        }
}
