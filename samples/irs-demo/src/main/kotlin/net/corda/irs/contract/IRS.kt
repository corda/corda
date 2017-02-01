package net.corda.irs.contract

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.node.services.ServiceType
import net.corda.core.transactions.TransactionBuilder
import net.corda.irs.flows.FixingFlow
import net.corda.irs.utilities.suggestInterestRateAnnouncementTimeWindow
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

    val days: Int get() = calculateDaysBetween(accrualStartDate, accrualEndDate, dayCountBasisYear, dayCountBasisDay)

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

    override val flow: Amount<Currency> get() {
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
class InterestRateSwap() : Contract {
    override val legalContractReference = SecureHash.sha256("is_this_the_text_of_the_contract ? TBD")

    companion object {
        val oracleType = ServiceType.corda.getSubType("interest_rates")
    }

    /**
     * This Common area contains all the information that is not leg specific.
     */
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

    open class FixedLeg(
            var fixedRatePayer: Party.Anonymised,
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
        fun copy(fixedRatePayer: Party.Anonymised = this.fixedRatePayer,
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

    open class FloatingLeg(
            var floatingRatePayer: Party.Anonymised,
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


        fun copy(floatingRatePayer: Party.Anonymised = this.floatingRatePayer,
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

    override fun verify(tx: TransactionForContract) = verifyClause(tx, AllComposition(Clauses.Timestamped(), Clauses.Group()), tx.commands.select<Commands>())

    interface Clauses {
        /**
         * Common superclass for IRS contract clauses, which defines behaviour on match/no-match, and provides
         * helper functions for the clauses.
         */
        abstract class AbstractIRSClause : Clause<State, Commands, UniqueIdentifier>() {
            // These functions may make more sense to use for basket types, but for now let's leave them here
            fun checkLegDates(legs: List<CommonLeg>) {
                requireThat {
                    "Effective date is before termination date" by legs.all { it.effectiveDate < it.terminationDate }
                    "Effective dates are in alignment" by legs.all { it.effectiveDate == legs[0].effectiveDate }
                    "Termination dates are in alignment" by legs.all { it.terminationDate == legs[0].terminationDate }
                }
            }

            fun checkLegAmounts(legs: List<CommonLeg>) {
                requireThat {
                    "The notional is non zero" by legs.any { it.notional.quantity > (0).toLong() }
                    "The notional for all legs must be the same" by legs.all { it.notional == legs[0].notional }
                }
                for (leg: CommonLeg in legs) {
                    if (leg is FixedLeg) {
                        requireThat {
                            // TODO: Confirm: would someone really enter a swap with a negative fixed rate?
                            "Fixed leg rate must be positive" by leg.fixedRate.isPositive()
                        }
                    }
                }
            }

            // TODO: After business rules discussion, add further checks to the schedules and rates
            fun checkSchedules(@Suppress("UNUSED_PARAMETER") legs: List<CommonLeg>): Boolean = true

            fun checkRates(@Suppress("UNUSED_PARAMETER") legs: List<CommonLeg>): Boolean = true

            /**
             * Compares two schedules of Floating Leg Payments, returns the difference (i.e. omissions in either leg or changes to the values).
             */
            fun getFloatingLegPaymentsDifferences(payments1: Map<LocalDate, Event>, payments2: Map<LocalDate, Event>): List<Pair<LocalDate, Pair<FloatingRatePaymentEvent, FloatingRatePaymentEvent>>> {
                val diff1 = payments1.filter { payments1[it.key] != payments2[it.key] }
                val diff2 = payments2.filter { payments1[it.key] != payments2[it.key] }
                return (diff1.keys + diff2.keys).map {
                    it to Pair(diff1[it] as FloatingRatePaymentEvent, diff2[it] as FloatingRatePaymentEvent)
                }
            }
        }

        class Group : GroupClauseVerifier<State, Commands, UniqueIdentifier>(AnyComposition(Agree(), Fix(), Pay(), Mature())) {
            // Group by Trade ID for in / out states
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<State, UniqueIdentifier>> {
                return tx.groupStates() { state -> state.linearId }
            }
        }

        class Timestamped : Clause<ContractState, Commands, Unit>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(tx.timestamp?.midpoint != null) { "must be timestamped" }
                // We return an empty set because we don't process any commands
                return emptySet()
            }
        }

        class Agree : AbstractIRSClause() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Agree::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Agree>()
                val irs = outputs.filterIsInstance<State>().single()
                requireThat {
                    "There are no in states for an agreement" by inputs.isEmpty()
                    "There are events in the fix schedule" by (irs.calculation.fixedLegPaymentSchedule.size > 0)
                    "There are events in the float schedule" by (irs.calculation.floatingLegPaymentSchedule.size > 0)
                    "All notionals must be non zero" by (irs.fixedLeg.notional.quantity > 0 && irs.floatingLeg.notional.quantity > 0)
                    "The fixed leg rate must be positive" by (irs.fixedLeg.fixedRate.isPositive())
                    "The currency of the notionals must be the same" by (irs.fixedLeg.notional.token == irs.floatingLeg.notional.token)
                    "All leg notionals must be the same" by (irs.fixedLeg.notional == irs.floatingLeg.notional)

                    "The effective date is before the termination date for the fixed leg" by (irs.fixedLeg.effectiveDate < irs.fixedLeg.terminationDate)
                    "The effective date is before the termination date for the floating leg" by (irs.floatingLeg.effectiveDate < irs.floatingLeg.terminationDate)
                    "The effective dates are aligned" by (irs.floatingLeg.effectiveDate == irs.fixedLeg.effectiveDate)
                    "The termination dates are aligned" by (irs.floatingLeg.terminationDate == irs.fixedLeg.terminationDate)
                    "The rates are valid" by checkRates(listOf(irs.fixedLeg, irs.floatingLeg))
                    "The schedules are valid" by checkSchedules(listOf(irs.fixedLeg, irs.floatingLeg))
                    "The fixing period date offset cannot be negative" by (irs.floatingLeg.fixingPeriodOffset >= 0)

                    // TODO: further tests
                }
                checkLegAmounts(listOf(irs.fixedLeg, irs.floatingLeg))
                checkLegDates(listOf(irs.fixedLeg, irs.floatingLeg))

                return setOf(command.value)
            }
        }

        class Fix : AbstractIRSClause() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Refix::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Refix>()
                val irs = outputs.filterIsInstance<State>().single()
                val prevIrs = inputs.filterIsInstance<State>().single()
                val paymentDifferences = getFloatingLegPaymentsDifferences(prevIrs.calculation.floatingLegPaymentSchedule, irs.calculation.floatingLegPaymentSchedule)

                // Having both of these tests are "redundant" as far as verify() goes, however, by performing both
                // we can relay more information back to the user in the case of failure.
                requireThat {
                    "There is at least one difference in the IRS floating leg payment schedules" by !paymentDifferences.isEmpty()
                    "There is only one change in the IRS floating leg payment schedule" by (paymentDifferences.size == 1)
                }

                val changedRates = paymentDifferences.single().second // Ignore the date of the changed rate (we checked that earlier).
                val (oldFloatingRatePaymentEvent, newFixedRatePaymentEvent) = changedRates
                val fixValue = command.value.fix
                // Need to check that everything is the same apart from the new fixed rate entry.
                requireThat {
                    "The fixed leg parties are constant" by (irs.fixedLeg.fixedRatePayer == prevIrs.fixedLeg.fixedRatePayer) // Although superseded by the below test, this is included for a regression issue
                    "The fixed leg is constant" by (irs.fixedLeg == prevIrs.fixedLeg)
                    "The floating leg is constant" by (irs.floatingLeg == prevIrs.floatingLeg)
                    "The common values are constant" by (irs.common == prevIrs.common)
                    "The fixed leg payment schedule is constant" by (irs.calculation.fixedLegPaymentSchedule == prevIrs.calculation.fixedLegPaymentSchedule)
                    "The expression is unchanged" by (irs.calculation.expression == prevIrs.calculation.expression)
                    "There is only one changed payment in the floating leg" by (paymentDifferences.size == 1)
                    "There changed payment is a floating payment" by (oldFloatingRatePaymentEvent.rate is ReferenceRate)
                    "The new payment is a fixed payment" by (newFixedRatePaymentEvent.rate is FixedRate)
                    "The changed payments dates are aligned" by (oldFloatingRatePaymentEvent.date == newFixedRatePaymentEvent.date)
                    "The new payment has the correct rate" by (newFixedRatePaymentEvent.rate.ratioUnit!!.value == fixValue.value)
                    "The fixing is for the next required date" by (prevIrs.calculation.nextFixingDate() == fixValue.of.forDay)
                    "The fix payment has the same currency as the notional" by (newFixedRatePaymentEvent.flow.token == irs.floatingLeg.notional.token)
                    // "The fixing is not in the future " by (fixCommand) // The oracle should not have signed this .
                }

                return setOf(command.value)
            }
        }

        class Pay : AbstractIRSClause() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Pay::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Pay>()
                requireThat {
                    "Payments not supported / verifiable yet" by false
                }
                return setOf(command.value)
            }
        }

        class Mature : AbstractIRSClause() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Mature::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Mature>()
                val irs = inputs.filterIsInstance<State>().single()
                requireThat {
                    "No more fixings to be applied" by (irs.calculation.nextFixingDate() == null)
                    "The irs is fully consumed and there is no id matched output state" by outputs.isEmpty()
                }

                return setOf(command.value)
            }
        }

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
    data class State(
            val fixedLeg: FixedLeg,
            val floatingLeg: FloatingLeg,
            val calculation: Calculation,
            val common: Common,
            override val linearId: UniqueIdentifier = UniqueIdentifier(common.tradeID)
    ) : FixableDealState, SchedulableState {

        override val contract = IRS_PROGRAM_ID

        override val oracleType: ServiceType
            get() = InterestRateSwap.oracleType

        override val ref = common.tradeID

        override val participants: List<CompositeKey>
            get() = parties.map { it.owningKey }

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return fixedLeg.fixedRatePayer.owningKey.containsAny(ourKeys) || floatingLeg.floatingRatePayer.owningKey.containsAny(ourKeys)
        }

        override val parties: List<Party.Anonymised>
            get() = listOf(fixedLeg.fixedRatePayer, floatingLeg.floatingRatePayer)

        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            val nextFixingOf = nextFixingOf() ?: return null

            // This is perhaps not how we should determine the time point in the business day, but instead expect the schedule to detail some of these aspects
            val instant = suggestInterestRateAnnouncementTimeWindow(index = nextFixingOf.name, source = floatingLeg.indexSource, date = nextFixingOf.forDay).start
            return ScheduledActivity(flowLogicRefFactory.create(FixingFlow.FixingRoleDecider::class.java, thisStateRef), instant)
        }

        override fun generateAgreement(notary: Party.Full): TransactionBuilder = InterestRateSwap().generateAgreement(floatingLeg, fixedLeg, calculation, common, notary)

        override fun generateFix(ptx: TransactionBuilder, oldState: StateAndRef<*>, fix: Fix) {
            InterestRateSwap().generateFix(ptx, StateAndRef(TransactionState(this, oldState.state.notary), oldState.ref), fix)
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
                          common: Common, notary: Party.Full): TransactionBuilder {

        val fixedLegPaymentSchedule = LinkedHashMap<LocalDate, FixedRatePaymentEvent>()
        var dates = BusinessCalendar.createGenericSchedule(fixedLeg.effectiveDate, fixedLeg.paymentFrequency, fixedLeg.paymentCalendar, fixedLeg.rollConvention, endDate = fixedLeg.terminationDate)
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
        val state = State(fixedLeg, floatingLeg, newCalculation, common)
        return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Agree(), listOf(state.floatingLeg.floatingRatePayer.owningKey, state.fixedLeg.fixedRatePayer.owningKey)))
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
                irs.state.notary
        )
        tx.addCommand(Commands.Refix(fixing), listOf(irs.state.data.floatingLeg.floatingRatePayer.owningKey, irs.state.data.fixedLeg.fixedRatePayer.owningKey))
    }
}
