package net.corda.core.contracts

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.annotations.VisibleForTesting
import java.math.BigDecimal
import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Amount represents a positive quantity of some token (currency, asset, etc.), measured in quantity of the smallest
 * representable units. Note that quantity is not necessarily 1/100ths of a currency unit, but are the actual smallest
 * amount used in whatever underlying thing the amount represents.
 *
 * Amounts of different tokens *do not mix* and attempting to add or subtract two amounts of different currencies
 * will throw [IllegalArgumentException]. Amounts may not be negative. Amounts are represented internally using a signed
 * 64 bit value, therefore, the maximum expressable amount is 2^63 - 1 == Long.MAX_VALUE. Addition, subtraction and
 * multiplication are overflow checked and will throw [ArithmeticException] if the operation would have caused integer
 * overflow.
 *
 * TODO: It may make sense to replace this with convenience extensions over the JSR 354 MonetaryAmount interface,
 *       in particular for use during calculations. This may also resolve...
 * TODO: Think about how positive-only vs positive-or-negative amounts can be represented in the type system.
 * TODO: Add either a scaling factor, or a variant for use in calculations.
 *
 * @param T the type of the token, for example [Currency].
 */
data class Amount<T>(val quantity: Long, val token: T) : Comparable<Amount<T>> {
    companion object {
        /**
         * Build an amount from a decimal representation. For example, with an input of "12.34" GBP,
         * returns an amount with a quantity of "1234".
         *
         * @see Amount<Currency>.toDecimal
         */
        fun fromDecimal(quantity: BigDecimal, currency: Currency) : Amount<Currency> {
            val longQuantity = quantity.movePointRight(currency.defaultFractionDigits).toLong()
            return Amount(longQuantity, currency)
        }
    }

    init {
        // Negative amounts are of course a vital part of any ledger, but negative values are only valid in certain
        // contexts: you cannot send a negative amount of cash, but you can (sometimes) have a negative balance.
        // If you want to express a negative amount, for now, use a long.
        require(quantity >= 0) { "Negative amounts are not allowed: $quantity" }
    }

    /**
     * Construct the amount using the given decimal value as quantity. Any fractional part
     * is discarded. To convert and use the fractional part, see [fromDecimal].
     */
    constructor(quantity: BigDecimal, token: T) : this(quantity.toLong(), token)
    constructor(quantity: BigInteger, token: T) : this(quantity.toLong(), token)

    operator fun plus(other: Amount<T>): Amount<T> {
        checkCurrency(other)
        return Amount(Math.addExact(quantity, other.quantity), token)
    }

    operator fun minus(other: Amount<T>): Amount<T> {
        checkCurrency(other)
        return Amount(Math.subtractExact(quantity, other.quantity), token)
    }

    private fun checkCurrency(other: Amount<T>) {
        require(other.token == token) { "Currency mismatch: ${other.token} vs $token" }
    }

    operator fun div(other: Long): Amount<T> = Amount(quantity / other, token)
    operator fun times(other: Long): Amount<T> = Amount(Math.multiplyExact(quantity, other), token)
    operator fun div(other: Int): Amount<T> = Amount(quantity / other, token)
    operator fun times(other: Int): Amount<T> = Amount(Math.multiplyExact(quantity, other.toLong()), token)

    override fun toString(): String = (BigDecimal(quantity).divide(BigDecimal(100))).setScale(2).toPlainString() + " " + token

    override fun compareTo(other: Amount<T>): Int {
        checkCurrency(other)
        return quantity.compareTo(other.quantity)
    }
}

/**
 * Convert a currency [Amount] to a decimal representation. For example, with an amount with a quantity
 * of "1234" GBP, returns "12.34".
 *
 * @see Amount.Companion.fromDecimal
 */
fun Amount<Currency>.toDecimal() : BigDecimal = BigDecimal(quantity).movePointLeft(token.defaultFractionDigits)

