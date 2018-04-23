/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.irs.contract

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.*
import net.corda.irs.flows.FixingFlow
import net.corda.irs.utilities.suggestInterestRateAnnouncementTimeWindow
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

const val IRS_PROGRAM_ID = "net.corda.irs.contract.InterestRateSwap"

// This is a placeholder for some types that we haven't identified exactly what they are just yet for things still in discussion
@CordaSerializable
open class UnknownType {
    override fun equals(other: Any?): Boolean = other is UnknownType
    override fun hashCode() = 1
}

/**
 * Event superclass - everything happens on a date.
 */
open class Event(val date: LocalDate) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Event) return false

        if (date != other.date) return false

        return true
    }

    override fun hashCode() = Objects.hash(date)
}

/**
 * Top level PaymentEvent class - represents an obligation to pay an amount on a given date, which may be either in the past or the future.
 */
abstract class PaymentEvent(date: LocalDate) : Event(date) {
    abstract fun calculate(): Amount<Currency>
}

/**
 * A [RatePaymentEvent] represents a dated obligation of payment.
 * It is a specialisation / modification of a basic cash flow event (to be written) that has some additional assistance
 * functions for interest rate swap legs of the fixed and floating nature.
 * For the fixed leg, the rate is already known at creation and therefore the flows can be pre-determined.
 * For the floating leg, the rate refers to a reference rate which is to be "fixed" at a point in the future.
 */
abstract class RatePaymentEvent(date: LocalDate,
                                val accrualStartDate: LocalDate,
                                val accrualEndDate: LocalDate,
                                val dayCountBasisDay: DayCountBasisDay,
                                val dayCountBasisYear: DayCountBasisYear,
                                val notional: Amount<Currency>,
                                val rate: Rate) : PaymentEvent(date) {
    companion object {
        val CSVHeader = "AccrualStartDate,AccrualEndDate,DayCountFactor,Days,Date,Ccy,Notional,Rate,Flow"
    }

    override fun calculate(): Amount<Currency> = flow

    abstract val flow: Amount<Currency>

    val days: Int get() = BusinessCalendar.calculateDaysBetween(accrualStartDate, accrualEndDate, dayCountBasisYear, dayCountBasisDay)

    // TODO : Fix below (use daycount convention for division, not hardcoded 360 etc)
    val dayCountFactor: BigDecimal get() = (BigDecimal(days).divide(BigDecimal(360.0), 8, RoundingMode.HALF_UP)).setScale(4, RoundingMode.HALF_UP)

    open fun asCSV() = "$accrualStartDate,$accrualEndDate,$dayCountFactor,$days,$date,${notional.token},$notional,$rate,$flow"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatePaymentEvent) return false

        if (accrualStartDate != other.accrualStartDate) return false
        if (accrualEndDate != other.accrualEndDate) return false
        if (dayCountBasisDay != other.dayCountBasisDay) return false
        if (dayCountBasisYear != other.dayCountBasisYear) return false
        if (notional != other.notional) return false
        if (rate != other.rate) return false
        // if (flow != other.flow) return false // Flow is derived

        return super.equals(other)
    }

    override fun hashCode() = super.hashCode() + 31 * Objects.hash(accrualStartDate, accrualEndDate, dayCountBasisDay,
            dayCountBasisYear, notional, rate)
}

/**
 * Basic class for the Fixed Rate Payments on the fixed leg - see [RatePaymentEvent].
 * Assumes that the rate is valid.
 */
@CordaSerializable
@JsonIgnoreProperties(ignoreUnknown = true)
class FixedRatePaymentEvent(date: LocalDate,
                            accrualStartDate: LocalDate,
                            accrualEndDate: LocalDate,
                            dayCountBasisDay: DayCountBasisDay,
                            dayCountBasisYear: DayCountBasisYear,
                            notional: Amount<Currency>,
                            rate: Rate) :
        RatePaymentEvent(date, accrualStartDate, accrualEndDate, dayCountBasisDay, dayCountBasisYear, notional, rate) {
    companion object {
        val CSVHeader = RatePaymentEvent.CSVHeader
    }

    override val flow: Amount<Currency> get() = Amount(dayCountFactor.times(BigDecimal(notional.quantity)).times(rate.ratioUnit!!.value).toLong(), notional.token)

    override fun toString(): String =
            "FixedRatePaymentEvent $accrualStartDate -> $accrualEndDate : $dayCountFactor : $days : $date : $notional : $rate : $flow"
}

/**
 * Basic class for the Floating Rate Payments on the floating leg - see [RatePaymentEvent].
 * If the rate is null returns a zero payment. // TODO: Is this the desired behaviour?
 */
