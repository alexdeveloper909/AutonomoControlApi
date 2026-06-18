package autonomo.service

import autonomo.domain.Settings
import autonomo.domain.BalanceAccount
import autonomo.domain.DomainValidation
import autonomo.domain.validateBalanceAccountRename
import autonomo.repository.WorkspaceSettingsRepository
import autonomo.repository.WorkspaceSettingsRepositoryPort

interface WorkspaceSettingsServicePort {
    fun getSettings(workspaceId: String): Settings?
    fun putSettings(
        workspaceId: String,
        settings: Settings,
        updatedBy: String,
        preserveExistingBalanceAccounts: Boolean = false
    )
}

class WorkspaceSettingsService(
    private val repository: WorkspaceSettingsRepositoryPort = WorkspaceSettingsRepository()
) : WorkspaceSettingsServicePort {
    override fun getSettings(workspaceId: String): Settings? = repository.getSettings(workspaceId)

    override fun putSettings(
        workspaceId: String,
        settings: Settings,
        updatedBy: String,
        preserveExistingBalanceAccounts: Boolean
    ) {
        val existing = repository.getSettings(workspaceId)
        val merged = if (preserveExistingBalanceAccounts && existing?.balanceAccounts != null) {
            settings.copy(balanceAccounts = existing.balanceAccounts)
        } else {
            settings
        }

        validateSettingsUpdate(existing, merged)
        repository.putSettings(workspaceId, merged, updatedBy)
    }

    private fun validateSettingsUpdate(existing: Settings?, incoming: Settings) {
        DomainValidation.validateSettings(incoming)
        val existingAccounts = existing?.balanceAccounts.orEmpty().associateBy { it.accountId }
        val incomingAccounts = incoming.balanceAccounts.orEmpty()

        incomingAccounts.forEach { account ->
            val before = existingAccounts[account.accountId] ?: return@forEach
            validateBalanceAccountRename(before, account)
            require(before.accountId != BalanceAccount.MAIN_ACCOUNT_ID || (account.archivedAt == null && account.closedAt == null)) {
                "Main balance account cannot be archived or closed"
            }
            require(before.archivedAt == null || before.archivedAt == account.archivedAt) {
                "BalanceAccount.archivedAt cannot be cleared or changed"
            }
            require(before.closedAt == null || before.closedAt == account.closedAt) {
                "BalanceAccount.closedAt cannot be cleared or changed"
            }
            require(before.closedAt == null || account.archivedAt != null) {
                "Closed balance account must remain archived"
            }
        }

        val incomingIds = incomingAccounts.map { it.accountId }.toSet()
        val removedIds = existingAccounts.keys - incomingIds
        require(removedIds.isEmpty()) {
            "Existing balance accounts cannot be removed"
        }
    }
}
