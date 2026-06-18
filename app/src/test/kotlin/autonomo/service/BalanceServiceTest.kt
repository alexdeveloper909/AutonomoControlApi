package autonomo.service

import autonomo.domain.BalanceAccount
import autonomo.domain.BalanceAccountKind
import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.Settings
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.repository.WorkspaceSettingsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BalanceServiceTest {
    @Test
    fun getBalanceReturnsCurrentTotalsAndAbsoluteRunningBalancesForSelectedYear() {
        val records = listOf(
            transferItem(
                eventDate = LocalDate.parse("2025-12-31"),
                recordId = "rec-before",
                payloadJson = """
                    {
                      "date": "2025-12-31",
                      "operation": "Inflow",
                      "amount": 500.00,
                      "accountId": "main"
                    }
                """.trimIndent()
            ),
            transferItem(
                eventDate = LocalDate.parse("2026-06-17"),
                recordId = "rec-transfer",
                payloadJson = """
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
        )
        val service = BalanceService(
            recordsRepository = FakeRecordsRepository(records),
            settingsRepository = FakeSettingsRepository(accountSettings())
        )

        val response = service.getBalance("ws-1", year = 2026, accountId = "main")

        assertEquals(0, response.totalCurrentBalance.compareTo(java.math.BigDecimal("1500.00")))
        assertEquals(1, response.ledgerRows.size)
        val row = response.ledgerRows.single()
        assertEquals("InternalTransfer", row.movementType)
        assertEquals(0, row.selectedAccountImpact!!.compareTo(java.math.BigDecimal("-300.00")))
        assertEquals(0, row.totalBalanceImpact.compareTo(java.math.BigDecimal("0.00")))
        assertEquals(0, row.selectedAccountRunningBalance!!.compareTo(java.math.BigDecimal("1200.00")))
        assertEquals(0, row.totalRunningBalance.compareTo(java.math.BigDecimal("1500.00")))
    }

    private class FakeRecordsRepository(
        private val records: List<RecordItem>
    ) : WorkspaceRecordsRepositoryPort {
        override fun create(record: RecordItem) = Unit
        override fun update(record: RecordItem) = Unit
        override fun get(workspaceId: String, recordKey: String): RecordItem? = null
        override fun delete(workspaceId: String, recordKey: String) = Unit
        override fun deleteByWorkspaceId(workspaceId: String) = Unit
        override fun setTtlByWorkspaceId(workspaceId: String, ttlEpoch: Long) = Unit
        override fun clearTtlByWorkspaceId(workspaceId: String) = Unit
        override fun queryByWorkspaceRecordKeyPrefix(workspaceId: String, recordKeyPrefix: String): List<RecordItem> =
            records.filter { it.recordKey.startsWith(recordKeyPrefix) }
        override fun queryByMonth(workspaceMonth: String, recordType: RecordType?): List<RecordItem> = emptyList()
        override fun queryByQuarter(workspaceQuarter: String, recordType: RecordType?): List<RecordItem> = emptyList()
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

    private fun transferItem(eventDate: LocalDate, recordId: String, payloadJson: String): RecordItem {
        val now = Instant.parse("2026-06-18T09:30:00Z")
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

    private fun accountSettings(): Settings =
        Settings(
            year = 2026,
            startDate = LocalDate.parse("2026-01-01"),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO,
            balanceAccounts = listOf(
                BalanceAccount.main(Money.eur("1000.00"), LocalDate.parse("2026-01-01")),
                BalanceAccount(
                    accountId = "cash",
                    kind = BalanceAccountKind.CASH,
                    name = "Cash",
                    openingBalance = Money.ZERO,
                    openingDate = LocalDate.parse("2026-01-01")
                )
            )
        )
}
