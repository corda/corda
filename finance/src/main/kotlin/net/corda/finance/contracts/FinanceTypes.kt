/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.CommodityContract
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Interest rate fixes
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// DOCSTART 1
/** A [FixOf] identifies the question side of a fix: what day, tenor and type of fix ("LIBOR", "EURIBOR" etc) */
@CordaSerializable
data class FixOf(val name: String, val forDay: LocalDate, val ofTenor: Tenor)
// DOCEND 1

// DOCSTART 2
/** A [Fix] represents a named interest rate, on a given day, for a given duration. It can be embedded in a tx. */
data class Fix(val of: FixOf, val value: BigDecimal) : CommandData
// DOCEND 2

/** Represents a textual expression of e.g. a formula */
@CordaSerializable
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
@CordaSerializable
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
        }
        // Move date to the closest business day when it falls on a weekend/holiday
        val adjustedMaturityDate = calendar.applyRollConvention(maturityDate, DateRollConvention.ModifiedFollowing)

        return BusinessCalendar.calculateDaysBetween(startDate, adjustedMaturityDate, DayCountBasisYear.Y360, DayCountBasisDay.DActual)
    }

    override fun toString(): String = name

    @CordaSerializable
    enum class TimeUnit(val code: String) {
        Day("D"), Week("W"), Month("M"), Year("Y")
    }
}

/**
 * Simple enum for returning accurals adjusted or unadjusted.
 * We don't actually do anything with this yet though, so it's ignored for now.
 */
@CordaSerializable
enum class AccrualAdjustment {
    Adjusted, Unadjusted
}

/**
 * This is utilised in the [DateRollConvention] class to determine which way we should initially step when
 * finding a business day.
 */
@CordaSerializable
enum class DateRollDirection(val value: Long) { FORWARD(1), BACKWARD(-1) }

/**
 * This reflects what happens if a date on which a business event is supposed to happen actually falls upon a non-working day.
 * Depending on the accounting requirement, we can move forward until we get to a business day, or backwards.
 * There are some additional rules which are explained in the individual cases below.
 */
@CordaSerializable
enum class DateRollConvention(val direction: () -> DateRollDirection, val isModified: Boolean) {
    // direction() cannot be a val due to the throw in the Actual instance

    /** Don't roll the date, use the one supplied. */
    Actual({ throw UnsupportedOperationException("Direction is not relevant for convention Actual") }, false),
    /** Following is the next business date from this one. */
    Following({ DateRollDirection.FORWARD }, false),
    /**
     * "Modified following" is the next business date, unless it's in the next month, in which case use the preceeding
     * business date.
     */
    ModifiedFollowing({ DateRollDirection.FORWARD }, true),
    /** Previous is the previous business date from this one. */
    Previous({ DateRollDirection.BACKWARD }, false),
    /**
     * Modified previous is the previous business date, unless it's in the previous month, in which case use the next
     * business date.
     */
    ModifiedPrevious({ DateRollDirection.BACKWARD }, true);
}


/**
 * This forms the day part of the "Day Count Basis" used for interest calculation.
 * Note that the first character cannot be a number (enum naming constraints), so we drop that
 * in the toString lest some people get confused.
 */
@CordaSerializable
enum class DayCountBasisDay {
    // We have to prefix 30 etc with a letter due to enum naming constraints.
    D30,
    D30N, D30P, D30E, D30G, DActual, DActualJ, D30Z, D30F, DBus_SaoPaulo;

    override fun toString(): String {
        return super.toString().drop(1)
    }
}

/** This forms the year part of the "Day Count Basis" used for interest calculation. */
@CordaSerializable
enum class DayCountBasisYear {
    // Ditto above comment for years.
    Y360,
    Y365F, Y365L, Y365Q, Y366, YActual, YActualA, Y365B, Y365, YISMA, YISDA, YICMA, Y252;

    override fun toString(): String {
        return super.toString().drop(1)
    }
}

/** Whether the payment should be made before the due date, or after it. */
@CordaSerializable
enum class PaymentRule {
    InAdvance, InArrears,
}

/**
 * Frequency at which an event occurs - the enumerator also casts to an integer specifying the number of times per year
 * that would divide into (eg annually = 1, semiannual = 2, monthly = 12 etc).
 */
@Suppress("unused")   // TODO: Revisit post-Vega and see if annualCompoundCount is still needed.
@CordaSerializable
enum class Frequency(val annualCompoundCount: Int, val offset: LocalDate.(Long) -> LocalDate) {
    Annual(1, { plusYears(1 * it) }),
    SemiAnnual(2, { plusMonths(6 * it) }),
    Quarterly(4, { plusMonths(3 * it) }),
    Monthly(12, { plusMonths(1 * it) }),
    Weekly(52, { plusWeeks(1 * it) }),
    BiWeekly(26, { plusWeeks(2 * it) }),
    Daily(365, { plusDays(1 * it) });
}

