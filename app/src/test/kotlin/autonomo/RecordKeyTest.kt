package autonomo

import autonomo.model.RecordKey
import autonomo.model.RecordType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecordKeyTest {
    @Test
    fun buildAndParseRoundTrip() {
        val date = LocalDate.of(2024, 6, 15)
        val key = RecordKey.build(RecordType.INVOICE, date, "abc-123")
        val parsed = RecordKey.parse(key)

        assertNotNull(parsed)
        assertEquals(RecordType.INVOICE, parsed?.recordType)
        assertEquals(date, parsed?.eventDate)
        assertEquals("abc-123", parsed?.recordId)
    }
}
