package contracts

import core.*
import java.math.BigDecimal
import java.security.PublicKey


// Things in here will move to the general utils class when we've hammered out various discussions regarding amounts, dates, oracle etc.

/**
 * A utility class to prevent the various mixups between percentages, decimals, bips etc.
 */
open class RatioUnit(value: BigDecimal) { // TODO: Discuss this type
    val value = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RatioUnit

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

}

/**
 * A class to reprecent a percentage in an unambiguous way.
 */
open class PercentageRatioUnit(percentageAsString: String) : RatioUnit(BigDecimal(percentageAsString).divide(BigDecimal("100"))) {
    override fun toString(): String = value.times(BigDecimal(100)).toString() + "%"
}

/**
 * For the convenience of writing "5".percent
 * Note that we do not currently allow 10.percent (ie no quotes) as this might get a little confusing if
 * 0.1.percent was written  TODO: Discuss
 */
val String.percent: PercentageRatioUnit get() = PercentageRatioUnit(this)

/**
 * Interface representing an agreement that exposes various attributes that are common. Implementing it simplifies
 * implementation of general protocols that manipulate many agreement types.
 */
interface DealState : LinearState {

    /** Human readable well known reference (e.g. trade reference) */
    val ref: String

    /** Exposes the Parties involved in a generic way */
    val parties: Array<Party>

    // TODO: This works by editing the keys used by a Party which is invalid.
    fun withPublicKey(before: Party, after: PublicKey): DealState

    /**
     * Generate a partial transaction representing an agreement (command) to this deal, allowing a general
     * deal/agreement protocol to generate the necessary transaction for potential implementations
     *
     * TODO: Currently this is the "inception" transaction but in future an offer of some description might be an input state ref
     *
     * TODO: This should more likely be a method on the Contract (on a common interface) and the changes to reference a
     * Contract instance from a ContractState are imminent, at which point we can move this out of here
     */
    fun generateAgreement(): TransactionBuilder
}

/**
 * Interface adding fixing specific methods
 */
interface FixableDealState : DealState {
    /**
     * When is the next fixing and what is the fixing for?
     *
     * TODO: In future we would use this to register for an event to trigger a/the fixing protocol
     */
    fun nextFixingOf(): FixOf?

    /**
     * Generate a fixing command for this deal and fix
     *
     * TODO: This would also likely move to methods on the Contract once the changes to reference
     * the Contract from the ContractState are in
     */
    fun generateFix(ptx: TransactionBuilder, oldStateRef: StateRef, fix: Fix)
}

/**
 * Parent of the Rate family. Used to denote fixed rates, floating rates, reference rates etc
 */
open class Rate(val ratioUnit: RatioUnit? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Rate

        if (ratioUnit != other.ratioUnit) return false

        return true
    }

    /**
     * @returns the hash code of the ratioUnit or zero if the ratioUnit is null, as is the case for floating rate fixings
     * that have not yet happened.  Yet-to-be fixed floating rates need to be equal such that schedules can be tested
     * for equality.
     */
    override fun hashCode(): Int {
        return ratioUnit?.hashCode() ?: 0
    }
}

/**
 * A very basic subclass to represent a fixed rate.
 */
class FixedRate(ratioUnit: RatioUnit) : Rate(ratioUnit) {
    override fun toString(): String = "$ratioUnit"
}

/**
 * The parent class of the Floating rate classes
 */
open class FloatingRate : Rate(null)

/**
 * So a reference rate is a rate that takes its value from a source at a given date
 * e.g. LIBOR 6M as of 17 March 2016. Hence it requires a source (name) and a value date in the getAsOf(..) method.
 */
class ReferenceRate(val oracle: String, val tenor: Tenor, val name: String) : FloatingRate() {
    override fun toString(): String = "$name - $tenor"
}

// TODO: For further discussion.
operator fun Amount.times(other: RatioUnit): Amount = Amount((BigDecimal(this.pennies).multiply(other.value)).longValueExact(), this.currency)
//operator fun Amount.times(other: FixedRate): Amount = Amount((BigDecimal(this.pennies).multiply(other.value)).longValueExact(), this.currency)
//fun Amount.times(other: InterestRateSwap.RatioUnit): Amount = Amount((BigDecimal(this.pennies).multiply(other.value)).longValueExact(), this.currency)

operator fun kotlin.Int.times(other: FixedRate): Int = BigDecimal(this).multiply(other.ratioUnit!!.value).intValueExact()
operator fun Int.times(other: Rate): Int = BigDecimal(this).multiply(other.ratioUnit!!.value).intValueExact()
operator fun Int.times(other: RatioUnit): Int = BigDecimal(this).multiply(other.value).intValueExact()
