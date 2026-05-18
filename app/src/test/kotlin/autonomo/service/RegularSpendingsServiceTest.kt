package autonomo.service

import autonomo.config.JsonSupport
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class RegularSpendingsServiceTest {
    @Test
    fun listDefinitionsNormalizesFixedTermPayloadFields() {
        val repo = FakeRecordsRepository(
            listOf(
                sampleRegularSpending(
                    recordId = "rs-1",
                    eventDate = LocalDate.parse("2026-06-15"),
                    payloadJson = """
                        {
                          "name": "Installment",
                          "startDate": "2026-06-15",
                          "scheduleType": "FIXED_TERM",
                          "paymentCount": 6,
                          "amount": 120.00
                        }
                    """.trimIndent()
                )
            )
        )
        val service = RegularSpendingsService(repo)

        val response = service.listDefinitions("ws-1")
        val payload = response.items.single().payload

        assertEquals("FIXED_TERM", payload.get("scheduleType").asText())
        assertEquals(6, payload.get("paymentCount").asInt())
        assertFalse(payload.has("cadence"))
    }

    @Test
    fun occurrencesReturnExactlyPaymentCountAcrossBroadRange() {
        val service = RegularSpendingsService(
            FakeRecordsRepository(
                listOf(
                    sampleRegularSpending(
                        recordId = "rs-1",
                        eventDate = LocalDate.parse("2026-06-15"),
                        payloadJson = fixedTermPayload(paymentCount = 3)
                    )
                )
            )
        )

        val response = service.listOccurrences(
            workspaceId = "ws-1",
            fromInclusive = LocalDate.parse("2026-01-01"),
            toInclusive = LocalDate.parse("2026-12-31")
        )

        assertEquals(
            listOf("2026-06-15", "2026-07-15", "2026-08-15"),
            response.items.map { it.payoutDate.toString() }
        )
    }

    @Test
    fun occurrencesRespectNarrowRangeInMiddleOfFixedTermPlan() {
        val service = RegularSpendingsService(
            FakeRecordsRepository(
                listOf(
                    sampleRegularSpending(
                        recordId = "rs-1",
                        eventDate = LocalDate.parse("2026-06-15"),
                        payloadJson = fixedTermPayload(paymentCount = 6)
                    )
                )
            )
        )

        val response = service.listOccurrences(
            workspaceId = "ws-1",
            fromInclusive = LocalDate.parse("2026-08-01"),
            toInclusive = LocalDate.parse("2026-09-30")
        )

        assertEquals(
            listOf("2026-08-15", "2026-09-15"),
            response.items.map { it.payoutDate.toString() }
        )
    }

    @Test
    fun occurrencesReturnNoItemsAfterFinalFixedTermPayment() {
        val service = RegularSpendingsService(
            FakeRecordsRepository(
                listOf(
                    sampleRegularSpending(
                        recordId = "rs-1",
                        eventDate = LocalDate.parse("2026-06-15"),
                        payloadJson = fixedTermPayload(paymentCount = 2)
                    )
                )
            )
        )

        val response = service.listOccurrences(
            workspaceId = "ws-1",
            fromInclusive = LocalDate.parse("2026-08-01"),
            toInclusive = LocalDate.parse("2026-12-31")
        )

        assertEquals(emptyList<Any>(), response.items)
    }

    @Test
    fun existingOngoingRecordsStillProduceOccurrences() {
        val service = RegularSpendingsService(
            FakeRecordsRepository(
                listOf(
                    sampleRegularSpending(
                        recordId = "rs-1",
                        eventDate = LocalDate.parse("2026-06-01"),
                        payloadJson = """
                            {
                              "name": "Gym",
                              "startDate": "2026-06-01",
                              "cadence": "MONTHLY",
                              "amount": 29.99
                            }
                        """.trimIndent()
                    )
                )
            )
        )

        val response = service.listOccurrences(
            workspaceId = "ws-1",
            fromInclusive = LocalDate.parse("2026-06-01"),
            toInclusive = LocalDate.parse("2026-08-31")
        )

        assertEquals(
            listOf("2026-06-01", "2026-07-01", "2026-08-01"),
            response.items.map { it.payoutDate.toString() }
        )
    }

    private class FakeRecordsRepository(
        private val items: List<RecordItem>
    ) : WorkspaceRecordsRepositoryPort {
        override fun create(record: RecordItem) = throw UnsupportedOperationException()
        override fun update(record: RecordItem) = throw UnsupportedOperationException()
        override fun get(workspaceId: String, recordKey: String): RecordItem? = throw UnsupportedOperationException()
        override fun delete(workspaceId: String, recordKey: String) = throw UnsupportedOperationException()
        override fun deleteByWorkspaceId(workspaceId: String) = throw UnsupportedOperationException()
        override fun setTtlByWorkspaceId(workspaceId: String, ttlEpoch: Long) = throw UnsupportedOperationException()
        override fun clearTtlByWorkspaceId(workspaceId: String) = throw UnsupportedOperationException()
        override fun queryByWorkspaceRecordKeyPrefix(workspaceId: String, recordKeyPrefix: String): List<RecordItem> = items
        override fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem> = throw UnsupportedOperationException()
        override fun queryByQuarter(workspaceQuarter: String, recordType: RecordType?): List<RecordItem> = throw UnsupportedOperationException()
        override fun isConditionalFailure(ex: Exception): Boolean = false
    }

    private fun fixedTermPayload(paymentCount: Int): String =
        """
        {
          "name": "Installment",
          "startDate": "2026-06-15",
          "scheduleType": "FIXED_TERM",
          "paymentCount": $paymentCount,
          "amount": 120.00
        }
        """.trimIndent()

    private fun sampleRegularSpending(recordId: String, eventDate: LocalDate, payloadJson: String): RecordItem {
        val now = Instant.parse("2026-05-18T09:30:00Z")
        return RecordItem(
            workspaceId = "ws-1",
            recordKey = "REGULAR_SPENDING#$eventDate#$recordId",
            recordId = recordId,
            recordType = RecordType.REGULAR_SPENDING,
            eventDate = eventDate,
            payloadJson = JsonSupport.mapper.readTree(payloadJson).toString(),
            workspaceMonth = "WS#ws-1#M#${eventDate.toString().substring(0, 7)}",
            workspaceQuarter = "WS#ws-1#Q#2026-Q2",
            createdAt = now,
            updatedAt = now,
            createdBy = "user-1",
            updatedBy = "user-1"
        )
    }
}
