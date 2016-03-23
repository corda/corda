package contracts

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.databind.type.SimpleType
import core.Amount
import core.Tenor
import java.math.BigDecimal
import java.time.LocalDate


// Things in here will move to the general utils class when we've hammered out various discussions regarding amounts, dates, oracle etc.

/**
 * A utility class to prevent the various mixups between percentages, decimals, bips etc.
 */
open class RatioUnit(value: BigDecimal) { // TODO: Discuss this type
    val value = value
}

/**
 * A class to reprecent a percentage in an unambiguous way.
 */
open class PercentageRatioUnit(percentageAsString: String) : RatioUnit(BigDecimal(percentageAsString).divide(BigDecimal("100"))) {
    override fun toString(): String = value.times(BigDecimal(100)).toString()+"%"
}

/**
 * For the convenience of writing "5".percent
 * Note that we do not currently allow 10.percent (ie no quotes) as this might get a little confusing if
 * 0.1.percent was written  TODO: Discuss
 */
val String.percent: PercentageRatioUnit get() = PercentageRatioUnit(this)

/**
 * Parent of the Rate family. Used to denote fixed rates, floating rates, reference rates etc
 */
open class Rate(val ratioUnit: RatioUnit? = null)

/**
 * A very basic subclass to represent a fixed rate.
 */
class FixedRate(ratioUnit: RatioUnit) : Rate(ratioUnit) {
    override fun toString(): String = "$ratioUnit"
}

/**
 * The parent class of the Floating rate classes
 */
open class FloatingRate: Rate(null)

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
