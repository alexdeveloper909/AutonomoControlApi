package autonomo.service

import autonomo.domain.BalanceAccount
import autonomo.domain.BalanceAccountKind
import autonomo.domain.Money
import autonomo.domain.Rate
import autonomo.domain.Settings
import autonomo.repository.WorkspaceSettingsRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class WorkspaceSettingsServiceTest {
    @Test
    fun putSettingsPreservesExistingBalanceAccountsWhenIncomingPayloadOmittedThem() {
        val existing = settings(
            balanceAccounts = listOf(
                BalanceAccount.main(Money.eur("100.00"), LocalDate.parse("2026-01-01")),
                cashAccount(name = "Cash")
            )
        )
        val repo = FakeSettingsRepository(existing)
        val service = WorkspaceSettingsService(repo)

        service.putSettings(
            workspaceId = "ws-1",
            settings = settings(balanceAccounts = null),
            updatedBy = "user-1",
            preserveExistingBalanceAccounts = true
        )

        assertEquals(existing.balanceAccounts, repo.savedSettings!!.balanceAccounts)
    }

    @Test
    fun putSettingsAllowsRenamingExistingBalanceAccountOnly() {
        val existingCash = cashAccount(name = "Cash")
        val repo = FakeSettingsRepository(
            settings(
                balanceAccounts = listOf(
                    BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                    existingCash
                )
            )
        )
        val service = WorkspaceSettingsService(repo)

        service.putSettings(
            workspaceId = "ws-1",
            settings = settings(
                balanceAccounts = listOf(
                    BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                    existingCash.copy(name = "Wallet")
                )
            ),
            updatedBy = "user-1",
            preserveExistingBalanceAccounts = false
        )

        assertEquals("Wallet", repo.savedSettings!!.balanceAccounts!!.first { it.accountId == "cash" }.name)
    }

    @Test
    fun putSettingsRejectsRenamePayloadChangingOpeningBalance() {
        val existingCash = cashAccount(name = "Cash")
        val repo = FakeSettingsRepository(
            settings(
                balanceAccounts = listOf(
                    BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                    existingCash
                )
            )
        )
        val service = WorkspaceSettingsService(repo)

        val error = assertThrows<IllegalArgumentException> {
            service.putSettings(
                workspaceId = "ws-1",
                settings = settings(
                    balanceAccounts = listOf(
                        BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                        existingCash.copy(name = "Wallet", openingBalance = Money.eur("25.00"))
                    )
                ),
                updatedBy = "user-1",
                preserveExistingBalanceAccounts = false
            )
        }

        assertEquals("BalanceAccount.openingBalance cannot change during rename", error.message)
    }

    @Test
    fun putSettingsAllowsArchivingAndClosingNonMainAccount() {
        val existingCash = cashAccount(name = "Cash")
        val archivedAt = LocalDate.parse("2026-06-01")
        val closedAt = LocalDate.parse("2026-06-02")
        val repo = FakeSettingsRepository(
            settings(
                balanceAccounts = listOf(
                    BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                    existingCash
                )
            )
        )
        val service = WorkspaceSettingsService(repo)

        service.putSettings(
            workspaceId = "ws-1",
            settings = settings(
                balanceAccounts = listOf(
                    BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                    existingCash.copy(archivedAt = archivedAt, closedAt = closedAt)
                )
            ),
            updatedBy = "user-1",
            preserveExistingBalanceAccounts = false
        )

        val savedCash = repo.savedSettings!!.balanceAccounts!!.first { it.accountId == "cash" }
        assertEquals(archivedAt, savedCash.archivedAt)
        assertEquals(closedAt, savedCash.closedAt)
    }

    @Test
    fun putSettingsRejectsClearingArchivedAccount() {
        val archivedCash = cashAccount(name = "Cash").copy(archivedAt = LocalDate.parse("2026-06-01"))
        val repo = FakeSettingsRepository(
            settings(
                balanceAccounts = listOf(
                    BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                    archivedCash
                )
            )
        )
        val service = WorkspaceSettingsService(repo)

        val error = assertThrows<IllegalArgumentException> {
            service.putSettings(
                workspaceId = "ws-1",
                settings = settings(
                    balanceAccounts = listOf(
                        BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01")),
                        archivedCash.copy(archivedAt = null)
                    )
                ),
                updatedBy = "user-1",
                preserveExistingBalanceAccounts = false
            )
        }

        assertEquals("BalanceAccount.archivedAt cannot be cleared or changed", error.message)
    }

    @Test
    fun putSettingsRejectsArchivingMainAccount() {
        val main = BalanceAccount.main(Money.ZERO, LocalDate.parse("2026-01-01"))
        val repo = FakeSettingsRepository(
            settings(
                balanceAccounts = listOf(
                    main,
                    cashAccount(name = "Cash")
                )
            )
        )
        val service = WorkspaceSettingsService(repo)

        val error = assertThrows<IllegalArgumentException> {
            service.putSettings(
                workspaceId = "ws-1",
                settings = settings(
                    balanceAccounts = listOf(
                        main.copy(archivedAt = LocalDate.parse("2026-06-01")),
                        cashAccount(name = "Cash")
                    )
                ),
                updatedBy = "user-1",
                preserveExistingBalanceAccounts = false
            )
        }

        assertEquals("Main balance account cannot be archived or closed", error.message)
    }

    @Test
    fun putSettingsRejectsInvalidBalanceAccounts() {
        val repo = FakeSettingsRepository(null)
        val service = WorkspaceSettingsService(repo)

        val error = assertThrows<IllegalArgumentException> {
            service.putSettings(
                workspaceId = "ws-1",
                settings = settings(
                    balanceAccounts = listOf(
                        cashAccount(name = "Cash")
                    )
                ),
                updatedBy = "user-1",
                preserveExistingBalanceAccounts = false
            )
        }

        assertEquals("Balance accounts must include exactly one Main account", error.message)
    }

    private class FakeSettingsRepository(
        private val existingSettings: Settings?
    ) : WorkspaceSettingsRepositoryPort {
        var savedSettings: Settings? = null

        override fun getSettings(workspaceId: String): Settings? = existingSettings

        override fun putSettings(workspaceId: String, settings: Settings, updatedBy: String) {
            savedSettings = settings
        }

        override fun deleteSettings(workspaceId: String) = Unit
        override fun setTtl(workspaceId: String, ttlEpoch: Long) = Unit
        override fun clearTtl(workspaceId: String) = Unit
    }

    private fun settings(balanceAccounts: List<BalanceAccount>?): Settings =
        Settings(
            year = 2026,
            startDate = LocalDate.parse("2026-01-01"),
            ivaStd = Rate.fromDecimal("0.21"),
            irpfRate = Rate.fromDecimal("0.20"),
            obligacion130 = true,
            openingBalance = Money.ZERO,
            balanceAccounts = balanceAccounts
        )

    private fun cashAccount(name: String): BalanceAccount =
        BalanceAccount(
            accountId = "cash",
            kind = BalanceAccountKind.CASH,
            name = name,
            openingBalance = Money.ZERO,
            openingDate = LocalDate.parse("2026-01-01")
        )
}