// TODO: Make Calendar data come from an oracle

/**
 * A business calendar performs date calculations that take into account national holidays and weekends. This is a
 * typical feature of financial contracts, in which a business may not want a payment event to fall on a day when
 * no staff are around to handle problems.
 */
@CordaSerializable
open class BusinessCalendar(val holidayDates: List<LocalDate>) {
    @CordaSerializable
    class UnknownCalendar(name: String) : FlowException("$name not found")

    companion object {
        val calendars = listOf("London", "NewYork")

        val TEST_CALENDAR_DATA = calendars.map {
            it to BusinessCalendar::class.java.getResourceAsStream("${it}HolidayCalendar.txt").bufferedReader().readText()
        }.toMap()

        @JvmStatic
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

        /** Parses a date of the form YYYY-MM-DD, like 2016-01-10 for 10th Jan. */
        @JvmStatic
        fun parseDateFromString(it: String): LocalDate = LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)

        /** Returns a business calendar that combines all the named holiday calendars into one list of holiday dates. */
        @JvmStatic
        fun getInstance(vararg calname: String) = BusinessCalendar(
                calname.flatMap { (TEST_CALENDAR_DATA[it] ?: throw UnknownCalendar(it)).split(",") }.
                        toSet().
                        map { parseDateFromString(it) }.
                        toList().sorted()
        )

        /** Calculates an event schedule that moves events around to ensure they fall on working days. */
        @JvmStatic
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

        /**
         * Calculates the date from @startDate moving forward 'steps' of time size 'period'. Does not apply calendar
         * logic / roll conventions.
         */
        @JvmStatic
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

/** A common netting command for contracts whose states can be netted. */
interface NetCommand : CommandData {
    /** The type of netting to apply, see [NetType] for options. */
    val type: NetType
}

/**
 * Enum for the types of netting that can be applied to state objects. Exact behaviour
 * for each type of netting is left to the contract to determine.
 */
@CordaSerializable
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
@CordaSerializable
data class Commodity(val commodityCode: String,
                     val displayName: String,
                     val defaultFractionDigits: Int = 0) : TokenizableAssetInfo {
    override val displayTokenSize: BigDecimal
        get() = BigDecimal.ONE.scaleByPowerOfTen(-defaultFractionDigits)

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
 * Interface representing an agreement that exposes various attributes that are common. Implementing it simplifies
 * implementation of general flows that manipulate many agreement types.
 */
interface DealState : LinearState {
    /**
     * Generate a partial transaction representing an agreement (command) to this deal, allowing a general
     * deal/agreement flow to generate the necessary transaction for potential implementations.
     *
     * TODO: Currently this is the "inception" transaction but in future an offer of some description might be an input state ref
     *
     * TODO: This should more likely be a method on the Contract (on a common interface) and the changes to reference a
     * Contract instance from a ContractState are imminent, at which point we can move this out of here.
     */
    fun generateAgreement(notary: Party): TransactionBuilder
}

/**
 * Interface adding fixing specific methods.
 */
interface FixableDealState : DealState {
    /**
     * When is the next fixing and what is the fixing for?
     */
    fun nextFixingOf(): FixOf?

    /**
     * What oracle service to use for the fixing
     */
    val oracle: Party

    /**
     * Generate a fixing command for this deal and fix.
     *
     * TODO: This would also likely move to methods on the Contract once the changes to reference
     * the Contract from the ContractState are in.
     */
    fun generateFix(ptx: TransactionBuilder, oldState: StateAndRef<*>, fix: Fix)
}


/**
 * Interface for state objects that support being netted with other state objects.
 */
interface BilateralNettableState<N : BilateralNettableState<N>> {
    /**
     * Returns an object used to determine if two states can be subject to close-out netting. If two states return
     * equal objects, they can be close out netted together.
     */
    val bilateralNetState: Any

    /**
     * Perform bilateral netting of this state with another state. The two states must be compatible (as in
     * bilateralNetState objects are equal).
     */
    fun net(other: N): N
}

/**
 * Interface for state objects that support being netted with other state objects.
 */
interface MultilateralNettableState<out T : Any> {
    /**
     * Returns an object used to determine if two states can be subject to close-out netting. If two states return
     * equal objects, they can be close out netted together.
     */
    val multilateralNetState: T
}

interface NettableState<N : BilateralNettableState<N>, out T : Any> : BilateralNettableState<N>,
        MultilateralNettableState<T>
