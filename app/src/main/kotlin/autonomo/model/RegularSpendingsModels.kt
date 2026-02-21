package autonomo.model

import autonomo.domain.Money
import java.time.LocalDate

data class RegularSpendingOccurrence(
    val recordId: String,
    val name: String,
    val payoutDate: LocalDate,
    val amount: Money
)

data class RegularSpendingOccurrencesResponse(
    val from: LocalDate,
    val to: LocalDate,
    val items: List<RegularSpendingOccurrence>
)

