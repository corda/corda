package core

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Amount represents a positive quantity of currency, measured in pennies, which are the smallest representable units.
 *
 * Note that "pennies" are not necessarily 1/100ths of a currency unit, but are the actual smallest amount used in
 * whatever currency the amount represents.
 *
 * Amounts of different currencies *do not mix* and attempting to add or subtract two amounts of different currencies
 * will throw [IllegalArgumentException]. Amounts may not be negative. Amounts are represented internally using a signed
 * 64 bit value, therefore, the maximum expressable amount is 2^63 - 1 == Long.MAX_VALUE. Addition, subtraction and
 * multiplication are overflow checked and will throw [ArithmeticException] if the operation would have caused integer
 * overflow.
 *
 * TODO: It may make sense to replace this with convenience extensions over the JSR 354 MonetaryAmount interface
 * TODO: Should amount be abstracted to cover things like quantities of a stock, bond, commercial paper etc? Probably.
 * TODO: Think about how positive-only vs positive-or-negative amounts can be represented in the type system.
 */
data class Amount(val pennies: Long, val currency: Currency) : Comparable<Amount> {
    init {
        // Negative amounts are of course a vital part of any ledger, but negative values are only valid in certain
        // contexts: you cannot send a negative amount of cash, but you can (sometimes) have a negative balance.
        // If you want to express a negative amount, for now, use a long.
        require(pennies >= 0) { "Negative amounts are not allowed: $pennies" }
    }

    constructor(amount: BigDecimal, currency: Currency) : this(amount.toLong(), currency)

    operator fun plus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(Math.addExact(pennies, other.pennies), currency)
    }

    operator fun minus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(Math.subtractExact(pennies, other.pennies), currency)
    }

    private fun checkCurrency(other: Amount) {
        require(other.currency == currency) { "Currency mismatch: ${other.currency} vs $currency" }
    }

    operator fun div(other: Long): Amount = Amount(pennies / other, currency)
    operator fun times(other: Long): Amount = Amount(Math.multiplyExact(pennies, other), currency)
    operator fun div(other: Int): Amount = Amount(pennies / other, currency)
    operator fun times(other: Int): Amount = Amount(Math.multiplyExact(pennies, other.toLong()), currency)
    
    override fun toString(): String = (BigDecimal(pennies).divide(BigDecimal(100))).setScale(2).toPlainString()

    override fun compareTo(other: Amount): Int {
        checkCurrency(other)
        return pennies.compareTo(other.pennies)
    }
}

fun Iterable<Amount>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()
fun Iterable<Amount>.sumOrThrow() = reduce { left, right -> left + right }
fun Iterable<Amount>.sumOrZero(currency: Currency) = if (iterator().hasNext()) sumOrThrow() else Amount(0, currency)

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Interest rate fixes
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/** A [FixOf] identifies the question side of a fix: what day, tenor and type of fix ("LIBOR", "EURIBOR" etc) */
data class FixOf(val name: String, val forDay: LocalDate, val ofTenor: Tenor)

/** A [Fix] represents a named interest rate, on a given day, for a given duration. It can be embedded in a tx. */
data class Fix(val of: FixOf, val value: BigDecimal) : CommandData

/** Represents a textual expression of e.g. a formula */
@JsonDeserialize(using = ExpressionDeserializer::class)
@JsonSerialize(using = ExpressionSerializer::class)
data class Expression(val expr: String)

object ExpressionSerializer : JsonSerializer<Expression>() {
    override fun serialize(expr: Expression, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(expr.expr)
    }
}

object ExpressionDeserializer : JsonDeserializer<Expression>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Expression {
        return Expression(parser.text)
    }
}

/** Placeholder class for the Tenor datatype - which is a standardised duration of time until maturity */
data class Tenor(val name: String) {
    private val amount: Int
    private val unit: TimeUnit

    init {
        if (name == "ON") {
            // Overnight
            amount = 1
            unit = TimeUnit.Day
        } else {
            val regex = """(\d+)([DMYW])""".toRegex()
            val match = regex.matchEntire(name)?.groupValues ?: throw IllegalArgumentException("Unrecognised tenor name: $name")

            amount = match[1].toInt()
            unit = TimeUnit.values().first { it.code == match[2] }
        }
    }

    fun daysToMaturity(startDate: LocalDate, calendar: BusinessCalendar): Int {
        val maturityDate = when (unit) {
            TimeUnit.Day -> startDate.plusDays(amount.toLong())
            TimeUnit.Week -> startDate.plusWeeks(amount.toLong())
            TimeUnit.Month -> startDate.plusMonths(amount.toLong())
            TimeUnit.Year -> startDate.plusYears(amount.toLong())
            else -> throw IllegalStateException("Invalid tenor time unit: $unit")
        }
        // Move date to the closest business day when it falls on a weekend/holiday
        val adjustedMaturityDate = calendar.applyRollConvention(maturityDate, DateRollConvention.ModifiedFollowing)
        val daysToMaturity = calculateDaysBetween(startDate, adjustedMaturityDate, DayCountBasisYear.Y360, DayCountBasisDay.DActual)

        return daysToMaturity.toInt()
    }

