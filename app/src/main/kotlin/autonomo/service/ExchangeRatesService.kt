package autonomo.service

import autonomo.config.JsonSupport
import autonomo.model.ExchangeRateResponse
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

interface ExchangeRatesServicePort {
    fun nbuRate(currency: String, date: LocalDate): ExchangeRateResponse
}

interface ExchangeRateHttpClient {
    fun get(uri: URI): ExchangeRateHttpResponse
}

data class ExchangeRateHttpResponse(
    val statusCode: Int,
    val body: String
)

class JdkExchangeRateHttpClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) : ExchangeRateHttpClient {
    override fun get(uri: URI): ExchangeRateHttpResponse {
        val request = HttpRequest.newBuilder()
            .uri(uri)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return ExchangeRateHttpResponse(statusCode = response.statusCode(), body = response.body())
    }
}

class ExchangeRatesService(
    private val httpClient: ExchangeRateHttpClient = JdkExchangeRateHttpClient()
) : ExchangeRatesServicePort {
    private val cache = ConcurrentHashMap<String, ExchangeRateResponse>()

    override fun nbuRate(currency: String, date: LocalDate): ExchangeRateResponse {
        val normalizedCurrency = currency.trim().uppercase()
        require(normalizedCurrency == "USD") { "Only USD exchange rates are supported for MVP" }
        val cacheKey = "$normalizedCurrency#$date"
        cache[cacheKey]?.let { return it }

        val urlDate = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val uri = URI.create("https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?valcode=$normalizedCurrency&date=$urlDate&json")
        val response = runCatching { httpClient.get(uri) }
            .getOrElse { throw IllegalStateException("NBU exchange-rate lookup failed") }
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("NBU exchange-rate lookup failed")
        }
        val root = JsonSupport.mapper.readTree(response.body)
        val item = root.firstOrNull() ?: throw IllegalStateException("NBU exchange-rate lookup returned no rate")
        val rate = item.get("rate")?.decimalValue()
            ?: throw IllegalStateException("NBU exchange-rate lookup returned no rate")
        val result = ExchangeRateResponse(
            currency = normalizedCurrency,
            taxCurrency = "UAH",
            date = date,
            rate = BigDecimal(rate.toPlainString()),
            source = "NBU",
            fetchedAt = Instant.now()
        )
        cache[cacheKey] = result
        return result
    }
}
