package autonomo.controller

import autonomo.model.ExchangeRateResponse
import autonomo.service.ExchangeRatesServicePort
import autonomo.util.ApiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ExchangeRatesControllerTest {
    @Test
    fun nbuRateReturnsUsdRate() {
        val controller = ExchangeRatesController(FakeExchangeRatesService())

        val response = controller.nbuRate(mapOf("currency" to "USD", "date" to "2026-06-15"))

        assertEquals(200, response.statusCode)
        assertEquals("USD", FakeExchangeRatesService.lastCurrency)
        assertEquals(LocalDate.parse("2026-06-15"), FakeExchangeRatesService.lastDate)
        assertEquals(true, response.body?.contains("\"source\":\"NBU\""))
    }

    @Test
    fun nbuRateRejectsMissingDate() {
        val controller = ExchangeRatesController(FakeExchangeRatesService())

        val response = controller.nbuRate(mapOf("currency" to "USD"))

        assertEquals(400, response.statusCode)
        val error = autonomo.config.JsonSupport.mapper.readValue(response.body, ApiError::class.java)
        assertEquals("date is required", error.message)
    }

    private class FakeExchangeRatesService : ExchangeRatesServicePort {
        override fun nbuRate(currency: String, date: LocalDate): ExchangeRateResponse {
            lastCurrency = currency
            lastDate = date
            return ExchangeRateResponse(
                currency = currency,
                taxCurrency = "UAH",
                date = date,
                rate = BigDecimal("40.50"),
                source = "NBU",
                fetchedAt = Instant.parse("2026-06-15T10:00:00Z")
            )
        }

        companion object {
            var lastCurrency: String? = null
            var lastDate: LocalDate? = null
        }
    }
}
