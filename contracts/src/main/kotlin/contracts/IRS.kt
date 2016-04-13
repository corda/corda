package contracts

import core.*
import core.crypto.SecureHash
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.PublicKey
import java.time.LocalDate
import java.util.*

val IRS_PROGRAM_ID = InterestRateSwap()

// This is a placeholder for some types that we haven't identified exactly what they are just yet for things still in discussion
open class UnknownType() {

    override fun equals(other: Any?): Boolean {
        return (other is UnknownType)
    }

    override fun hashCode(): Int {
        return 1
    }
}

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
        calculateDaysBetween(accrualStartDate, accrualEndDate, dayCountBasisYear, dayCountBasisDay)

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

    /**
     * The Calculation data class is "mutable" through out the life of the swap, as in, it's the only thing that contains
     * data that will changed from state to state (Recall that the design insists that everything is immutable, so we actually
     * copy / update for each transition)
     */
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
                    fixedLegPaymentSchedule = fixedLegPaymentSchedule)
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

        override fun hashCode(): Int {
            var result = notional.hashCode()
            result += 31 * result + paymentFrequency.hashCode()
            result += 31 * result + effectiveDate.hashCode()
            result += 31 * result + (effectiveDateAdjustment?.hashCode() ?: 0)
            result += 31 * result + terminationDate.hashCode()
            result += 31 * result + (terminationDateAdjustment?.hashCode() ?: 0)
            result += 31 * result + dayCountBasisDay.hashCode()
            result += 31 * result + dayCountBasisYear.hashCode()
            result += 31 * result + dayInMonth
            result += 31 * result + paymentRule.hashCode()
            result += 31 * result + paymentDelay
            result += 31 * result + paymentCalendar.hashCode()
            result += 31 * result + interestPeriodAdjustment.hashCode()
            return result
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

        override fun hashCode(): Int {
            var result = super.hashCode()
            result += 31 * result + fixedRatePayer.hashCode()
            result += 31 * result + fixedRate.hashCode()
            result += 31 * result + rollConvention.hashCode()
            return result
        }

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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            if (!super.equals(other)) return false

            other as FloatingLeg

            if (floatingRatePayer != other.floatingRatePayer) return false
            if (rollConvention != other.rollConvention) return false
            if (fixingRollConvention != other.fixingRollConvention) return false
            if (resetDayInMonth != other.resetDayInMonth) return false
            if (fixingPeriod != other.fixingPeriod) return false
            if (resetRule != other.resetRule) return false
            if (fixingsPerPayment != other.fixingsPerPayment) return false
            if (fixingCalendar != other.fixingCalendar) return false
            if (index != other.index) return false
            if (indexSource != other.indexSource) return false
            if (indexTenor != other.indexTenor) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result += 31 * result + floatingRatePayer.hashCode()
            result += 31 * result + rollConvention.hashCode()
            result += 31 * result + fixingRollConvention.hashCode()
            result += 31 * result + resetDayInMonth
            result += 31 * result + fixingPeriod.hashCode()
            result += 31 * result + resetRule.hashCode()
            result += 31 * result + fixingsPerPayment.hashCode()
            result += 31 * result + fixingCalendar.hashCode()
            result += 31 * result + index.hashCode()
            result += 31 * result + indexSource.hashCode()
            result += 31 * result + indexTenor.hashCode()
            return result
        }

    }

    /**
     * verify() with a few examples of what needs to be checked. TODO: Lots more to add.
     */
    override fun verify(tx: TransactionForVerification) {
        val command = tx.commands.requireSingleCommand<InterestRateSwap.Commands>()
        val time = tx.commands.getTimestampByName("Mock Company 0", "Timestamping Service", "Bank A")?.midpoint
        if (time == null) throw IllegalArgumentException("must be timestamped")

        val irs = tx.outStates.filterIsInstance<InterestRateSwap.State>().single()
        when (command.value) {
            is Commands.Agree -> {
                requireThat {
                    "There are no in states for an agreement" by tx.inStates.isEmpty()
                    "The fixed rate is non zero" by (irs.fixedLeg.fixedRate != FixedRate(PercentageRatioUnit("0.0")))
                    "There are events in the fix schedule" by (irs.calculation.fixedLegPaymentSchedule.size > 0)
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
    ) : FixableDealState {

        override val contract = IRS_PROGRAM_ID
        override val thread = SecureHash.sha256(common.tradeID)
        override val ref = common.tradeID

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return (fixedLeg.fixedRatePayer.owningKey in ourKeys) || (floatingLeg.floatingRatePayer.owningKey in ourKeys)
        }

        override val parties: Array<Party>
            get() = arrayOf(fixedLeg.fixedRatePayer, floatingLeg.floatingRatePayer)

        // TODO: This changing of the public key violates the assumption that Party is a fixed identity key.
        override fun withPublicKey(before: Party, after: PublicKey): DealState {
            val newParty = Party(before.name, after)
            if (before == fixedLeg.fixedRatePayer) {
                val deal = copy()
                deal.fixedLeg.fixedRatePayer = newParty
                return deal
            } else if (before == floatingLeg.floatingRatePayer) {
                val deal = copy()
                deal.floatingLeg.floatingRatePayer = newParty
                return deal
            } else {
                throw IllegalArgumentException("No such party: $before")
            }
        }

        override fun generateAgreement(): TransactionBuilder = InterestRateSwap().generateAgreement(floatingLeg, fixedLeg, calculation, common)

        override fun generateFix(ptx: TransactionBuilder, oldStateRef: StateRef, fix: Fix) {
            InterestRateSwap().generateFix(ptx, StateAndRef(this, oldStateRef), Pair(fix.of.forDay, Rate(RatioUnit(fix.value))))
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
                    ReferenceRate(floatingLeg.indexSource, floatingLeg.indexTenor, floatingLeg.index)
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