@CordaSerializable
@JsonIgnoreProperties(ignoreUnknown = true)
class FloatingRatePaymentEvent(date: LocalDate,
                               accrualStartDate: LocalDate,
                               accrualEndDate: LocalDate,
                               dayCountBasisDay: DayCountBasisDay,
                               dayCountBasisYear: DayCountBasisYear,
                               val fixingDate: LocalDate,
                               notional: Amount<Currency>,
                               rate: Rate) : RatePaymentEvent(date, accrualStartDate, accrualEndDate, dayCountBasisDay, dayCountBasisYear, notional, rate) {

    companion object {
        val CSVHeader = RatePaymentEvent.CSVHeader + ",FixingDate"
    }

    override val flow: Amount<Currency>
        get() {
            // TODO: Should an uncalculated amount return a zero ? null ? etc.
            val v = rate.ratioUnit?.value ?: return Amount(0, notional.token)
            return Amount(dayCountFactor.times(BigDecimal(notional.quantity)).times(v).toLong(), notional.token)
        }

    override fun toString(): String = "FloatingPaymentEvent $accrualStartDate -> $accrualEndDate : $dayCountFactor : $days : $date : $notional : $rate (fix on $fixingDate): $flow"

    override fun asCSV(): String = "$accrualStartDate,$accrualEndDate,$dayCountFactor,$days,$date,${notional.token},$notional,$fixingDate,$rate,$flow"

    /**
     * Used for making immutables.
     */
    fun withNewRate(newRate: Rate): FloatingRatePaymentEvent =
            FloatingRatePaymentEvent(date, accrualStartDate, accrualEndDate, dayCountBasisDay,
                    dayCountBasisYear, fixingDate, notional, newRate)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as FloatingRatePaymentEvent
        if (fixingDate != other.fixingDate) return false
        return super.equals(other)
    }

    override fun hashCode() = super.hashCode() + 31 * Objects.hash(fixingDate)

    // Can't autogenerate as not a data class :-(
    fun copy(date: LocalDate = this.date,
             accrualStartDate: LocalDate = this.accrualStartDate,
             accrualEndDate: LocalDate = this.accrualEndDate,
             dayCountBasisDay: DayCountBasisDay = this.dayCountBasisDay,
             dayCountBasisYear: DayCountBasisYear = this.dayCountBasisYear,
             fixingDate: LocalDate = this.fixingDate,
             notional: Amount<Currency> = this.notional,
             rate: Rate = this.rate) = FloatingRatePaymentEvent(date, accrualStartDate, accrualEndDate, dayCountBasisDay, dayCountBasisYear, fixingDate, notional, rate)
}


/**
 * The Interest Rate Swap class. For a quick overview of what an IRS is, see here - http://www.pimco.co.uk/EN/Education/Pages/InterestRateSwapsBasics1-08.aspx (no endorsement).
 * This contract has 4 significant data classes within it, the "Common", "Calculation", "FixedLeg" and "FloatingLeg".
 * It also has 4 commands, "Agree", "Fix", "Pay" and "Mature".
 * Currently, we are not interested (excuse pun) in valuing the swap, calculating the PVs, DFs and all that good stuff (soon though).
 * This is just a representation of a vanilla Fixed vs Floating (same currency) IRS in the R3 prototype model.
 */
class InterestRateSwap : Contract {
    /**
     * This Common area contains all the information that is not leg specific.
     */
    @CordaSerializable
    data class Common(
            val baseCurrency: Currency,
            val eligibleCurrency: Currency,
            val eligibleCreditSupport: String,
            val independentAmounts: Amount<Currency>,
            val threshold: Amount<Currency>,
            val minimumTransferAmount: Amount<Currency>,
            val rounding: Amount<Currency>,
            val valuationDateDescription: String, // This describes (in english) how regularly the swap is to be valued, e.g. "every local working day"
            val notificationTime: String,
            val resolutionTime: String,
            val interestRate: ReferenceRate,
            val addressForTransfers: String,
            val exposure: UnknownType,
            val localBusinessDay: BusinessCalendar,
            val dailyInterestAmount: Expression,
            val tradeID: String,
            val hashLegalDocs: String
    )

