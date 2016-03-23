/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

import core.*
import core.crypto.SecureHash
import core.node.services.DummyTimestampingAuthority
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

val IRS_PROGRAM_ID = InterestRateSwap()

// This is a placeholder for some types that we haven't identified exactly what they are just yet for things still in discussion
open class UnknownType()

/**
 * Event superclass - everything happens on a date.
 */
open class Event(val date: LocalDate)

/**
 * Top level PaymentEvent class - represents an obligation to pay an amount on a given date, which may be either in the past or the future.
 */
abstract class PaymentEvent(date: LocalDate) : Event(date) {
    abstract fun calculate(): Amount
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
                       val notional: Amount,
                       val rate: Rate) : PaymentEvent(date) {
    companion object {
        val CSVHeader = "AccrualStartDate,AccrualEndDate,DayCountFactor,Days,Date,Ccy,Notional,Rate,Flow"
    }

    override fun calculate(): Amount = flow

    abstract val flow: Amount

    val days: Int get() =
        dayCountCalculator(accrualStartDate, accrualEndDate, dayCountBasisYear, dayCountBasisDay)

    val dayCountFactor: BigDecimal get() =
    // TODO : Fix below (use daycount convention for division)
        (BigDecimal(days).divide(BigDecimal(360.0), 8, RoundingMode.HALF_UP)).setScale(4, RoundingMode.HALF_UP)

    open fun asCSV(): String = "$accrualStartDate,$accrualEndDate,$dayCountFactor,$days,$date,${notional.currency},${notional},$rate,$flow"
}

/**
 * Basic class for the Fixed Rate Payments on the fixed leg - see [RatePaymentEvent]
 * Assumes that the rate is valid.
 */
class FixedRatePaymentEvent(date: LocalDate,
                            accrualStartDate: LocalDate,
                            accrualEndDate: LocalDate,
                            dayCountBasisDay: DayCountBasisDay,
                            dayCountBasisYear: DayCountBasisYear,
                            notional: Amount,
                            rate: Rate) :
        RatePaymentEvent(date, accrualStartDate, accrualEndDate, dayCountBasisDay, dayCountBasisYear, notional, rate) {
    companion object {
        val CSVHeader = RatePaymentEvent.CSVHeader
    }

    override val flow: Amount get() =
    Amount(dayCountFactor.times(BigDecimal(notional.pennies)).times(rate.ratioUnit!!.value).toLong(), notional.currency)

    override fun toString(): String =
            "FixedRatePaymentEvent $accrualStartDate -> $accrualEndDate : $dayCountFactor : $days : $date : $notional : $rate : $flow"
}

/**
 * Basic class for the Floating Rate Payments on the floating leg - see [RatePaymentEvent]
 * If the rate is null returns a zero payment. // TODO: Is this the desired behaviour?
 */
class FloatingRatePaymentEvent(date: LocalDate,
                               accrualStartDate: LocalDate,
                               accrualEndDate: LocalDate,
                               dayCountBasisDay: DayCountBasisDay,
                               dayCountBasisYear: DayCountBasisYear,
                               val fixingDate: LocalDate,
                               notional: Amount,
                               rate: Rate) : RatePaymentEvent(date, accrualStartDate, accrualEndDate, dayCountBasisDay, dayCountBasisYear, notional, rate) {

    companion object {
        val CSVHeader = RatePaymentEvent.CSVHeader + ",FixingDate"
    }

    override val flow: Amount get() {
        // TODO: Should an uncalculated amount return a zero ? null ? etc.
        val v = rate.ratioUnit?.value ?: return Amount(0, notional.currency)
        return Amount(dayCountFactor.times(BigDecimal(notional.pennies)).times(v).toLong(), notional.currency)
    }

    override fun toString(): String {
        return "FloatingPaymentEvent $accrualStartDate -> $accrualEndDate : $dayCountFactor : $days : $date : $notional : $rate (fix on $fixingDate): $flow"
    }

    override fun asCSV(): String = "$accrualStartDate,$accrualEndDate,$dayCountFactor,$days,$date,${notional.currency},${notional},$fixingDate,$rate,$flow"

    /**
     * Used for making immutables
     */
    fun withNewRate(newRate: Rate): FloatingRatePaymentEvent =
            FloatingRatePaymentEvent(date, accrualStartDate, accrualEndDate, dayCountBasisDay,
                    dayCountBasisYear, fixingDate, notional, newRate)
}