fun <T> Iterable<Amount<T>>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()
fun <T> Iterable<Amount<T>>.sumOrThrow() = reduce { left, right -> left + right }
fun <T> Iterable<Amount<T>>.sumOrZero(currency: T) = if (iterator().hasNext()) sumOrThrow() else Amount(0, currency)

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

    override fun toString(): String = name

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
 * finding a business day.
 */
enum class DateRollDirection(val value: Long) { FORWARD(1), BACKWARD(-1) }

/**
 * This reflects what happens if a date on which a business event is supposed to happen actually falls upon a non-working day.
 * Depending on the accounting requirement, we can move forward until we get to a business day, or backwards.
 * There are some additional rules which are explained in the individual cases below.
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
 * Frequency at which an event occurs - the enumerator also casts to an integer specifying the number of times per year
 * that would divide into (eg annually = 1, semiannual = 2, monthly = 12 etc).
 */
@Suppress("unused")   // TODO: Revisit post-Vega and see if annualCompoundCount is still needed.
enum class Frequency(val annualCompoundCount: Int) {
    Annual(1) {
        override fun offset(d: LocalDate, n: Long) = d.plusYears(1 * n)
    },
    SemiAnnual(2) {
        override fun offset(d: LocalDate, n: Long) = d.plusMonths(6 * n)
    },
    Quarterly(4) {
        override fun offset(d: LocalDate, n: Long) = d.plusMonths(3 * n)
    },
    Monthly(12) {
        override fun offset(d: LocalDate, n: Long) = d.plusMonths(1 * n)
    },
    Weekly(52) {
        override fun offset(d: LocalDate, n: Long) = d.plusWeeks(1 * n)
    },
    BiWeekly(26) {
        override fun offset(d: LocalDate, n: Long) = d.plusWeeks(2 * n)
    },
    Daily(365) {
        override fun offset(d: LocalDate, n: Long) = d.plusDays(1 * n)
    };

    abstract fun offset(d: LocalDate, n: Long = 1): LocalDate
    // Daily() // Let's not worry about this for now.
}


@Suppress("unused") // This utility may be useful in future. TODO: Review before API stability guarantees in place.
fun LocalDate.isWorkingDay(accordingToCalendar: BusinessCalendar): Boolean = accordingToCalendar.isWorkingDay(this)

// TODO: Make Calendar data come from an oracle

/**
 * A business calendar performs date calculations that take into account national holidays and weekends. This is a
 * typical feature of financial contracts, in which a business may not want a payment event to fall on a day when
 * no staff are around to handle problems.
 */
open class BusinessCalendar private constructor(val holidayDates: List<LocalDate>) {
    class UnknownCalendar(name: String) : Exception("$name not found")

    companion object {
        val calendars = listOf("London", "NewYork")

        val TEST_CALENDAR_DATA = calendars.map {
            it to BusinessCalendar::class.java.getResourceAsStream("${it}HolidayCalendar.txt").bufferedReader().readText()
        }.toMap()

        /** Parses a date of the form YYYY-MM-DD, like 2016-01-10 for 10th Jan. */
        fun parseDateFromString(it: String) = LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)

        /** Returns a business calendar that combines all the named holiday calendars into one list of holiday dates. */
        fun getInstance(vararg calname: String) = BusinessCalendar(
                calname.flatMap { (TEST_CALENDAR_DATA[it] ?: throw UnknownCalendar(it)).split(",") }.
                        toSet().
                        map { parseDateFromString(it) }.
                        toList().sorted()
        )

        /** Calculates an event schedule that moves events around to ensure they fall on working days. */
        fun createGenericSchedule(startDate: LocalDate,
                                  period: Frequency,
                                  calendar: BusinessCalendar = getInstance(),
                                  dateRollConvention: DateRollConvention = DateRollConvention.Following,
                                  noOfAdditionalPeriods: Int = Integer.MAX_VALUE,
                                  endDate: LocalDate? = null,
                                  periodOffset: Int? = null): List<LocalDate> {
            val ret = ArrayList<LocalDate>()
            var ctr = 0
            var currentDate = startDate

            while (true) {
                currentDate = getOffsetDate(currentDate, period)
                if (periodOffset == null || periodOffset <= ctr)
                    ret.add(calendar.applyRollConvention(currentDate, dateRollConvention))
                ctr += 1
                // TODO: Fix addl period logic
                if ((ctr > noOfAdditionalPeriods) || (currentDate >= endDate ?: currentDate))
                    break
            }
            return ret
        }

