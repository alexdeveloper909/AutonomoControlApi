package autonomo.model

import autonomo.domain.MonthSummary
import autonomo.domain.QuarterSummary
import autonomo.domain.Settings

data class MonthSummariesResponse(
    val settings: Settings,
    val items: List<MonthSummary>
)

data class QuarterSummariesResponse(
    val settings: Settings,
    val items: List<QuarterSummary>
)

