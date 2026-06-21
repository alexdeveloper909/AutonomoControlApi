package autonomo.controller

import autonomo.service.ExchangeRatesService
import autonomo.service.ExchangeRatesServicePort
import autonomo.util.HttpResponse
import autonomo.util.HttpResponses
import java.time.LocalDate

class ExchangeRatesController(
    private val service: ExchangeRatesServicePort = ExchangeRatesService()
) {
    fun nbuRate(queryParams: Map<String, String>): HttpResponse {
        val currency = queryParams["currency"]?.takeIf { it.isNotBlank() }
            ?: return HttpResponses.badRequest("currency is required")
        val date = queryParams["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return HttpResponses.badRequest("date is required")
        return runCatching {
            HttpResponses.ok(service.nbuRate(currency, date))
        }.getOrElse { error ->
            when (error) {
                is IllegalArgumentException -> HttpResponses.badRequest(error.message ?: "Invalid request")
                else -> HttpResponses.serverError(error.message ?: "NBU exchange-rate lookup failed")
            }
        }
    }
}