        /** Calculates the date from @startDate moving forward @steps of time size @period. Does not apply calendar
         * logic / roll conventions.
         */
        fun getOffsetDate(startDate: LocalDate, period: Frequency, steps: Int = 1): LocalDate {
            if (steps == 0) return startDate
            return period.offset(startDate, steps.toLong())
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
        if (i == 0) return date
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

/**
 * Enum for the types of netting that can be applied to state objects. Exact behaviour
 * for each type of netting is left to the contract to determine.
 */
enum class NetType {
    /**
     * Close-out netting applies where one party is bankrupt or otherwise defaults (exact terms are contract specific),
     * and allows their counterparty to net obligations without requiring approval from all parties. For example, if
     * Bank A owes Bank B £1m, and Bank B owes Bank A £1m, in the case of Bank B defaulting this would enable Bank A
     * to net out the two obligations to zero, rather than being legally obliged to pay £1m without any realistic
     * expectation of the debt to them being paid. Realistically this is limited to bilateral netting, to simplify
     * determining which party must sign the netting transaction.
     */
    CLOSE_OUT,
    /**
     * "Payment" is used to refer to conventional netting, where all parties must confirm the netting transaction. This
     * can be a multilateral netting transaction, and may be created by a central clearing service.
     */
    PAYMENT
}

/**
 * Class representing a commodity, as an equivalent to the [Currency] class. This exists purely to enable the
 * [CommodityContract] contract, and is likely to change in future.
 *
 * @param commodityCode a unique code for the commodity. No specific registry for these is currently defined, although
 * this is likely to change in future.
 * @param displayName human readable name for the commodity.
 * @param defaultFractionDigits the number of digits normally after the decimal point when referring to quantities of
 * this commodity.
 */
data class Commodity(val commodityCode: String,
                     val displayName: String,
                     val defaultFractionDigits: Int = 0) {
    companion object {
        private val registry = mapOf(
                // Simple example commodity, as in http://www.investopedia.com/university/commodities/commodities14.asp
                Pair("FCOJ", Commodity("FCOJ", "Frozen concentrated orange juice"))
        )

        fun getInstance(commodityCode: String): Commodity?
                = registry[commodityCode]
    }
}

/**
 * This class provides a truly unique identifier of a trade, state, or other business object, bound to any existing
 * external ID. Equality and comparison are based on the unique ID only; if two states somehow have the same UUID but
 * different external IDs, it would indicate a problem with handling of IDs.
 *
 * @param externalId Any existing weak identifier such as trade reference ID.
 * This should be set here the first time a [UniqueIdentifier] is created as part of state issuance,
 * or ledger on-boarding activity. This ensure that the human readable identity is paired with the strong ID.
 * @param id Should never be set by user code and left as default initialised.
 * So that the first time a state is issued this should be given a new UUID.
 * Subsequent copies and evolutions of a state should just copy the [externalId] and [id] fields unmodified.
 */
data class UniqueIdentifier(val externalId: String? = null, val id: UUID = UUID.randomUUID()) : Comparable<UniqueIdentifier> {
    override fun toString(): String = if (externalId != null) "${externalId}_$id" else id.toString()

    companion object {
        /** Helper function for unit tests where the UUID needs to be manually initialised for consistency. */
        @VisibleForTesting
        fun fromString(name: String): UniqueIdentifier = UniqueIdentifier(null, UUID.fromString(name))
    }

    override fun compareTo(other: UniqueIdentifier): Int = id.compareTo(other.id)

    override fun equals(other: Any?): Boolean {
        return if (other is UniqueIdentifier)
            id == other.id
        else
            false
    }

    override fun hashCode(): Int = id.hashCode()
}