    /**
     * The Calculation data class is "mutable" through out the life of the swap, as in, it's the only thing that contains
     * data that will changed from state to state (Recall that the design insists that everything is immutable, so we actually
     * copy / update for each transition).
     */
    @CordaSerializable
    data class Calculation(
            val expression: Expression,
            val floatingLegPaymentSchedule: Map<LocalDate, FloatingRatePaymentEvent>,
            val fixedLegPaymentSchedule: Map<LocalDate, FixedRatePaymentEvent>
    ) {
        /**
         * Gets the date of the next fixing.
         * @return LocalDate or null if no more fixings.
         */
        fun nextFixingDate(): LocalDate? {
            return floatingLegPaymentSchedule.
                    filter { it.value.rate is ReferenceRate }.// TODO - a better way to determine what fixings remain to be fixed
                    minBy { it.value.fixingDate.toEpochDay() }?.value?.fixingDate
        }

        /**
         * Returns the fixing for that date.
         */
        fun getFixing(date: LocalDate): FloatingRatePaymentEvent =
                floatingLegPaymentSchedule.values.single { it.fixingDate == date }

        /**
         * Returns a copy after modifying (applying) the fixing for that date.
         */
        fun applyFixing(date: LocalDate, newRate: FixedRate): Calculation {
            val paymentEvent = getFixing(date)
            val newFloatingLPS = floatingLegPaymentSchedule + (paymentEvent.date to paymentEvent.withNewRate(newRate))
            return Calculation(expression = expression,
                    floatingLegPaymentSchedule = newFloatingLPS,
                    fixedLegPaymentSchedule = fixedLegPaymentSchedule)
        }
    }

    abstract class CommonLeg(
            val notional: Amount<Currency>,
            val paymentFrequency: Frequency,
            val effectiveDate: LocalDate,
            val effectiveDateAdjustment: DateRollConvention?,
            val terminationDate: LocalDate,
            val terminationDateAdjustment: DateRollConvention?,
            val dayCountBasisDay: DayCountBasisDay,
            val dayCountBasisYear: DayCountBasisYear,
            val dayInMonth: Int,
            val paymentRule: PaymentRule,
            val paymentDelay: Int,
            val paymentCalendar: BusinessCalendar,
            val interestPeriodAdjustment: AccrualAdjustment
    ) {
        override fun toString(): String {
            return "Notional=$notional,PaymentFrequency=$paymentFrequency,EffectiveDate=$effectiveDate,EffectiveDateAdjustment:$effectiveDateAdjustment,TerminatationDate=$terminationDate," +
                    "TerminationDateAdjustment=$terminationDateAdjustment,DayCountBasis=$dayCountBasisDay/$dayCountBasisYear,DayInMonth=$dayInMonth," +
                    "PaymentRule=$paymentRule,PaymentDelay=$paymentDelay,PaymentCalendar=$paymentCalendar,InterestPeriodAdjustment=$interestPeriodAdjustment"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as CommonLeg

            if (notional != other.notional) return false
            if (paymentFrequency != other.paymentFrequency) return false
            if (effectiveDate != other.effectiveDate) return false
            if (effectiveDateAdjustment != other.effectiveDateAdjustment) return false
            if (terminationDate != other.terminationDate) return false
            if (terminationDateAdjustment != other.terminationDateAdjustment) return false
            if (dayCountBasisDay != other.dayCountBasisDay) return false
            if (dayCountBasisYear != other.dayCountBasisYear) return false
            if (dayInMonth != other.dayInMonth) return false
            if (paymentRule != other.paymentRule) return false
            if (paymentDelay != other.paymentDelay) return false
            if (paymentCalendar != other.paymentCalendar) return false
            if (interestPeriodAdjustment != other.interestPeriodAdjustment) return false

            return true
        }

        override fun hashCode() = super.hashCode() + 31 * Objects.hash(notional, paymentFrequency, effectiveDate,
                effectiveDateAdjustment, terminationDate, effectiveDateAdjustment, terminationDate, terminationDateAdjustment,
                dayCountBasisDay, dayCountBasisYear, dayInMonth, paymentRule, paymentDelay, paymentCalendar, interestPeriodAdjustment)
    }

