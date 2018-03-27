package net.corda.irs.contract

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.corda.core.contracts.Amount
import net.corda.annotations.serialization.CordaSerializable
import net.corda.finance.contracts.Tenor
import java.math.BigDecimal
import java.util.*


// Things in here will move to the general utils class when we've hammered out various discussions regarding amounts, dates, oracle etc.

/**
 * A utility class to prevent the various mixups between percentages, decimals, bips etc.
 */
@CordaSerializable
open class RatioUnit(val value: BigDecimal) { // TODO: Discuss this type
    override fun equals(other: Any?) = (other as? RatioUnit)?.value == value
    override fun hashCode() = value.hashCode()
    override fun toString() = value.toString()
}

/**
 * A class to represent a percentage in an unambiguous way.
 */
open class PercentageRatioUnit(val percentageAsString: String) : RatioUnit(BigDecimal(percentageAsString).divide(BigDecimal("100"))) {
    override fun toString() = value.times(BigDecimal(100)).toString() + "%"
}

/**
 * For the convenience of writing "5".percent
 * Note that we do not currently allow 10.percent (ie no quotes) as this might get a little confusing if 0.1.percent was
 * written. Additionally, there is a possibility of creating a precision error in the implicit conversion.
 */
val String.percent: PercentageRatioUnit get() = PercentageRatioUnit(this)

/**
 * Parent of the Rate family. Used to denote fixed rates, floating rates, reference rates etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@CordaSerializable
open class Rate(val ratioUnit: RatioUnit? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Rate

        if (ratioUnit != other.ratioUnit) return false

        return true
    }

    /**
     * @return the hash code of the ratioUnit or zero if the ratioUnit is null, as is the case for floating rate fixings
     * that have not yet happened.  Yet-to-be fixed floating rates need to be equal such that schedules can be tested
     * for equality.
     */
    override fun hashCode() = ratioUnit?.hashCode() ?: 0

    override fun toString() = ratioUnit.toString()
}

/**
 * A very basic subclass to represent a fixed rate.
 */
@CordaSerializable
class FixedRate(ratioUnit: RatioUnit) : Rate(ratioUnit) {
    @JsonIgnore
    fun isPositive(): Boolean = ratioUnit!!.value > BigDecimal("0.0")

    override fun equals(other: Any?) = other?.javaClass == javaClass && super.equals(other)
}

/**
 * The parent class of the Floating rate classes.
 */
@CordaSerializable
open class FloatingRate : Rate(null)

/**
 * So a reference rate is a rate that takes its value from a source at a given date
 * e.g. LIBOR 6M as of 17 March 2016. Hence it requires a source (name) and a value date in the getAsOf(..) method.
 */
class ReferenceRate(val oracle: String, val tenor: Tenor, val name: String) : FloatingRate() {
    override fun toString(): String = "$name - $tenor"
}

// TODO: For further discussion.
operator fun Amount<Currency>.times(other: RatioUnit): Amount<Currency> = Amount((BigDecimal(this.quantity).multiply(other.value)).longValueExact(), this.token)
//operator fun Amount<Currency>.times(other: FixedRate): Amount<Currency> = Amount<Currency>((BigDecimal(this.pennies).multiply(other.value)).longValueExact(), this.currency)
//fun Amount<Currency>.times(other: InterestRateSwap.RatioUnit): Amount<Currency> = Amount<Currency>((BigDecimal(this.pennies).multiply(other.value)).longValueExact(), this.currency)

operator fun kotlin.Int.times(other: FixedRate): Int = BigDecimal(this).multiply(other.ratioUnit!!.value).intValueExact()
operator fun Int.times(other: Rate): Int = BigDecimal(this).multiply(other.ratioUnit!!.value).intValueExact()
operator fun Int.times(other: RatioUnit): Int = BigDecimal(this).multiply(other.value).intValueExact()
