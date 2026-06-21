package autonomo.model

import autonomo.domain.BusinessEntity
import autonomo.domain.BusinessEntityType
import autonomo.domain.UkrainianFopSocialContributionSettings
import autonomo.domain.UkrainianFopTaxRates
import autonomo.domain.UkrainianFopYearSummary

data class BusinessEntitiesResponse(
    val items: List<Any>
)

data class BusinessEntityResponse(
    val entity: BusinessEntity
)

data class BusinessEntityWriteRequest(
    val type: BusinessEntityType,
    val name: String,
    val taxCurrency: String,
    val invoiceCurrencies: List<String>,
    val taxRatesByYear: Map<Int, UkrainianFopTaxRates> = emptyMap(),
    val socialContribution: UkrainianFopSocialContributionSettings = UkrainianFopSocialContributionSettings(),
    val confirmHistoricalSummaryChange: Boolean = false
)

data class UkrainianFopSummaryResponse(
    val entity: BusinessEntity,
    val summary: UkrainianFopYearSummary,
    val isComplete: Boolean
)
