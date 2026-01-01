package autonomo.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@JvmInline
value class MonthKey @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @JsonValue val value: String
) {
    init {
        require(MONTH_REGEX.matches(value)) { "MonthKey must be in YYYY-MM format" }
    }

    fun firstDay(): LocalDate = YearMonth.parse(value).atDay(1)

    override fun toString(): String = value

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")
        private val MONTH_REGEX = Regex("\\d{4}-\\d{2}")

        fun from(date: LocalDate): MonthKey = MonthKey(date.format(FORMATTER))
    }
}

@JvmInline
value class QuarterKey @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @JsonValue val value: String
) {
    init {
        require(QUARTER_REGEX.matches(value)) { "QuarterKey must be in YYYY-QN format" }
    }

    override fun toString(): String = value

    companion object {
        private val QUARTER_REGEX = Regex("\\d{4}-Q[1-4]")

        fun from(monthKey: MonthKey): QuarterKey {
            val yearMonth = YearMonth.parse(monthKey.value)
            val quarter = ((yearMonth.monthValue - 1) / 3) + 1
            return QuarterKey("${yearMonth.year}-Q$quarter")
        }
    }
}

@JvmInline
value class Money @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @JsonValue val amount: BigDecimal
) {
    operator fun plus(other: Money): Money = Money(amount.add(other.amount))
    operator fun minus(other: Money): Money = Money(amount.subtract(other.amount))
    operator fun unaryMinus(): Money = Money(amount.negate())

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
    }
}

data class Rate(val decimal: BigDecimal) {
    fun of(money: Money): Money = Money(money.amount.multiply(decimal))
}

@JvmInline
value class Percentage @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @JsonValue val value: BigDecimal
) {
    fun of(money: Money): Money = Money(money.amount.multiply(value))
}
