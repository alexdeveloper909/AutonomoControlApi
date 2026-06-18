package autonomo.service

import autonomo.domain.BalanceMovement
import autonomo.domain.DateRange
import autonomo.domain.Money
import autonomo.domain.balanceSnapshot
import autonomo.domain.normalizedBalanceAccounts
import autonomo.model.BalanceAccountResponse
import autonomo.model.BalanceLedgerRowResponse
import autonomo.model.BalanceResponse
import autonomo.model.RecordItem
import autonomo.model.RecordType
import autonomo.repository.WorkspaceRecordsRepository
import autonomo.repository.WorkspaceRecordsRepositoryPort
import autonomo.repository.WorkspaceSettingsRepository
import autonomo.repository.WorkspaceSettingsRepositoryPort
import autonomo.util.RecordPayloadParser
import java.time.LocalDate

interface BalanceServicePort {
    fun getBalance(workspaceId: String, year: Int?, accountId: String?): BalanceResponse
}

class BalanceService(
    private val recordsRepository: WorkspaceRecordsRepositoryPort = WorkspaceRecordsRepository(),
    private val settingsRepository: WorkspaceSettingsRepositoryPort = WorkspaceSettingsRepository()
) : BalanceServicePort {
    override fun getBalance(workspaceId: String, year: Int?, accountId: String?): BalanceResponse {
        val settings = settingsRepository.getSettings(workspaceId)
            ?: throw IllegalArgumentException("workspace settings not found")
        val accounts = settings.normalizedBalanceAccounts()
        if (accountId != null) {
            require(accounts.any { it.accountId == accountId }) { "accountId is invalid" }
        }

        val transferItems = recordsRepository.queryByWorkspaceRecordKeyPrefix(workspaceId, "${RecordType.TRANSFER.name}#")
        val movements = transferItems.map { item ->
            val payload = autonomo.config.JsonSupport.mapper.readTree(item.payloadJson)
            item to RecordPayloadParser.parseBalanceMovement(payload)
        }
        val visibleRange = year?.let {
            DateRange(LocalDate.of(it, 1, 1), LocalDate.of(it, 12, 31))
        }
        val snapshot = balanceSnapshot(settings, movements.map { it.second }, visibleRange)
        val visibleItems = movements
            .sortedWith(compareBy<Pair<RecordItem, BalanceMovement>> { it.second.date }.thenBy { movements.indexOf(it) })
            .filter { visibleRange == null || visibleRange.contains(it.second.date) }

        val rows = snapshot.ledgerRows.zip(visibleItems).mapNotNull { (row, visibleItem) ->
            val item = visibleItem.first
            if (accountId != null && accountId !in row.accountImpacts.keys) return@mapNotNull null
            val selectedImpact = accountId?.let { row.accountImpacts[it] ?: Money.ZERO }
            val selectedRunning = accountId?.let { row.accountRunningBalances[it] ?: Money.ZERO }
            when (val movement = row.movement) {
                is BalanceMovement.External -> BalanceLedgerRowResponse(
                    recordKey = item.recordKey,
                    recordId = item.recordId,
                    eventDate = item.eventDate,
                    movementType = "External",
                    operation = movement.operation.name,
                    accountId = movement.accountId,
                    amount = movement.amount.amount,
                    selectedAccountImpact = selectedImpact?.amount,
                    totalBalanceImpact = row.totalImpact.amount,
                    selectedAccountRunningBalance = selectedRunning?.amount,
                    totalRunningBalance = row.totalRunningBalance.amount,
                    note = movement.note
                )
                is BalanceMovement.InternalTransfer -> BalanceLedgerRowResponse(
                    recordKey = item.recordKey,
                    recordId = item.recordId,
                    eventDate = item.eventDate,
                    movementType = "InternalTransfer",
                    fromAccountId = movement.fromAccountId,
                    toAccountId = movement.toAccountId,
                    amount = movement.amount.amount,
                    selectedAccountImpact = selectedImpact?.amount,
                    totalBalanceImpact = row.totalImpact.amount,
                    selectedAccountRunningBalance = selectedRunning?.amount,
                    totalRunningBalance = row.totalRunningBalance.amount,
                    note = movement.note
                )
            }
        }

        return BalanceResponse(
            workspaceId = workspaceId,
            asOfDate = LocalDate.now(),
            year = year,
            selectedAccountId = accountId,
            totalCurrentBalance = snapshot.totalCurrentBalance.amount,
            accounts = snapshot.accounts.map { account ->
                BalanceAccountResponse(
                    accountId = account.accountId,
                    name = account.name,
                    kind = account.kind.name,
                    archived = account.archivedAt != null,
                    closedAt = account.closedAt,
                    currentBalance = snapshot.accountCurrentBalance(account.accountId).amount
                )
            },
            ledgerRows = rows
        )
    }
}
