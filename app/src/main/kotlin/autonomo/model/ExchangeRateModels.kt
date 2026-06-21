package autonomo.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ExchangeRateResponse(
    val currency: String,
    val taxCurrency: String,
    val date: LocalDate,
    val rate: BigDecimal,
    val source: String,
    val fetchedAt: Instant
)
