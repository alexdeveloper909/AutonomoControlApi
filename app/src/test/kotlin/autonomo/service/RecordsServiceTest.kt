package autonomo.service

import autonomo.config.JsonSupport
import autonomo.domain.BalanceAccount
import autonomo.domain.BalanceAccountKind
import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.Settings
import autonomo.model.RecordRequest
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.repository.WorkspaceSettingsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class RecordsServiceTest {
    @Test
    fun listByMonthCanSortByEventDateDescending() {
        val items = listOf(
            sampleItem(RecordType.EXPENSE, LocalDate.parse("2024-06-05"), "rec-1"),
            sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-2"),
            sampleItem(RecordType.STATE_PAYMENT, LocalDate.parse("2024-06-10"), "rec-3")
        )

        val repo = FakeRecordsRepository(itemsByMonth = mapOf("WS#ws-1#M#2024-06" to items))
        val service = RecordsService(repo)

        val response = service.listByMonth(
            workspaceId = "ws-1",
            month = YearMonth.parse("2024-06"),
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC)
        )

        assertEquals(listOf("rec-2", "rec-3", "rec-1"), response.items.map { it.recordId })
    }

    @Test
    fun listByMonthSupportsLimitAndNextToken() {
        val items = listOf(
            sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-1"),
            sampleItem(RecordType.EXPENSE, LocalDate.parse("2024-06-10"), "rec-2"),
            sampleItem(RecordType.TRANSFER, LocalDate.parse("2024-06-05"), "rec-3")
        )

        val repo = FakeRecordsRepository(itemsByMonth = mapOf("WS#ws-1#M#2024-06" to items))
        val service = RecordsService(repo)

        val firstPage = service.listByMonth(
            workspaceId = "ws-1",
            month = YearMonth.parse("2024-06"),
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 2)
        )

        assertEquals(listOf("rec-1", "rec-2"), firstPage.items.map { it.recordId })
        assertNotNull(firstPage.nextToken)

        val secondPage = service.listByMonth(
            workspaceId = "ws-1",
            month = YearMonth.parse("2024-06"),
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 2, nextToken = firstPage.nextToken)
        )

        assertEquals(listOf("rec-3"), secondPage.items.map { it.recordId })
        assertNull(secondPage.nextToken)
    }

    @Test
    fun listByMonthRejectsInvalidNextToken() {
        val items = listOf(
            sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-1")
        )

        val repo = FakeRecordsRepository(itemsByMonth = mapOf("WS#ws-1#M#2024-06" to items))
        val service = RecordsService(repo)

        assertThrows<InvalidNextTokenException> {
            service.listByMonth(
                workspaceId = "ws-1",
                month = YearMonth.parse("2024-06"),
                recordType = null,
                options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 1, nextToken = "nope")
            )
        }
    }

    @Test
    fun listByYearCanSortByEventDateDescending() {
        val invoice = sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-1")
        val expense = sampleItem(RecordType.EXPENSE, LocalDate.parse("2024-06-10"), "rec-2")
        val transfer = sampleItem(RecordType.TRANSFER, LocalDate.parse("2024-01-05"), "rec-3")

        val repo = FakeRecordsRepository(
            itemsByRecordKeyPrefix = mapOf(
                "INVOICE#2024-" to listOf(invoice),
                "EXPENSE#2024-" to listOf(expense),
                "TRANSFER#2024-" to listOf(transfer)
            )
        )
        val service = RecordsService(repo)

        val response = service.listByYear(
            workspaceId = "ws-1",
            year = 2024,
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC)
        )

        assertEquals(listOf("rec-1", "rec-2", "rec-3"), response.items.map { it.recordId })
    }

    @Test
    fun listByYearSupportsLimitAndNextToken() {
        val invoice = sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-1")
        val expense = sampleItem(RecordType.EXPENSE, LocalDate.parse("2024-06-10"), "rec-2")
        val transfer = sampleItem(RecordType.TRANSFER, LocalDate.parse("2024-01-05"), "rec-3")

        val repo = FakeRecordsRepository(
            itemsByRecordKeyPrefix = mapOf(
                "INVOICE#2024-" to listOf(invoice),
                "EXPENSE#2024-" to listOf(expense),
                "TRANSFER#2024-" to listOf(transfer)
            )
        )
        val service = RecordsService(repo)

        val firstPage = service.listByYear(
            workspaceId = "ws-1",
            year = 2024,
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 2)
        )

        assertEquals(listOf("rec-1", "rec-2"), firstPage.items.map { it.recordId })
        assertNotNull(firstPage.nextToken)

        val secondPage = service.listByYear(
            workspaceId = "ws-1",
            year = 2024,
            recordType = null,
            options = RecordsListOptions(sort = RecordsSort.EVENT_DATE_DESC, limit = 2, nextToken = firstPage.nextToken)
        )

        assertEquals(listOf("rec-3"), secondPage.items.map { it.recordId })
        assertNull(secondPage.nextToken)
    }

    @Test
    fun listByYearWithRecordTypeOnlyQueriesThatType() {
        val invoice = sampleItem(RecordType.INVOICE, LocalDate.parse("2024-06-20"), "rec-1")
        val repo = FakeRecordsRepository(itemsByRecordKeyPrefix = mapOf("INVOICE#2024-" to listOf(invoice)))
        val service = RecordsService(repo)

        service.listByYear(
            workspaceId = "ws-1",
            year = 2024,
            recordType = RecordType.INVOICE,
            options = RecordsListOptions()
        )

        assertEquals(listOf("INVOICE#2024-"), repo.queriedPrefixes)
    }

    @Test
    fun createBudgetRecordStoresNormalizedSpentPayload() {
        val repo = FakeRecordsRepository()
        val service = RecordsService(repo)
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "monthKey": "2026-03",
              "plannedSpend": 2000.00,
              "earned": 2500.00,
              "description": "March review"
            }
            """.trimIndent()
        )

        val response = service.createRecord(
            workspaceId = "ws-1",
            userId = "user-1",
            request = RecordRequest(recordType = RecordType.BUDGET, payload = payload)
        )

        val storedPayload = JsonSupport.mapper.readTree(repo.created.single().payloadJson)
        assertEquals(LocalDate.parse("2026-03-01"), response.eventDate)
        assertEquals(0, storedPayload.get("spent").decimalValue().compareTo(java.math.BigDecimal("2000.00")))
        assertEquals("March review", storedPayload.get("notes").asText())
        assertEquals(false, storedPayload.has("plannedSpend"))
    }

    @Test
    fun createBudgetRecordRejectsDuplicateMonth() {
        val existing = sampleItem(RecordType.BUDGET, LocalDate.parse("2026-03-01"), "rec-existing")
        val repo = FakeRecordsRepository(
            itemsByRecordKeyPrefix = mapOf("BUDGET#2026-03-01#" to listOf(existing))
        )
        val service = RecordsService(repo)
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "monthKey": "2026-03",
              "spent": 2000.00,
              "earned": 2500.00
            }
            """.trimIndent()
        )

        val error = assertThrows<RecordConflictException> {
            service.createRecord(
                workspaceId = "ws-1",
                userId = "user-1",
                request = RecordRequest(recordType = RecordType.BUDGET, payload = payload)
            )
        }

        assertEquals("Budget entry already exists for 2026-03", error.message)
    }

    @Test
    fun updateBudgetRecordRejectsMonthKeyChanges() {
        val existing = sampleItem(RecordType.BUDGET, LocalDate.parse("2026-03-01"), "rec-1")
        val repo = FakeRecordsRepository(itemsByGetKey = mapOf(existing.recordKey to existing))
        val service = RecordsService(repo)
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "monthKey": "2026-04",
              "spent": 2000.00,
              "earned": 2500.00
            }
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            service.updateRecord(
                workspaceId = "ws-1",
                userId = "user-1",
                recordType = RecordType.BUDGET,
                eventDate = LocalDate.parse("2026-03-01"),
                recordId = "rec-1",
                request = RecordRequest(recordType = RecordType.BUDGET, payload = payload)
            )
        }

        assertEquals("Budget month cannot be changed", error.message)
    }

    @Test
    fun createFixedTermRegularSpendingStoresNormalizedPayload() {
        val repo = FakeRecordsRepository()
        val service = RecordsService(repo)
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "name": "Credit card installment",
              "startDate": "2026-06-15",
              "scheduleType": "FIXED_TERM",
              "paymentCount": 6,
              "amount": 120.00
            }
            """.trimIndent()
        )

        val response = service.createRecord(
            workspaceId = "ws-1",
            userId = "user-1",
            request = RecordRequest(recordType = RecordType.REGULAR_SPENDING, payload = payload)
        )

        val storedPayload = JsonSupport.mapper.readTree(repo.created.single().payloadJson)
        assertEquals(LocalDate.parse("2026-06-15"), response.eventDate)
        assertEquals("FIXED_TERM", storedPayload.get("scheduleType").asText())
        assertEquals(6, storedPayload.get("paymentCount").asInt())
        assertEquals(false, storedPayload.has("cadence"))
    }

    @Test
    fun updateFixedTermRegularSpendingStoresNormalizedPayload() {
        val existing = sampleItem(RecordType.REGULAR_SPENDING, LocalDate.parse("2026-06-15"), "rec-1")
        val repo = FakeRecordsRepository(itemsByGetKey = mapOf(existing.recordKey to existing))
        val service = RecordsService(repo)
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "name": "Updated installment",
              "startDate": "2026-06-15",
              "scheduleType": "FIXED_TERM",
              "paymentCount": 10,
              "amount": 99.00
            }
            """.trimIndent()
        )

        val response = service.updateRecord(
            workspaceId = "ws-1",
            userId = "user-1",
            recordType = RecordType.REGULAR_SPENDING,
            eventDate = LocalDate.parse("2026-06-15"),
            recordId = "rec-1",
            request = RecordRequest(recordType = RecordType.REGULAR_SPENDING, payload = payload)
        )

        val storedPayload = JsonSupport.mapper.readTree(repo.updated.single().payloadJson)
        assertEquals(LocalDate.parse("2026-06-15"), response!!.eventDate)
        assertEquals("FIXED_TERM", storedPayload.get("scheduleType").asText())
        assertEquals(10, storedPayload.get("paymentCount").asInt())
        assertEquals(false, storedPayload.has("cadence"))
    }

    @Test
    fun createInvalidFixedTermRegularSpendingFails() {
        val service = RecordsService(FakeRecordsRepository())
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "name": "Credit card installment",
              "startDate": "2026-06-15",
              "scheduleType": "FIXED_TERM",
              "cadence": "YEARLY",
              "paymentCount": 6,
              "amount": 120.00
            }
            """.trimIndent()
        )

        assertThrows<IllegalArgumentException> {
            service.createRecord(
                workspaceId = "ws-1",
                userId = "user-1",
                request = RecordRequest(recordType = RecordType.REGULAR_SPENDING, payload = payload)
            )
        }
    }

    @Test
    fun createInternalTransferValidatesAccountsAndStoresInternalShape() {
        val repo = FakeRecordsRepository()
        val service = RecordsService(repo, FakeSettingsRepository(accountSettings()))
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "movementType": "InternalTransfer",
              "fromAccountId": "main",
              "toAccountId": "cash",
              "amount": 300.00,
              "note": "ATM"
            }
            """.trimIndent()
        )

        val response = service.createRecord(
            workspaceId = "ws-1",
            userId = "user-1",
            request = RecordRequest(recordType = RecordType.TRANSFER, payload = payload)
        )

        val storedPayload = JsonSupport.mapper.readTree(repo.created.single().payloadJson)
        assertEquals(LocalDate.parse("2026-06-17"), response.eventDate)
        assertEquals("InternalTransfer", storedPayload.get("movementType").asText())
        assertEquals("cash", storedPayload.get("toAccountId").asText())
        assertEquals(false, storedPayload.has("operation"))
    }

    @Test
    fun createInternalTransferRejectsSameSourceAndTarget() {
        val service = RecordsService(FakeRecordsRepository(), FakeSettingsRepository(accountSettings()))
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "movementType": "InternalTransfer",
              "fromAccountId": "main",
              "toAccountId": "main",
              "amount": 300.00
            }
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            service.createRecord(
                workspaceId = "ws-1",
                userId = "user-1",
                request = RecordRequest(recordType = RecordType.TRANSFER, payload = payload)
            )
        }

        assertEquals("Internal transfer accounts must be different", error.message)
    }

    @Test
    fun updateTransferCanMoveDateWhilePreservingRecordIdentityAndCreationMetadata() {
        val existing = transferItem(
            eventDate = LocalDate.parse("2026-06-17"),
            recordId = "rec-1",
            payloadJson = """
                {
                  "date": "2026-06-17",
                  "operation": "Outflow",
                  "amount": 300.00,
                  "accountId": "main"
                }
            """.trimIndent()
        )
        val repo = FakeRecordsRepository(itemsByGetKey = mapOf(existing.recordKey to existing))
        val service = RecordsService(repo, FakeSettingsRepository(accountSettings()))
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-18",
              "movementType": "InternalTransfer",
              "fromAccountId": "main",
              "toAccountId": "cash",
              "amount": 300.00
            }
            """.trimIndent()
        )

        val response = service.updateRecord(
            workspaceId = "ws-1",
            userId = "user-2",
            recordType = RecordType.TRANSFER,
            eventDate = LocalDate.parse("2026-06-17"),
            recordId = "rec-1",
            request = RecordRequest(recordType = RecordType.TRANSFER, payload = payload)
        )

        val moved = repo.created.single()
        assertEquals("TRANSFER#2026-06-18#rec-1", moved.recordKey)
        assertEquals("rec-1", moved.recordId)
        assertEquals(existing.createdAt, moved.createdAt)
        assertEquals(existing.createdBy, moved.createdBy)
        assertEquals(listOf(existing.recordKey), repo.deletedKeys)
        assertEquals("TRANSFER#2026-06-18#rec-1", response!!.recordKey)
    }

    @Test
    fun updateTransferCanChangeInternalTransferIntoExternalInflow() {
        val existing = transferItem(
            eventDate = LocalDate.parse("2026-06-17"),
            recordId = "rec-1",
            payloadJson = """
                {
                  "date": "2026-06-17",
                  "movementType": "InternalTransfer",
                  "fromAccountId": "main",
                  "toAccountId": "cash",
                  "amount": 300.00
                }
            """.trimIndent()
        )
        val repo = FakeRecordsRepository(itemsByGetKey = mapOf(existing.recordKey to existing))
        val service = RecordsService(repo, FakeSettingsRepository(accountSettings()))
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "operation": "Inflow",
              "accountId": "cash",
              "amount": 300.00
            }
            """.trimIndent()
        )

        service.updateRecord(
            workspaceId = "ws-1",
            userId = "user-2",
            recordType = RecordType.TRANSFER,
            eventDate = LocalDate.parse("2026-06-17"),
            recordId = "rec-1",
            request = RecordRequest(recordType = RecordType.TRANSFER, payload = payload)
        )

        val storedPayload = JsonSupport.mapper.readTree(repo.updated.single().payloadJson)
        assertEquals("Inflow", storedPayload.get("operation").asText())
        assertEquals("cash", storedPayload.get("accountId").asText())
        assertEquals(false, storedPayload.has("movementType"))
    }

    @Test
    fun updateTransferRejectsNewArchivedAccountReference() {
        val existing = transferItem(
            eventDate = LocalDate.parse("2026-06-17"),
            recordId = "rec-1",
            payloadJson = """
                {
                  "date": "2026-06-17",
                  "operation": "Outflow",
                  "amount": 20.00,
                  "accountId": "main"
                }
            """.trimIndent()
        )
        val repo = FakeRecordsRepository(itemsByGetKey = mapOf(existing.recordKey to existing))
        val settings = accountSettings(
            cashAccount = BalanceAccount(
                accountId = "cash",
                kind = BalanceAccountKind.CASH,
                name = "Cash",
                openingBalance = Money.ZERO,
                openingDate = LocalDate.parse("2026-01-01"),
                archivedAt = LocalDate.parse("2026-06-01")
            )
        )
        val service = RecordsService(repo, FakeSettingsRepository(settings))
        val payload = JsonSupport.mapper.readTree(
            """
            {
              "date": "2026-06-17",
              "operation": "Outflow",
              "accountId": "cash",
              "amount": 20.00
            }
            """.trimIndent()
        )

        val error = assertThrows<IllegalArgumentException> {
            service.updateRecord(
                workspaceId = "ws-1",
                userId = "user-2",
                recordType = RecordType.TRANSFER,
                eventDate = LocalDate.parse("2026-06-17"),
                recordId = "rec-1",
                request = RecordRequest(recordType = RecordType.TRANSFER, payload = payload)
            )
        }

        assertEquals("Balance movement cannot create a new archived account reference", error.message)
    }

    private class FakeRecordsRepository(
        private val itemsByMonth: Map<String, List<RecordItem>> = emptyMap(),
        private val itemsByQuarter: Map<String, List<RecordItem>> = emptyMap(),
        private val itemsByRecordKeyPrefix: Map<String, List<RecordItem>> = emptyMap(),
        private val itemsByGetKey: Map<String, RecordItem> = emptyMap()
    ) : WorkspaceRecordsRepositoryPort {
        val queriedPrefixes = mutableListOf<String>()
        val created = mutableListOf<RecordItem>()
        val updated = mutableListOf<RecordItem>()
        val deletedKeys = mutableListOf<String>()

        override fun create(record: RecordItem) {
            created += record
        }

        override fun update(record: RecordItem) {
            updated += record
        }

        override fun get(workspaceId: String, recordKey: String): RecordItem? = itemsByGetKey[recordKey]
        override fun delete(workspaceId: String, recordKey: String) {
            deletedKeys += recordKey
        }
        override fun deleteByWorkspaceId(workspaceId: String) = throw UnsupportedOperationException()
        override fun setTtlByWorkspaceId(workspaceId: String, ttlEpoch: Long) = throw UnsupportedOperationException()
        override fun clearTtlByWorkspaceId(workspaceId: String) = throw UnsupportedOperationException()

        override fun queryByWorkspaceRecordKeyPrefix(workspaceId: String, recordKeyPrefix: String): List<RecordItem> {
            queriedPrefixes.add(recordKeyPrefix)
            return itemsByRecordKeyPrefix[recordKeyPrefix].orEmpty()
        }

        override fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem> =
            itemsByMonth[workspaceMonth].orEmpty()

        override fun queryByQuarter(workspaceQuarter: String, recordType: RecordType?): List<RecordItem> =
            itemsByQuarter[workspaceQuarter].orEmpty()

        override fun isConditionalFailure(ex: Exception): Boolean = false
    }

    private class FakeSettingsRepository(
        private val settings: Settings?
    ) : WorkspaceSettingsRepositoryPort {
        override fun getSettings(workspaceId: String): Settings? = settings
        override fun putSettings(workspaceId: String, settings: Settings, updatedBy: String) = Unit
        override fun deleteSettings(workspaceId: String) = Unit
        override fun setTtl(workspaceId: String, ttlEpoch: Long) = Unit
        override fun clearTtl(workspaceId: String) = Unit
    }

    private fun sampleItem(recordType: RecordType, eventDate: LocalDate, recordId: String): RecordItem {
        val now = Instant.parse("2024-06-21T09:30:00Z")
        return RecordItem(
            workspaceId = "ws-1",
            recordKey = "${recordType.name}#$eventDate#$recordId",
            recordId = recordId,
            recordType = recordType,
            eventDate = eventDate,
            payloadJson = defaultPayload(recordType, eventDate),
            workspaceMonth = "WS#ws-1#M#2024-06",
            workspaceQuarter = "WS#ws-1#Q#2024-Q2",
            createdAt = now,
            updatedAt = now,
            createdBy = "user-1",
            updatedBy = "user-1"
        )
    }

    private fun transferItem(eventDate: LocalDate, recordId: String, payloadJson: String): RecordItem {
        val now = Instant.parse("2024-06-21T09:30:00Z")
        return RecordItem(
            workspaceId = "ws-1",
            recordKey = "TRANSFER#$eventDate#$recordId",
            recordId = recordId,
            recordType = RecordType.TRANSFER,
            eventDate = eventDate,
            payloadJson = payloadJson,
            workspaceMonth = "WS#ws-1#M#${eventDate.toString().substring(0, 7)}",
            workspaceQuarter = "WS#ws-1#Q#2026-Q2",
            createdAt = now,
            updatedAt = now,
            createdBy = "user-1",
            updatedBy = "user-1"
        )
    }

    private fun accountSettings(
        cashAccount: BalanceAccount = BalanceAccount(
            accountId = "cash",
            kind = BalanceAccountKind.CASH,
            name = "Cash",
            openingBalance = Money.ZERO,
            openingDate = LocalDate.parse("2026-01-01")
        )
    ): Settings =
        Settings(
            year = 2026,
            startDate = LocalDate.parse("2026-01-01"),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO,
            balanceAccounts = listOf(
                BalanceAccount.main(Money.eur("1000.00"), LocalDate.parse("2026-01-01")),
                cashAccount
            )
        )

    private fun defaultPayload(recordType: RecordType, eventDate: LocalDate): String =
        when (recordType) {
            RecordType.REGULAR_SPENDING ->
                """
                {
                  "name": "Spending",
                  "startDate": "$eventDate",
                  "cadence": "MONTHLY",
                  "amount": 10.00
                }
                """.trimIndent()
            else -> "{}"
        }
}
