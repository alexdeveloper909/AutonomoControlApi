package autonomo.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.time.LocalDate

class ExchangeRatesServiceTest {
    @Test
    fun nbuRateFetchesUsdRateAndCachesByCurrencyDate() {
        val client = FakeExchangeRateHttpClient(
            responses = listOf(
                ExchangeRateHttpResponse(
                    statusCode = 200,
                    body = """[{"cc":"USD","rate":40.50,"exchangedate":"15.06.2026"}]"""
                )
            )
        )
        val service = ExchangeRatesService(client)

        val first = service.nbuRate("usd", LocalDate.parse("2026-06-15"))
        val second = service.nbuRate("USD", LocalDate.parse("2026-06-15"))

        assertEquals("USD", first.currency)
        assertEquals("UAH", first.taxCurrency)
        assertEquals(0, first.rate.compareTo(java.math.BigDecimal("40.50")))
        assertEquals(first, second)
        assertEquals(1, client.requestedUris.size)
        assertEquals(
            "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?valcode=USD&date=20260615&json",
            client.requestedUris.single().toString()
        )
    }

    @Test
    fun nbuRateRejectsNonUsdCurrency() {
        val service = ExchangeRatesService(FakeExchangeRateHttpClient())

        val error = assertThrows<IllegalArgumentException> {
            service.nbuRate("EUR", LocalDate.parse("2026-06-15"))
        }

        assertEquals("Only USD exchange rates are supported for MVP", error.message)
    }

    @Test
    fun nbuRateReturnsClearFailureForUpstreamErrors() {
        val service = ExchangeRatesService(
            FakeExchangeRateHttpClient(
                responses = listOf(ExchangeRateHttpResponse(statusCode = 503, body = "{}"))
            )
        )

        val error = assertThrows<IllegalStateException> {
            service.nbuRate("USD", LocalDate.parse("2026-06-15"))
        }

        assertEquals("NBU exchange-rate lookup failed", error.message)
    }

    @Test
    fun nbuRateReturnsClearFailureWhenNoRateIsReturned() {
        val service = ExchangeRatesService(
            FakeExchangeRateHttpClient(
                responses = listOf(ExchangeRateHttpResponse(statusCode = 200, body = "[]"))
            )
        )

        val error = assertThrows<IllegalStateException> {
            service.nbuRate("USD", LocalDate.parse("2026-06-15"))
        }

        assertEquals("NBU exchange-rate lookup returned no rate", error.message)
    }

    private class FakeExchangeRateHttpClient(
        private val responses: List<ExchangeRateHttpResponse> = emptyList()
    ) : ExchangeRateHttpClient {
        val requestedUris = mutableListOf<URI>()
        private var index = 0

        override fun get(uri: URI): ExchangeRateHttpResponse {
            requestedUris += uri
            return responses.getOrElse(index++) {
                throw IllegalStateException("unexpected request")
            }
        }
    }
}