    override fun toString(): String = "$name"

    enum class TimeUnit(val code: String) {
        Day("D"), Week("W"), Month("M"), Year("Y")
    }
}

/**
 * Simple enum for returning accurals adjusted or unadjusted.
 * We don't actually do anything with this yet though, so it's ignored for now.
 */
enum class AccrualAdjustment {
    Adjusted, Unadjusted
}

/**
 * This is utilised in the [DateRollConvention] class to determine which way we should initially step when
 * finding a business day
 */
enum class DateRollDirection(val value: Long) { FORWARD(1), BACKWARD(-1) }

/**
 * This reflects what happens if a date on which a business event is supposed to happen actually falls upon a non-working day
 * Depending on the accounting requirement, we can move forward until we get to a business day, or backwards
 * There are some additional rules which are explained in the individual cases below
 */
enum class DateRollConvention {
    // direction() cannot be a val due to the throw in the Actual instance

    /** Don't roll the date, use the one supplied. */
    Actual {
        override fun direction(): DateRollDirection = throw UnsupportedOperationException("Direction is not relevant for convention Actual")
        override val isModified: Boolean = false
    },
    /** Following is the next business date from this one. */
    Following {
        override fun direction(): DateRollDirection = DateRollDirection.FORWARD
        override val isModified: Boolean = false
    },
    /**
     * "Modified following" is the next business date, unless it's in the next month, in which case use the preceeding
     * business date.
     */
    ModifiedFollowing {
        override fun direction(): DateRollDirection = DateRollDirection.FORWARD
        override val isModified: Boolean = true
    },
    /** Previous is the previous business date from this one. */
    Previous {
        override fun direction(): DateRollDirection = DateRollDirection.BACKWARD
        override val isModified: Boolean = false
    },
    /**
     * Modified previous is the previous business date, unless it's in the previous month, in which case use the next
     * business date.
     */
    ModifiedPrevious {
        override fun direction(): DateRollDirection = DateRollDirection.BACKWARD
        override val isModified: Boolean = true
    };

    abstract fun direction(): DateRollDirection
    abstract val isModified: Boolean
}


/**
 * This forms the day part of the "Day Count Basis" used for interest calculation.
 * Note that the first character cannot be a number (enum naming constraints), so we drop that
 * in the toString lest some people get confused.
 */
enum class DayCountBasisDay {
    // We have to prefix 30 etc with a letter due to enum naming constraints.
    D30,
    D30N, D30P, D30E, D30G, DActual, DActualJ, D30Z, D30F, DBus_SaoPaulo;

    override fun toString(): String {
        return super.toString().drop(1)
    }
}

/** This forms the year part of the "Day Count Basis" used for interest calculation. */
enum class DayCountBasisYear {
    // Ditto above comment for years.
    Y360,
    Y365F, Y365L, Y365Q, Y366, YActual, YActualA, Y365B, Y365, YISMA, YICMA, Y252;

    override fun toString(): String {
        return super.toString().drop(1)
    }
}

/** Whether the payment should be made before the due date, or after it. */
enum class PaymentRule {
    InAdvance, InArrears,
}

/**
 * Date offset that the fixing is done prior to the accrual start date.
 * Currently not used in the calculation.
 */
enum class DateOffset {
    // TODO: Definitely shouldn't be an enum, but let's leave it for now at T-2 is a convention.
    ZERO,
    TWODAYS,
}


/**
 * Frequency at which an event occurs - the enumerator also casts to an integer specifying the number of times per year
 * that would divide into (eg annually = 1, semiannual = 2, monthly = 12 etc).
 */
enum class Frequency(val annualCompoundCount: Int) {
    Annual(1) {
        override fun offset(d: LocalDate) = d.plusYears(1)
    },
    SemiAnnual(2) {
        override fun offset(d: LocalDate) = d.plusMonths(6)
    },
    Quarterly(4) {
        override fun offset(d: LocalDate) = d.plusMonths(3)
    },
    Monthly(12) {
        override fun offset(d: LocalDate) = d.plusMonths(1)
    },
    Weekly(52) {
        override fun offset(d: LocalDate) = d.plusWeeks(1)
    },
    BiWeekly(26) {
        override fun offset(d: LocalDate) = d.plusWeeks(2)
    };

    abstract fun offset(d: LocalDate): LocalDate
    // Daily() // Let's not worry about this for now.
}


fun LocalDate.isWorkingDay(accordingToCalendar: BusinessCalendar): Boolean = accordingToCalendar.isWorkingDay(this)

// TODO: Make Calendar data come from an oracle

/**
 * A business calendar performs date calculations that take into account national holidays and weekends. This is a
 * typical feature of financial contracts, in which a business may not want a payment event to fall on a day when
 * no staff are around to handle problems.
 */