/**
 * Don't try and use a rate that isn't ready yet.
 */
class DataNotReadyException : Exception()


/**
 * The Interest Rate Swap class. For a quick overview of what an IRS is, see here - http://www.pimco.co.uk/EN/Education/Pages/InterestRateSwapsBasics1-08.aspx (no endorsement)
 * This contract has 4 significant data classes within it, the "Common", "Calculation", "FixedLeg" and "FloatingLeg"
 * It also has 4 commands, "Agree", "Fix", "Pay" and "Mature".
 * Currently, we are not interested (excuse pun) in valuing the swap, calculating the PVs, DFs and all that good stuff (soon though).
 * This is just a representation of a vanilla Fixed vs Floating (same currency) IRS in the R3 prototype model.
 */
class InterestRateSwap() : Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("is_this_the_text_of_the_contract ? TBD")

    /**
     * This Common area contains all the information that is not leg specific.
     */
    data class Common(
            val baseCurrency: Currency,
            val eligibleCurrency: Currency,
            val eligibleCreditSupport: String,
            val independentAmounts: Amount,
            val threshold: Amount,
            val minimumTransferAmount: Amount,
            val rounding: Amount,
            val valuationDate: String,
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

    data class Expression(val expr: String)

    /**
     * The Calculation data class is "mutable" through out the life of the swap, as in, it's the only thing that contains
     * data that will changed from state to state (Recall that the design insists that everything is immutable, so we actually
     * copy / update for each transition)
     */
    data class Calculation(
            val expression: Expression,
            val floatingLegPaymentSchedule: Map<LocalDate, FloatingRatePaymentEvent>,
            val fixedLegpaymentSchedule: Map<LocalDate, FixedRatePaymentEvent>
    ) {
        /**
         * Gets the date of the next fixing.
         * @return LocalDate or null if no more fixings.
         */
        fun nextFixingDate(): LocalDate? {
            return floatingLegPaymentSchedule.
                    filter { it.value.rate is OracleRetrievableReferenceRate }.// TODO - a better way to determine what fixings remain to be fixed
                    minBy { it.value.fixingDate.toEpochDay() }?.value?.fixingDate
        }

        /**
         * Returns the fixing for that date
         */
        fun getFixing(date: LocalDate): FloatingRatePaymentEvent =
                floatingLegPaymentSchedule.values.single { it.fixingDate == date }

        /**
         * Returns a copy after modifying (applying) the fixing for that date.
         */
        fun applyFixing(date: LocalDate, newRate: Rate): Calculation {
            val paymentEvent = getFixing(date)
            val newFloatingLPS = floatingLegPaymentSchedule + (paymentEvent.date to paymentEvent.withNewRate(newRate))
            return Calculation(expression = expression,
                    floatingLegPaymentSchedule = newFloatingLPS,
                    fixedLegpaymentSchedule = fixedLegpaymentSchedule)
        }

        fun exportSchedule() {

        }

    }

    abstract class CommonLeg(
            val notional: Amount,
            val paymentFrequency: Frequency,
            val effectiveDate: LocalDate,
            val effectiveDateAdjustment: DateRollConvention?,
            val terminationDate: LocalDate,
            val terminationDateAdjustment: DateRollConvention?,
            var dayCountBasisDay: DayCountBasisDay,
            var dayCountBasisYear: DayCountBasisYear,
            var dayInMonth: Int,
            var paymentRule: PaymentRule,
            var paymentDelay: Int,
            var paymentCalendar: BusinessCalendar,
            var interestPeriodAdjustment: AccrualAdjustment
    ) {
        override fun toString(): String {
            return "Notional=$notional,PaymentFrequency=$paymentFrequency,EffectiveDate=$effectiveDate,EffectiveDateAdjustment:$effectiveDateAdjustment,TerminatationDate=$terminationDate," +
                    "TerminationDateAdjustment=$terminationDateAdjustment,DayCountBasis=$dayCountBasisDay/$dayCountBasisYear,DayInMonth=$dayInMonth," +
                    "PaymentRule=$paymentRule,PaymentDelay=$paymentDelay,PaymentCalendar=$paymentCalendar,InterestPeriodAdjustment=$interestPeriodAdjustment"
        }
    }

    open class FixedLeg(
            var fixedRatePayer: Party,
            notional: Amount,
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
    }

    open class FloatingLeg(
            var floatingRatePayer: Party,
            notional: Amount,
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
            var fixingPeriod: DateOffset,
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
                "FixingPeriond=$fixingPeriod,ResetRule=$resetRule,FixingsPerPayment=$fixingsPerPayment,FixingCalendar=$fixingCalendar," +
                "Index=$index,IndexSource=$indexSource,IndexTenor=$indexTenor"

    }

    /**
     * verify() with a few examples of what needs to be checked. TODO: Lots more to add.
     */
    override fun verify(tx: TransactionForVerification) {
        val command = tx.commands.requireSingleCommand<InterestRateSwap.Commands>()
        val time = tx.getTimestampBy(DummyTimestampingAuthority.identity)?.midpoint
        if (time == null) throw IllegalArgumentException("must be timestamped")

        val irs = tx.outStates.filterIsInstance<InterestRateSwap.State>().single()
        when (command.value) {
            is Commands.Agree -> {
                requireThat {
                    "There are no in states for an agreement" by tx.inStates.isEmpty()
                    "The fixed rate is non zero" by (irs.fixedLeg.fixedRate != FixedRate(PercentageRatioUnit("0.0")))
                    "There are events in the fix schedule" by (irs.calculation.fixedLegpaymentSchedule.size > 0)
                    "There are events in the float schedule" by (irs.calculation.floatingLegPaymentSchedule.size > 0)
                    // "There are fixes in the schedule" by (irs.calculation.floatingLegPaymentSchedule!!.size > 0)
                    // TODO: shortlist of other tests
                }
            }
            is Commands.Fix -> {
                requireThat {
                    // TODO: see previous block
                    // "There is a fixing supplied" by false // TODO
                    //  "The fixing has been signed by an appropriate oracle" by false // TODO
                    // "The fixing has arrived at the right time" by false
                    // "The net payment has been calculated" by false // TODO : Not sure if this is the right place

                }
            }
            is Commands.Pay -> {
                requireThat {
                    // TODO: see previous block
                    //"A counterparty must be making a payment" by false // TODO
                    // "The right counterparty must be receiving the payment" by false // TODO
                }
            }
            else -> throw IllegalArgumentException("Unrecognised verifiable command: ${command.value}")
        }
    }

    interface Commands : CommandData {
        class Fix : TypeOnlyCommandData(), Commands    // Receive interest rate from oracle, Both sides agree
        class Pay : TypeOnlyCommandData(), Commands    // Not implemented just yet
        class Agree : TypeOnlyCommandData(), Commands  // Both sides agree to trade
        class Mature : TypeOnlyCommandData(), Commands // Trade has matured; no more actions. Cleanup. // TODO: Do we need this?
    }

    /**
     * The state class contains the 4 major data classes
     */
    data class State(
            val fixedLeg: FixedLeg,
            val floatingLeg: FloatingLeg,
            val calculation: Calculation,
            val common: Common
    ) : ContractState {
        override val contract = IRS_PROGRAM_ID

        /**
         * For evaluating arbitrary java on the platform
         */

        fun evaluateCalculation(businessDate: LocalDate, expression: Expression = calculation.expression): Any {
            // TODO: Jexl is purely for prototyping. It may be replaced
            // TODO: Whatever we do use must be secure and sandboxed
            var jexl = JexlBuilder().create()
            var expr = jexl.createExpression(expression.expr)
            var jc = MapContext()
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
        fun prettyPrint(): String = toString().replace(",", "\n")
    }

    /**
     *  This generates the agreement state and also the schedules from the initial data.
     *  Note: The day count, interest rate calculation etc are not finished yet, but they are demonstrable.
     */
    fun generateAgreement(floatingLeg: FloatingLeg, fixedLeg: FixedLeg, calculation: Calculation, common: Common): TransactionBuilder {

        val fixedLegPaymentSchedule = HashMap<LocalDate, FixedRatePaymentEvent>()
        var dates = BusinessCalendar.createGenericSchedule(fixedLeg.effectiveDate, fixedLeg.paymentFrequency, fixedLeg.paymentCalendar, fixedLeg.rollConvention, endDate = fixedLeg.terminationDate)
        var periodStartDate = fixedLeg.effectiveDate

        // Create a schedule for the fixed payments
        for (periodEndDate in dates) {
            val paymentEvent = FixedRatePaymentEvent(
                    // TODO: We are assuming the payment date is the end date of the accrual period.
                    periodEndDate, periodStartDate, periodEndDate,
                    fixedLeg.dayCountBasisDay,
                    fixedLeg.dayCountBasisYear,
                    fixedLeg.notional,
                    fixedLeg.fixedRate
            )
            fixedLegPaymentSchedule[periodEndDate] = paymentEvent
            periodStartDate = periodEndDate
        }

        dates = BusinessCalendar.createGenericSchedule(floatingLeg.effectiveDate,
                floatingLeg.fixingsPerPayment,
                floatingLeg.fixingCalendar,
                floatingLeg.rollConvention,
                endDate = floatingLeg.terminationDate)

        var floatingLegPaymentSchedule: MutableMap<LocalDate, FloatingRatePaymentEvent> = HashMap()
        periodStartDate = floatingLeg.effectiveDate

        // TODO: Temporary until implemented via Rates Oracle.
        val telerate = TelerateOracle("3750")

        // Now create a schedule for the floating and fixes.
        for (periodEndDate in dates) {
            val paymentEvent = FloatingRatePaymentEvent(
                    periodEndDate,
                    periodStartDate,
                    periodEndDate,
                    floatingLeg.dayCountBasisDay,
                    floatingLeg.dayCountBasisYear,
                    calcFixingDate(periodStartDate, floatingLeg.fixingPeriod, floatingLeg.fixingCalendar),
                    floatingLeg.notional,
                    // TODO: OracleRetrievableReferenceRate will be replaced via oracle v soon.
                    OracleRetrievableReferenceRate(telerate, floatingLeg.indexTenor, floatingLeg.index)
            )

            floatingLegPaymentSchedule.put(periodEndDate, paymentEvent)
            periodStartDate = periodEndDate
        }

        val newCalculation = Calculation(calculation.expression, floatingLegPaymentSchedule, fixedLegPaymentSchedule)

        // Put all the above into a new State object.
        val state = State(fixedLeg, floatingLeg, newCalculation, common)
        return TransactionBuilder().withItems(state, Command(Commands.Agree(), listOf(state.floatingLeg.floatingRatePayer.owningKey, state.fixedLeg.fixedRatePayer.owningKey)))
    }

    private fun calcFixingDate(date: LocalDate, fixingPeriod: DateOffset, calendar: BusinessCalendar): LocalDate {
        return when (fixingPeriod) {
            DateOffset.ZERO -> date
            DateOffset.TWODAYS -> calendar.moveBusinessDays(date, DateRollDirection.BACKWARD, 2)
            else -> TODO("Improved fixing date calculation logic")
        }
    }

    // TODO: Replace with rates oracle
    fun generateFix(tx: TransactionBuilder, irs: StateAndRef<State>, fixing: Pair<LocalDate, Rate>) {
        tx.addInputState(irs.ref)
        tx.addOutputState(irs.state.copy(calculation = irs.state.calculation.applyFixing(fixing.first, fixing.second)))
        tx.addCommand(Commands.Fix(), listOf(irs.state.floatingLeg.floatingRatePayer.owningKey, irs.state.fixedLeg.fixedRatePayer.owningKey))
    }
}
