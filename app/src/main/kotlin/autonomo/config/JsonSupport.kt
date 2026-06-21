package autonomo.config

import autonomo.domain.MonthKey
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.YearMonth

object JsonSupport {
    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(
            SimpleModule()
                .addKeySerializer(MonthKey::class.java, MonthKeyKeySerializer())
                .addKeyDeserializer(MonthKey::class.java, MonthKeyKeyDeserializer())
        )
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private class MonthKeyKeySerializer : StdSerializer<MonthKey>(MonthKey::class.java) {
        override fun serialize(value: MonthKey, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeFieldName(value.toString())
        }
    }

    private class MonthKeyKeyDeserializer : KeyDeserializer() {
        override fun deserializeKey(key: String, ctxt: DeserializationContext): MonthKey =
            runCatching { MonthKey(YearMonth.parse(key)) }
                .getOrElse { throw IllegalArgumentException("MonthKey map key must be YYYY-MM") }
    }
}
