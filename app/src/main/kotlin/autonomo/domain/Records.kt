package autonomo.domain

import java.math.BigDecimal
import java.time.LocalDate

data class Invoice(
    val invoiceDate: LocalDate,
    val number: String,
    val client: String,
    val baseExclVat: Money,
    val ivaRate: IvaRate,
    val retencion: RetencionRate,
    val paymentDate: LocalDate? = null,
    val amountReceivedOverride: Money? = null
) {
    init {
        require(baseExclVat.amount >= BigDecimal.ZERO) { "Invoice.baseExclVat must be >= 0" }
    }

    val ivaAmount: Money get() = ivaRate.toRate().of(baseExclVat)
    val irpfWithheld: Money get() = -(retencion.toRate().of(baseExclVat))
    val totalReceivable: Money get() = baseExclVat + ivaAmount + irpfWithheld
    val monthKey: MonthKey get() = MonthKey.from((paymentDate ?: invoiceDate).withDayOfMonth(1))
    val quarterKey: QuarterKey get() = QuarterKey.from(monthKey)
    val amountReceived: Money get() = amountReceivedOverride ?: totalReceivable
}

data class Expense(
    val documentDate: LocalDate,
    val vendor: String,
    val category: String,
    val baseExclVat: Money,
    val ivaRate: IvaRate,
    val vatRecoverableFlag: Boolean,
    val deductibleShare: Percentage,
    val paymentDate: LocalDate? = null,
    val amountPaidOverride: Money? = null
) {
    init {
        require(baseExclVat.amount >= BigDecimal.ZERO) { "Expense.baseExclVat must be >= 0" }
        require(deductibleShare.value >= BigDecimal.ZERO && deductibleShare.value <= BigDecimal.ONE) {
            "Expense.deductibleShare must be in [0,1]"
        }
    }

    val ivaAmount: Money get() = ivaRate.toRate().of(baseExclVat)
    val deductibleBase: Money get() = deductibleShare.of(baseExclVat)
    val vatRecoverable: Money get() = if (vatRecoverableFlag) ivaAmount else Money.ZERO
    val totalPayable: Money get() = baseExclVat + ivaAmount

    val monthKey: MonthKey get() = MonthKey.from((paymentDate ?: documentDate).withDayOfMonth(1))
    val quarterKey: QuarterKey get() = QuarterKey.from(monthKey)

    val amountPaid: Money get() = amountPaidOverride ?: totalPayable
}

data class StatePayment(
    val paymentDate: LocalDate,
    val type: StatePaymentType,
    val amount: Money
) {
    init {
        require(amount.amount >= BigDecimal.ZERO) { "StatePayment.amount must be >= 0" }
    }

    val monthKey: MonthKey get() = MonthKey.from(paymentDate.withDayOfMonth(1))
    val quarterKey: QuarterKey get() = QuarterKey.from(monthKey)
}

data class Transfer(
    val date: LocalDate,
    val operation: TransferOp,
    val amount: Money,
    val note: String? = null
) {
    init {
        require(amount.amount >= BigDecimal.ZERO) { "Transfer.amount must be >= 0" }
    }

    val monthKey: MonthKey get() = MonthKey.from(date.withDayOfMonth(1))
    val quarterKey: QuarterKey get() = QuarterKey.from(monthKey)

    fun signed(): Money = when (operation) {
        TransferOp.Inflow -> amount
        TransferOp.Outflow -> -amount
    }
}

data class BudgetEntry(
    val monthKey: MonthKey,
    val plannedSpend: Money,
    val earned: Money,
    val description: String?,
    val budgetGoal: String?
)

enum class IvaRate(val decimal: BigDecimal) {
    ZERO(BigDecimal("0.00")),
    SUPER_REDUCED(BigDecimal("0.04")),
    REDUCED(BigDecimal("0.10")),
    STANDARD(BigDecimal("0.21"));

    fun toRate(): Rate = Rate(decimal)
}

enum class RetencionRate(val decimal: BigDecimal) {
    ZERO(BigDecimal("0.00")),
    NEW_PROFESSIONAL(BigDecimal("0.07")),
    STANDARD(BigDecimal("0.15"));

    fun toRate(): Rate = Rate(decimal)
}

enum class StatePaymentType {
    Modelo303,
    Modelo130,
    SeguridadSocial,
    RentaAnual,
    Other
}

enum class TransferOp { Inflow, Outflow }