    @CordaSerializable
    open class FixedLeg(
            var fixedRatePayer: AbstractParty,
            notional: Amount<Currency>,
            paymentFrequency: Frequency,
            effectiveDate: LocalDate,
            effectiveDateAdjustment: DateRollConvention?,
            terminationDate: LocalDate,
            terminationDateAdjustment: DateRollConvention?,
            dayCountBasisDay: DayCountBasisDay,
            dayCountBasisYear: DayCountBasisYear,
            dayInMonth: Int,
            paymentRule: PaymentRule,
            paymentDelay: Int,
            paymentCalendar: BusinessCalendar,
            interestPeriodAdjustment: AccrualAdjustment,
            var fixedRate: FixedRate,
            var rollConvention: DateRollConvention // TODO - best way of implementing - still awaiting some clarity
    ) : CommonLeg
    (notional, paymentFrequency, effectiveDate, effectiveDateAdjustment, terminationDate, terminationDateAdjustment,
            dayCountBasisDay, dayCountBasisYear, dayInMonth, paymentRule, paymentDelay, paymentCalendar, interestPeriodAdjustment) {
        override fun toString(): String = "FixedLeg(Payer=$fixedRatePayer," + super.toString() + ",fixedRate=$fixedRate," +
                "rollConvention=$rollConvention"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            if (!super.equals(other)) return false

            other as FixedLeg

            if (fixedRatePayer != other.fixedRatePayer) return false
            if (fixedRate != other.fixedRate) return false
            if (rollConvention != other.rollConvention) return false

            return true
        }

        override fun hashCode() = super.hashCode() + 31 * Objects.hash(fixedRatePayer, fixedRate, rollConvention)