open class BusinessCalendar private constructor(val calendars: Array<out String>, val holidayDates: List<LocalDate>) {
    class UnknownCalendar(name: String) : Exception("$name not found")

    companion object {
        val calendars = listOf("London", "NewYork")

        val TEST_CALENDAR_DATA = calendars.map {
            it to BusinessCalendar::class.java.getResourceAsStream("${it}HolidayCalendar.txt").bufferedReader().readText()
        }.toMap()

        /** Parses a date of the form YYYY-MM-DD, like 2016-01-10 for 10th Jan. */
        fun parseDateFromString(it: String) = LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)

        /** Returns a business calendar that combines all the named holiday calendars into one list of holiday dates. */
        fun getInstance(vararg calname: String) = BusinessCalendar(calname,
                calname.flatMap { (TEST_CALENDAR_DATA[it] ?: throw UnknownCalendar(it)).split(",") }.
                        toSet().
                        map { parseDateFromString(it) }.
                        toList().sorted()
        )

        /** Calculates an event schedule that moves events around to ensure they fall on working days. */
        fun createGenericSchedule(startDate: LocalDate,
                                  period: Frequency,
                                  calendar: BusinessCalendar = BusinessCalendar.getInstance(),
                                  dateRollConvention: DateRollConvention = DateRollConvention.Following,
                                  noOfAdditionalPeriods: Int = Integer.MAX_VALUE,
                                  endDate: LocalDate? = null,
                                  periodOffset: Int? = null): List<LocalDate> {
            val ret = ArrayList<LocalDate>()
            var ctr = 0
            var currentDate = startDate

            while (true) {
                currentDate = period.offset(currentDate)
                val scheduleDate = calendar.applyRollConvention(currentDate, dateRollConvention)

                if (periodOffset == null || periodOffset <= ctr)
                    ret.add(scheduleDate)
                ctr += 1
                // TODO: Fix addl period logic
                if ((ctr > noOfAdditionalPeriods ) || (currentDate >= endDate ?: currentDate ))
                    break
            }
            return ret
        }
    }

    override fun equals(other: Any?): Boolean = if (other is BusinessCalendar) {
        /** Note this comparison is OK as we ensure they are sorted in getInstance() */
        this.holidayDates == other.holidayDates
    } else {
        false
    }

    override fun hashCode(): Int {
        return this.holidayDates.hashCode()
    }

    open fun isWorkingDay(date: LocalDate): Boolean =
            when {
                date.dayOfWeek == DayOfWeek.SATURDAY -> false
                date.dayOfWeek == DayOfWeek.SUNDAY -> false
                holidayDates.contains(date) -> false
                else -> true
            }

    open fun applyRollConvention(testDate: LocalDate, dateRollConvention: DateRollConvention): LocalDate {
        if (dateRollConvention == DateRollConvention.Actual) return testDate

        var direction = dateRollConvention.direction().value
        var trialDate = testDate
        while (!isWorkingDay(trialDate)) {
            trialDate = trialDate.plusDays(direction)
        }

        // We've moved to the next working day in the right direction, but if we're using the "modified" date roll
        // convention and we've crossed into another month, reverse the direction instead to stay within the month.
        // Probably better explained here: http://www.investopedia.com/terms/m/modifiedfollowing.asp

        if (dateRollConvention.isModified && testDate.month != trialDate.month) {
            direction = -direction
            trialDate = testDate
            while (!isWorkingDay(trialDate)) {
                trialDate = trialDate.plusDays(direction)
            }
        }
        return trialDate
    }

    /**
     * Returns a date which is the inbound date plus/minus a given number of business days.
     * TODO: Make more efficient if necessary
     */
    fun moveBusinessDays(date: LocalDate, direction: DateRollDirection, i: Int): LocalDate {
        require(i >= 0)
        if ( i == 0 ) return date
        var retDate = date
        var ctr = 0
        while (ctr < i) {
            retDate = retDate.plusDays(direction.value)
            if (isWorkingDay(retDate)) ctr++
        }
        return retDate
    }
}

fun calculateDaysBetween(startDate: LocalDate,
                         endDate: LocalDate,
                         dcbYear: DayCountBasisYear,
                         dcbDay: DayCountBasisDay): Int {
    // Right now we are only considering Actual/360 and 30/360 .. We'll do the rest later.
    // TODO: The rest.
    return when {
        dcbDay == DayCountBasisDay.DActual -> (endDate.toEpochDay() - startDate.toEpochDay()).toInt()
        dcbDay == DayCountBasisDay.D30 && dcbYear == DayCountBasisYear.Y360 -> ((endDate.year - startDate.year) * 360.0 + (endDate.monthValue - startDate.monthValue) * 30.0 + endDate.dayOfMonth - startDate.dayOfMonth).toInt()
        else -> TODO("Can't calculate days using convention $dcbDay / $dcbYear")
    }
}
