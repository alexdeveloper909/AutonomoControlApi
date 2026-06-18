package autonomo.model

import java.math.BigDecimal
import java.time.LocalDate

data class BalanceResponse(
    val workspaceId: String,
    val asOfDate: LocalDate,
    val year: Int?,
    val selectedAccountId: String?,
    val totalCurrentBalance: BigDecimal,
    val accounts: List<BalanceAccountResponse>,
    val ledgerRows: List<BalanceLedgerRowResponse>,
    val nextPageToken: String? = null
)

data class BalanceAccountResponse(
    val accountId: String,
    val name: String,
    val kind: String,
    val archived: Boolean,
    val closedAt: LocalDate?,
    val currentBalance: BigDecimal
)

data class BalanceLedgerRowResponse(
    val recordKey: String,
    val recordId: String,
    val eventDate: LocalDate,
    val movementType: String,
    val operation: String? = null,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val amount: BigDecimal,
    val selectedAccountImpact: BigDecimal?,
    val totalBalanceImpact: BigDecimal,
    val selectedAccountRunningBalance: BigDecimal?,
    val totalRunningBalance: BigDecimal,
    val note: String? = null
)