        // Can't autogenerate as not a data class :-(
        fun copy(fixedRatePayer: AbstractParty = this.fixedRatePayer,
                 notional: Amount<Currency> = this.notional,
                 paymentFrequency: Frequency = this.paymentFrequency,
                 effectiveDate: LocalDate = this.effectiveDate,
                 effectiveDateAdjustment: DateRollConvention? = this.effectiveDateAdjustment,
                 terminationDate: LocalDate = this.terminationDate,
                 terminationDateAdjustment: DateRollConvention? = this.terminationDateAdjustment,
                 dayCountBasisDay: DayCountBasisDay = this.dayCountBasisDay,
                 dayCountBasisYear: DayCountBasisYear = this.dayCountBasisYear,
                 dayInMonth: Int = this.dayInMonth,
                 paymentRule: PaymentRule = this.paymentRule,
                 paymentDelay: Int = this.paymentDelay,
                 paymentCalendar: BusinessCalendar = this.paymentCalendar,
                 interestPeriodAdjustment: AccrualAdjustment = this.interestPeriodAdjustment,
                 fixedRate: FixedRate = this.fixedRate) = FixedLeg(
                fixedRatePayer, notional, paymentFrequency, effectiveDate, effectiveDateAdjustment, terminationDate,
                terminationDateAdjustment, dayCountBasisDay, dayCountBasisYear, dayInMonth, paymentRule, paymentDelay,
                paymentCalendar, interestPeriodAdjustment, fixedRate, rollConvention)
    }

    @CordaSerializable
    open class FloatingLeg(
            var floatingRatePayer: AbstractParty,
            notional: Amount<Currency>,
            paymentFrequency: Frequency,
            effectiveDate: LocalDate,
            effectiveDateAdjustment: DateRollConvention?,
            terminationDate: LocalDate,
            terminationDateAdjustment: DateRollConvention?,
            dayCountBasisDay: DayCountBasisDay,
            dayCountBasisYear: DayCountBasisYear,
            dayInMonth: Int,
            paymentRule: PaymentRule,
            paymentDelay: Int,
            paymentCalendar: BusinessCalendar,
            interestPeriodAdjustment: AccrualAdjustment,
            var rollConvention: DateRollConvention,
            var fixingRollConvention: DateRollConvention,
            var resetDayInMonth: Int,
            var fixingPeriodOffset: Int,
            var resetRule: PaymentRule,
            var fixingsPerPayment: Frequency,
            var fixingCalendar: BusinessCalendar,
            var index: String,
            var indexSource: String,
            var indexTenor: Tenor
    ) : CommonLeg(notional, paymentFrequency, effectiveDate, effectiveDateAdjustment, terminationDate, terminationDateAdjustment,
            dayCountBasisDay, dayCountBasisYear, dayInMonth, paymentRule, paymentDelay, paymentCalendar, interestPeriodAdjustment) {
        override fun toString(): String = "FloatingLeg(Payer=$floatingRatePayer," + super.toString() +
                "rollConvention=$rollConvention,FixingRollConvention=$fixingRollConvention,ResetDayInMonth=$resetDayInMonth" +
                "FixingPeriondOffset=$fixingPeriodOffset,ResetRule=$resetRule,FixingsPerPayment=$fixingsPerPayment,FixingCalendar=$fixingCalendar," +
                "Index=$index,IndexSource=$indexSource,IndexTenor=$indexTenor"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            if (!super.equals(other)) return false

            other as FloatingLeg

            if (floatingRatePayer != other.floatingRatePayer) return false
            if (rollConvention != other.rollConvention) return false
            if (fixingRollConvention != other.fixingRollConvention) return false
            if (resetDayInMonth != other.resetDayInMonth) return false
            if (fixingPeriodOffset != other.fixingPeriodOffset) return false
            if (resetRule != other.resetRule) return false
            if (fixingsPerPayment != other.fixingsPerPayment) return false
            if (fixingCalendar != other.fixingCalendar) return false
            if (index != other.index) return false
            if (indexSource != other.indexSource) return false
            if (indexTenor != other.indexTenor) return false

            return true
        }

        override fun hashCode() = super.hashCode() + 31 * Objects.hash(floatingRatePayer, rollConvention,
                fixingRollConvention, resetDayInMonth, fixingPeriodOffset, resetRule, fixingsPerPayment, fixingCalendar,
                index, indexSource, indexTenor)


        fun copy(floatingRatePayer: AbstractParty = this.floatingRatePayer,
                 notional: Amount<Currency> = this.notional,
                 paymentFrequency: Frequency = this.paymentFrequency,
                 effectiveDate: LocalDate = this.effectiveDate,
                 effectiveDateAdjustment: DateRollConvention? = this.effectiveDateAdjustment,
                 terminationDate: LocalDate = this.terminationDate,
                 terminationDateAdjustment: DateRollConvention? = this.terminationDateAdjustment,
                 dayCountBasisDay: DayCountBasisDay = this.dayCountBasisDay,
                 dayCountBasisYear: DayCountBasisYear = this.dayCountBasisYear,
                 dayInMonth: Int = this.dayInMonth,
                 paymentRule: PaymentRule = this.paymentRule,
                 paymentDelay: Int = this.paymentDelay,
                 paymentCalendar: BusinessCalendar = this.paymentCalendar,
                 interestPeriodAdjustment: AccrualAdjustment = this.interestPeriodAdjustment,
                 rollConvention: DateRollConvention = this.rollConvention,
                 fixingRollConvention: DateRollConvention = this.fixingRollConvention,
                 resetDayInMonth: Int = this.resetDayInMonth,
                 fixingPeriod: Int = this.fixingPeriodOffset,
                 resetRule: PaymentRule = this.resetRule,
                 fixingsPerPayment: Frequency = this.fixingsPerPayment,
                 fixingCalendar: BusinessCalendar = this.fixingCalendar,
                 index: String = this.index,
                 indexSource: String = this.indexSource,
                 indexTenor: Tenor = this.indexTenor
        ) = FloatingLeg(floatingRatePayer, notional, paymentFrequency, effectiveDate, effectiveDateAdjustment,
                terminationDate, terminationDateAdjustment, dayCountBasisDay, dayCountBasisYear, dayInMonth,
                paymentRule, paymentDelay, paymentCalendar, interestPeriodAdjustment, rollConvention,
                fixingRollConvention, resetDayInMonth, fixingPeriod, resetRule, fixingsPerPayment,
                fixingCalendar, index, indexSource, indexTenor)
    }

    // These functions may make more sense to use for basket types, but for now let's leave them here
    private fun checkLegDates(legs: List<CommonLeg>) {
        requireThat {
            "Effective date is before termination date" using legs.all { it.effectiveDate < it.terminationDate }
            "Effective dates are in alignment" using legs.all { it.effectiveDate == legs[0].effectiveDate }
            "Termination dates are in alignment" using legs.all { it.terminationDate == legs[0].terminationDate }
        }
    }

    private fun checkLegAmounts(legs: List<CommonLeg>) {
        requireThat {
            "The notional is non zero" using legs.any { it.notional.quantity > (0).toLong() }
            "The notional for all legs must be the same" using legs.all { it.notional == legs[0].notional }
        }
        for (leg: CommonLeg in legs) {
            if (leg is FixedLeg) {
                requireThat {
                    // TODO: Confirm: would someone really enter a swap with a negative fixed rate?
                    "Fixed leg rate must be positive" using leg.fixedRate.isPositive()
                }
            }
        }
    }

    /**
     * Compares two schedules of Floating Leg Payments, returns the difference (i.e. omissions in either leg or changes to the values).
     */
    private fun getFloatingLegPaymentsDifferences(payments1: Map<LocalDate, Event>, payments2: Map<LocalDate, Event>): List<Pair<LocalDate, Pair<FloatingRatePaymentEvent, FloatingRatePaymentEvent>>> {
        val diff1 = payments1.filter { payments1[it.key] != payments2[it.key] }
        val diff2 = payments2.filter { payments1[it.key] != payments2[it.key] }
        return (diff1.keys + diff2.keys).map {
            it to Pair(diff1[it] as FloatingRatePaymentEvent, diff2[it] as FloatingRatePaymentEvent)
        }
    }

    private fun verifyAgreeCommand(inputs: List<State>, outputs: List<State>) {
        val irs = outputs.filterIsInstance<State>().single()
        requireThat {
            "There are no in states for an agreement" using inputs.isEmpty()
            "There are events in the fix schedule" using (irs.calculation.fixedLegPaymentSchedule.isNotEmpty())
            "There are events in the float schedule" using (irs.calculation.floatingLegPaymentSchedule.isNotEmpty())
            "All notionals must be non zero" using (irs.fixedLeg.notional.quantity > 0 && irs.floatingLeg.notional.quantity > 0)
            "The fixed leg rate must be positive" using (irs.fixedLeg.fixedRate.isPositive())
            "The currency of the notionals must be the same" using (irs.fixedLeg.notional.token == irs.floatingLeg.notional.token)
            "All leg notionals must be the same" using (irs.fixedLeg.notional == irs.floatingLeg.notional)
            "The effective date is before the termination date for the fixed leg" using (irs.fixedLeg.effectiveDate < irs.fixedLeg.terminationDate)
            "The effective date is before the termination date for the floating leg" using (irs.floatingLeg.effectiveDate < irs.floatingLeg.terminationDate)
            "The effective dates are aligned" using (irs.floatingLeg.effectiveDate == irs.fixedLeg.effectiveDate)
            "The termination dates are aligned" using (irs.floatingLeg.terminationDate == irs.fixedLeg.terminationDate)
            "The fixing period date offset cannot be negative" using (irs.floatingLeg.fixingPeriodOffset >= 0)

            // TODO: further tests
        }
        checkLegAmounts(listOf(irs.fixedLeg, irs.floatingLeg))
        checkLegDates(listOf(irs.fixedLeg, irs.floatingLeg))
    }

    private fun verifyFixCommand(inputs: List<State>, outputs: List<State>, command: CommandWithParties<Commands.Refix>) {
        val irs = outputs.filterIsInstance<State>().single()
        val prevIrs = inputs.filterIsInstance<State>().single()
        val paymentDifferences = getFloatingLegPaymentsDifferences(prevIrs.calculation.floatingLegPaymentSchedule, irs.calculation.floatingLegPaymentSchedule)

        // Having both of these tests are "redundant" as far as verify() goes, however, by performing both
        // we can relay more information back to the user in the case of failure.
        requireThat {
            "There is at least one difference in the IRS floating leg payment schedules" using !paymentDifferences.isEmpty()
            "There is only one change in the IRS floating leg payment schedule" using (paymentDifferences.size == 1)
        }

        val (oldFloatingRatePaymentEvent, newFixedRatePaymentEvent) = paymentDifferences.single().second // Ignore the date of the changed rate (we checked that earlier).
        val fixValue = command.value.fix
        // Need to check that everything is the same apart from the new fixed rate entry.
        requireThat {
            "The fixed leg parties are constant" using (irs.fixedLeg.fixedRatePayer == prevIrs.fixedLeg.fixedRatePayer) // Although superseded by the below test, this is included for a regression issue
            "The fixed leg is constant" using (irs.fixedLeg == prevIrs.fixedLeg)
            "The floating leg is constant" using (irs.floatingLeg == prevIrs.floatingLeg)
            "The common values are constant" using (irs.common == prevIrs.common)
            "The fixed leg payment schedule is constant" using (irs.calculation.fixedLegPaymentSchedule == prevIrs.calculation.fixedLegPaymentSchedule)
            "The expression is unchanged" using (irs.calculation.expression == prevIrs.calculation.expression)
            "There is only one changed payment in the floating leg" using (paymentDifferences.size == 1)
            "There changed payment is a floating payment" using (oldFloatingRatePaymentEvent.rate is ReferenceRate)
            "The new payment is a fixed payment" using (newFixedRatePaymentEvent.rate is FixedRate)
            "The changed payments dates are aligned" using (oldFloatingRatePaymentEvent.date == newFixedRatePaymentEvent.date)
            "The new payment has the correct rate" using (newFixedRatePaymentEvent.rate.ratioUnit!!.value == fixValue.value)
            "The fixing is for the next required date" using (prevIrs.calculation.nextFixingDate() == fixValue.of.forDay)
            "The fix payment has the same currency as the notional" using (newFixedRatePaymentEvent.flow.token == irs.floatingLeg.notional.token)
            // "The fixing is not in the future " by (fixCommand) // The oracle should not have signed this .
        }
    }

    private fun verifyPayCommand() {
        requireThat {
            "Payments not supported / verifiable yet" using false
        }
    }

    private fun verifyMatureCommand(inputs: List<State>, outputs: List<State>) {
        val irs = inputs.filterIsInstance<State>().single()
        requireThat {
            "No more fixings to be applied" using (irs.calculation.nextFixingDate() == null)
            "The irs is fully consumed and there is no id matched output state" using outputs.isEmpty()
        }
    }

    override fun verify(tx: LedgerTransaction) {
        requireNotNull(tx.timeWindow) { "must be have a time-window)" }
        val groups: List<LedgerTransaction.InOutGroup<State, UniqueIdentifier>> = tx.groupStates { state -> state.linearId }
        var atLeastOneCommandProcessed = false
        for ((inputs, outputs, _) in groups) {
            val agreeCommand = tx.commands.select<Commands.Agree>().firstOrNull()
            if (agreeCommand != null) {
                verifyAgreeCommand(inputs, outputs)
                atLeastOneCommandProcessed = true
            }
            val fixCommand = tx.commands.select<Commands.Refix>().firstOrNull()
            if (fixCommand != null) {
                verifyFixCommand(inputs, outputs, fixCommand)
                atLeastOneCommandProcessed = true
            }
            val payCommand = tx.commands.select<Commands.Pay>().firstOrNull()
            if (payCommand != null) {
                verifyPayCommand()
                atLeastOneCommandProcessed = true
            }
            val matureCommand = tx.commands.select<Commands.Mature>().firstOrNull()
            if (matureCommand != null) {
                verifyMatureCommand(inputs, outputs)
                atLeastOneCommandProcessed = true
            }
        }
        require(atLeastOneCommandProcessed) { "At least one command needs to present" }
    }

    interface Commands : CommandData {
        data class Refix(val fix: Fix) : Commands      // Receive interest rate from oracle, Both sides agree
        class Pay : TypeOnlyCommandData(), Commands    // Not implemented just yet
        class Agree : TypeOnlyCommandData(), Commands  // Both sides agree to trade
        class Mature : TypeOnlyCommandData(), Commands // Trade has matured; no more actions. Cleanup. // TODO: Do we need this?
    }

    /**
     * The state class contains the 4 major data classes.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class State(
            val fixedLeg: FixedLeg,
            val floatingLeg: FloatingLeg,
            val calculation: Calculation,
            val common: Common,
            override val oracle: Party,
            override val linearId: UniqueIdentifier = UniqueIdentifier(common.tradeID)
    ) : FixableDealState, SchedulableState {
        val ref: String get() = linearId.externalId ?: ""

        override val participants: List<AbstractParty>
            get() = listOf(fixedLeg.fixedRatePayer, floatingLeg.floatingRatePayer)

        // DOCSTART 1
        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            val nextFixingOf = nextFixingOf() ?: return null

            // This is perhaps not how we should determine the time point in the business day, but instead expect the schedule to detail some of these aspects
            val instant = suggestInterestRateAnnouncementTimeWindow(index = nextFixingOf.name, source = floatingLeg.indexSource, date = nextFixingOf.forDay).fromTime!!
            return ScheduledActivity(flowLogicRefFactory.create("net.corda.irs.flows.FixingFlow\$FixingRoleDecider", thisStateRef), instant)
        }
        // DOCEND 1

        override fun generateAgreement(notary: Party): TransactionBuilder {
            return InterestRateSwap().generateAgreement(floatingLeg, fixedLeg, calculation, common, oracle, notary)
        }

        override fun generateFix(ptx: TransactionBuilder, oldState: StateAndRef<*>, fix: Fix) {
            InterestRateSwap().generateFix(ptx, StateAndRef(TransactionState(this, IRS_PROGRAM_ID, oldState.state.notary), oldState.ref), fix)
        }

        override fun nextFixingOf(): FixOf? {
            val date = calculation.nextFixingDate()
            return if (date == null) null else {
                val fixingEvent = calculation.getFixing(date)
                val oracleRate = fixingEvent.rate as ReferenceRate
                FixOf(oracleRate.name, date, oracleRate.tenor)
            }
        }

        /**
         * For evaluating arbitrary java on the platform.
         */

        fun evaluateCalculation(businessDate: LocalDate, expression: Expression = calculation.expression): Any {
            // TODO: Jexl is purely for prototyping. It may be replaced
            // TODO: Whatever we do use must be secure and sandboxed
            val jexl = JexlBuilder().create()
            val expr = jexl.createExpression(expression.expr)
            val jc = MapContext()
            jc.set("fixedLeg", fixedLeg)
            jc.set("floatingLeg", floatingLeg)
            jc.set("calculation", calculation)
            jc.set("common", common)
            jc.set("currentBusinessDate", businessDate)
            return expr.evaluate(jc)
        }

        /**
         * Just makes printing it out a bit better for those who don't have 80000 column wide monitors.
         */
        fun prettyPrint() = toString().replace(",", "\n")
    }

    /**
     *  This generates the agreement state and also the schedules from the initial data.
     *  Note: The day count, interest rate calculation etc are not finished yet, but they are demonstrable.
     */
    fun generateAgreement(floatingLeg: FloatingLeg, fixedLeg: FixedLeg, calculation: Calculation,
                          common: Common, oracle: Party, notary: Party): TransactionBuilder {
        val fixedLegPaymentSchedule = LinkedHashMap<LocalDate, FixedRatePaymentEvent>()
        var dates = BusinessCalendar.createGenericSchedule(
                fixedLeg.effectiveDate,
                fixedLeg.paymentFrequency,
                fixedLeg.paymentCalendar,
                fixedLeg.rollConvention,
                endDate = fixedLeg.terminationDate)
        var periodStartDate = fixedLeg.effectiveDate

        // Create a schedule for the fixed payments
        for (periodEndDate in dates) {
            val paymentDate = BusinessCalendar.getOffsetDate(periodEndDate, Frequency.Daily, fixedLeg.paymentDelay)
            val paymentEvent = FixedRatePaymentEvent(
                    paymentDate,
                    periodStartDate,
                    periodEndDate,
                    fixedLeg.dayCountBasisDay,
                    fixedLeg.dayCountBasisYear,
                    fixedLeg.notional,
                    fixedLeg.fixedRate
            )
            fixedLegPaymentSchedule[paymentDate] = paymentEvent
            periodStartDate = periodEndDate
        }

        dates = BusinessCalendar.createGenericSchedule(floatingLeg.effectiveDate,
                floatingLeg.fixingsPerPayment,
                floatingLeg.fixingCalendar,
                floatingLeg.rollConvention,
                endDate = floatingLeg.terminationDate)

        val floatingLegPaymentSchedule: MutableMap<LocalDate, FloatingRatePaymentEvent> = LinkedHashMap()
        periodStartDate = floatingLeg.effectiveDate

        // Now create a schedule for the floating and fixes.
        for (periodEndDate in dates) {
            val paymentDate = BusinessCalendar.getOffsetDate(periodEndDate, Frequency.Daily, floatingLeg.paymentDelay)
            val paymentEvent = FloatingRatePaymentEvent(
                    paymentDate,
                    periodStartDate,
                    periodEndDate,
                    floatingLeg.dayCountBasisDay,
                    floatingLeg.dayCountBasisYear,
                    calcFixingDate(periodStartDate, floatingLeg.fixingPeriodOffset, floatingLeg.fixingCalendar),
                    floatingLeg.notional,
                    ReferenceRate(floatingLeg.indexSource, floatingLeg.indexTenor, floatingLeg.index)
            )

            floatingLegPaymentSchedule[paymentDate] = paymentEvent
            periodStartDate = periodEndDate
        }

        val newCalculation = Calculation(calculation.expression, floatingLegPaymentSchedule, fixedLegPaymentSchedule)

        // Put all the above into a new State object.
        val state = State(fixedLeg, floatingLeg, newCalculation, common, oracle)
        return TransactionBuilder(notary).withItems(
                StateAndContract(state, IRS_PROGRAM_ID),
                Command(Commands.Agree(), listOf(state.floatingLeg.floatingRatePayer.owningKey, state.fixedLeg.fixedRatePayer.owningKey))
        )
    }

    private fun calcFixingDate(date: LocalDate, fixingPeriodOffset: Int, calendar: BusinessCalendar): LocalDate {
        return when (fixingPeriodOffset) {
            0 -> date
            else -> calendar.moveBusinessDays(date, DateRollDirection.BACKWARD, fixingPeriodOffset)
        }
    }

    fun generateFix(tx: TransactionBuilder, irs: StateAndRef<State>, fixing: Fix) {
        tx.addInputState(irs)
        val fixedRate = FixedRate(RatioUnit(fixing.value))
        tx.addOutputState(
                irs.state.data.copy(calculation = irs.state.data.calculation.applyFixing(fixing.of.forDay, fixedRate)),
                irs.state.contract,
                irs.state.notary
        )
        tx.addCommand(Commands.Refix(fixing), listOf(irs.state.data.floatingLeg.floatingRatePayer.owningKey, irs.state.data.fixedLeg.fixedRatePayer.owningKey))
    }
}
