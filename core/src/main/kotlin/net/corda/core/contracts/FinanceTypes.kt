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
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * This interface is used by [Amount] to determine the conversion ratio from
 * indicative/displayed asset amounts in [BigDecimal] to fungible tokens represented by Amount objects.
 */
interface TokenizableAssetInfo {
    val displayTokenSize: BigDecimal
}

/**
 * Amount represents a positive quantity of some token (currency, asset, etc.), measured in quantity of the smallest
 * representable units. The nominal quantity represented by each individual token is equal to the [displayTokenSize].
 * The scale property of the [displayTokenSize] should correctly reflect the displayed decimal places and is used
 * when rounding conversions from indicative/displayed amounts in [BigDecimal] to Amount occur via the Amount.fromDecimal method.
 *
 * Amounts of different tokens *do not mix* and attempting to add or subtract two amounts of different currencies
 * will throw [IllegalArgumentException]. Amounts may not be negative. Amounts are represented internally using a signed
 * 64 bit value, therefore, the maximum expressable amount is 2^63 - 1 == Long.MAX_VALUE. Addition, subtraction and
 * multiplication are overflow checked and will throw [ArithmeticException] if the operation would have caused integer
 * overflow.
 *
 * @param quantity the number of tokens as a Long value.
 * @param displayTokenSize the nominal display unit size of a single token,
 * potentially with trailing decimal display places if the scale parameter is non-zero.
 * @param T the type of the token, for example [Currency].
 * T should implement TokenizableAssetInfo if automatic conversion to/from a display format is required.
 *
 * TODO Proper lookup of currencies in a locale and context sensitive fashion is not supported and is left to the application.
 */
@CordaSerializable
data class Amount<T : Any>(val quantity: Long, val displayTokenSize: BigDecimal, val token: T) : Comparable<Amount<T>> {
    companion object {
        /**
         * Build an Amount from a decimal representation. For example, with an input of "12.34 GBP",
         * returns an amount with a quantity of "1234" tokens. The displayTokenSize as determined via
         * getDisplayTokenSize is used to determine the conversion scaling.
         * e.g. Bonds might be in nominal amounts of 100, currencies in 0.01 penny units.
         *
         * @see Amount<Currency>.toDecimal
         * @throws ArithmeticException if the intermediate calculations cannot be converted to an unsigned 63-bit token amount.
         */
        @JvmStatic
        @JvmOverloads
        fun <T : Any> fromDecimal(displayQuantity: BigDecimal, token: T, rounding: RoundingMode = RoundingMode.FLOOR): Amount<T> {
            val tokenSize = getDisplayTokenSize(token)
            val tokenCount = displayQuantity.divide(tokenSize).setScale(0, rounding).longValueExact()
            return Amount(tokenCount, tokenSize, token)
        }

        /**
         * For a particular token returns a zero sized Amount<T>
         */
        @JvmStatic
        fun <T : Any> zero(token: T): Amount<T> {
            val tokenSize = getDisplayTokenSize(token)
            return Amount(0L, tokenSize, token)
        }


        /**
         * Determines the representation of one Token quantity in BigDecimal. For Currency and Issued<Currency>
         * the definitions is taken from Currency defaultFractionDigits property e.g. 2 for USD, or 0 for JPY
         * so that the automatic token size is the conventional minimum penny amount.
         * For other possible token types the asset token should implement TokenizableAssetInfo to
         * correctly report the designed nominal amount.
         */
        fun getDisplayTokenSize(token: Any): BigDecimal {
            if (token is TokenizableAssetInfo) {
                return token.displayTokenSize
            }
            if (token is Currency) {
                return BigDecimal.ONE.scaleByPowerOfTen(-token.defaultFractionDigits)
            }
            if (token is Issued<*>) {
                return getDisplayTokenSize(token.product)
            }
            return BigDecimal.ONE
        }

        private val currencySymbols: Map<String, Currency> = mapOf(
                "$" to USD,
                "£" to GBP,
                "€" to EUR,
                "¥" to JPY,
                "₽" to RUB
        )
        private val currencyCodes: Map<String, Currency> by lazy { Currency.getAvailableCurrencies().map { it.currencyCode to it }.toMap() }

        /**
         * Returns an amount that is equal to the given currency amount in text. Examples of what is supported:
         *
         * - 12 USD
         * - 14.50 USD
         * - 10 USD
         * - 30 CHF
         * - $10.24
         * - £13
         * - €5000
         *
         * Note this method does NOT respect internationalisation rules: it ignores commas and uses . as the
         * decimal point separator, always. It also ignores the users locale:
         *
         * - $ is always USD,
         * - £ is always GBP
         * - € is always the Euro
         * - ¥ is always Japanese Yen.
         * - ₽ is always the Russian ruble.
         *
         * Thus an input of $12 expecting some other countries dollar will not work. Do your own parsing if
         * you need correct handling of currency amounts with locale-sensitive handling.
         *
         * @throws IllegalArgumentException if the input string was not understood.
         */
        fun parseCurrency(input: String): Amount<Currency> {
            val i = input.filter { it != ',' }
            try {
                // First check the symbols at the front.
                for ((symbol, currency) in currencySymbols) {
                    if (i.startsWith(symbol)) {
                        val rest = i.substring(symbol.length)
                        return fromDecimal(BigDecimal(rest), currency)
                    }
                }
                // Now check the codes at the end.
                val split = i.split(' ')
                if (split.size == 2) {
                    val (rest, code) = split
                    for ((cc, currency) in currencyCodes) {
                        if (cc == code) {
                            return fromDecimal(BigDecimal(rest), currency)
                        }
                    }
                }
            } catch(e: Exception) {
                throw IllegalArgumentException("Could not parse $input as a currency", e)
            }
            throw IllegalArgumentException("Did not recognise the currency in $input or could not parse")
        }
    }

    init {
        // Amount represents a static balance of physical assets as managed by the distributed ledger and is not allowed
        // to become negative a rule further maintained by the Contract verify method.
        // N.B. If concepts such as an account overdraft are required this should be modelled separately via Obligations,
        // or similar second order smart contract concepts.
        require(quantity >= 0) { "Negative amounts are not allowed: $quantity" }
    }

    /**
     * Automatic conversion constructor from number of tokens to an Amount using getDisplayTokenSize to determine
     * the displayTokenSize.
     *
     * @param tokenQuantity the number of tokens represented.
     * @param token the type of the token, for example a [Currency] object.
     */
    constructor(tokenQuantity: Long, token: T) : this(tokenQuantity, getDisplayTokenSize(token), token)

    /**
     * A checked addition operator is supported to simplify aggregation of Amounts.
     * @throws ArithmeticException if there is overflow of Amount tokens during the summation
     * Mixing non-identical token types will throw [IllegalArgumentException]
     */
    operator fun plus(other: Amount<T>): Amount<T> {
        checkToken(other)
        return Amount(Math.addExact(quantity, other.quantity), displayTokenSize, token)
    }

    /**
     * A checked addition operator is supported to simplify netting of Amounts.
     * If this leads to the Amount going negative this will throw [IllegalArgumentException].
     * @throws ArithmeticException if there is Numeric underflow
     * Mixing non-identical token types will throw [IllegalArgumentException]
     */
    operator fun minus(other: Amount<T>): Amount<T> {
        checkToken(other)
        return Amount(Math.subtractExact(quantity, other.quantity), displayTokenSize, token)
    }

    private fun checkToken(other: Amount<T>) {
        require(other.token == token) { "Token mismatch: ${other.token} vs $token" }
        require(other.displayTokenSize == displayTokenSize) { "Token size mismatch: ${other.displayTokenSize} vs $displayTokenSize" }
    }

    /**
     * The multiplication operator is supported to allow easy calculation for multiples of a primitive Amount.
     * Note this is not a conserving operation, so it may not always be correct modelling of proper token behaviour.
     * N.B. Division is not supported as fractional tokens are not representable by an Amount.
     */
    operator fun times(other: Long): Amount<T> = Amount(Math.multiplyExact(quantity, other), displayTokenSize, token)

    operator fun times(other: Int): Amount<T> = Amount(Math.multiplyExact(quantity, other.toLong()), displayTokenSize, token)

    /**
     * This method provides a token conserving divide mechanism.
     * @param partitions the number of amounts to divide the current quantity into.
     * @result Returns [partitions] separate Amount objects which sum to the same quantity as this Amount
     * and differ by no more than a single token in size.
     */
    fun splitEvenly(partitions: Int): List<Amount<T>> {
        require(partitions >= 1) { "Must split amount into one, or more pieces" }
        val commonTokensPerPartition = quantity.div(partitions)
        val residualTokens = quantity - (commonTokensPerPartition * partitions)
        val splitAmount = Amount(commonTokensPerPartition, displayTokenSize, token)
        val splitAmountPlusOne = Amount(commonTokensPerPartition + 1L, displayTokenSize, token)
        return (0..partitions - 1).map { if (it < residualTokens) splitAmountPlusOne else splitAmount }.toList()
    }

    /**
     * Convert a currency [Amount] to a decimal representation. For example, with an amount with a quantity
     * of "1234" GBP, returns "12.34". The precise representation is controlled by the displayTokenSize,
     * which determines the size of a single token and controls the trailing decimal places via it's scale property.
     *
     * @see Amount.Companion.fromDecimal
     */
    fun toDecimal(): BigDecimal = BigDecimal.valueOf(quantity, 0) * displayTokenSize


    /**
     * Convert a currency [Amount] to a display string representation.
     *
     * For example, with an amount with a quantity of "1234" GBP, returns "12.34 GBP".
     * The result of fromDecimal is used to control the numerical formatting and
     * the token specifier appended is taken from token.toString.
     *
     * @see Amount.Companion.fromDecimal
     */
    override fun toString(): String {
        return toDecimal().toPlainString() + " " + token
    }

    override fun compareTo(other: Amount<T>): Int {
        checkToken(other)
        return quantity.compareTo(other.quantity)
    }
}


fun <T : Any> Iterable<Amount<T>>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()
fun <T : Any> Iterable<Amount<T>>.sumOrThrow() = reduce { left, right -> left + right }
fun <T : Any> Iterable<Amount<T>>.sumOrZero(token: T) = if (iterator().hasNext()) sumOrThrow() else Amount.zero(token)


/**
 * Simple data class to associate the origin, owner, or holder of a particular Amount object.
 * @param source the holder of the Amount.
 * @param amount the Amount of asset available.
 * @param ref is an optional field used for housekeeping in the caller.
 * e.g. to point back at the original Vault state objects.
 * @see SourceAndAmount.apply which processes a list of SourceAndAmount objects
 * and calculates the resulting Amount distribution as a new list of SourceAndAmount objects.
 */
data class SourceAndAmount<T : Any, out P : Any>(val source: P, val amount: Amount<T>, val ref: Any? = null)

/**
 * This class represents a possibly negative transfer of tokens from one vault state to another, possibly at a future date.
 *
 * @param quantityDelta is a signed Long value representing the exchanged number of tokens. If positive then
 * it represents the movement of Math.abs(quantityDelta) tokens away from source and receipt of Math.abs(quantityDelta)
 * at the destination. If the quantityDelta is negative then the source will receive Math.abs(quantityDelta) tokens
 * and the destination will lose Math.abs(quantityDelta) tokens.
 * Where possible the source and destination should be coded to ensure a positive quantityDelta,
 * but in various scenarios it may be more consistent to allow positive and negative values.
 * For example it is common for a bank to code asset flows as gains and losses from its perspective i.e. always the destination.
 * @param token represents the type of asset token as would be used to construct Amount<T> objects.
 * @param source is the [Party], [Account], [CompositeKey], or other identifier of the token source if quantityDelta is positive,
 * or the token sink if quantityDelta is negative. The type P should support value equality.
 * @param destination is the [Party], [Account], [CompositeKey], or other identifier of the token sink if quantityDelta is positive,
 * or the token source if quantityDelta is negative. The type P should support value equality.
 */
@CordaSerializable
class AmountTransfer<T : Any, P : Any>(val quantityDelta: Long,
                                       val token: T,
                                       val source: P,
                                       val destination: P) {
    companion object {
        /**
         * Construct an AmountTransfer object from an indicative/displayable BigDecimal source, applying rounding as specified.
         * The token size is determined from the token type and is the same as for [Amount] of the same token.
         * @param displayQuantityDelta is the signed amount to transfer between source and destination in displayable units.
         * Positive values mean transfers from source to destination. Negative values mean transfers from destination to source.
         * @param token defines the asset being represented in the transfer. The token should implement [TokenizableAssetInfo] if custom
         * conversion logic is required.
         * @param source The payer of the transfer if displayQuantityDelta is positive, the payee if displayQuantityDelta is negative
         * @param destination The payee of the transfer if displayQuantityDelta is positive, the payer if displayQuantityDelta is negative
         * @param rounding The mode of rounding to apply after scaling to integer token units.
         */
        @JvmStatic
        @JvmOverloads
        fun <T : Any, P : Any> fromDecimal(displayQuantityDelta: BigDecimal,
                                           token: T,
                                           source: P,
                                           destination: P,
                                           rounding: RoundingMode = RoundingMode.DOWN): AmountTransfer<T, P> {
            val tokenSize = Amount.getDisplayTokenSize(token)
            val deltaTokenCount = displayQuantityDelta.divide(tokenSize).setScale(0, rounding).longValueExact()
            return AmountTransfer(deltaTokenCount, token, source, destination)
        }

        /**
         * Helper to make a zero size AmountTransfer
         */
        @JvmStatic
        fun <T : Any, P : Any> zero(token: T,
                                    source: P,
                                    destination: P): AmountTransfer<T, P> = AmountTransfer(0L, token, source, destination)
    }

    init {
        require(source != destination) { "The source and destination cannot be the same ($source)" }
    }

    /**
     * Add together two [AmountTransfer] objects to produce the single equivalent net flow.
     * The addition only applies to AmountTransfer objects with the same token type.
     * Also the pair of parties must be aligned, although source destination may be
     * swapped in the second item.
     * @throws ArithmeticException if there is underflow, or overflow in the summations.
     */
    operator fun plus(other: AmountTransfer<T, P>): AmountTransfer<T, P> {
        require(other.token == token) { "Token mismatch: ${other.token} vs $token" }
        require((other.source == source && other.destination == destination)
                || (other.source == destination && other.destination == source)) {
            "Only AmountTransfer between the same two parties can be aggregated/netted"
        }
        return if (other.source == source) {
            AmountTransfer(Math.addExact(quantityDelta, other.quantityDelta), token, source, destination)
        } else {
            AmountTransfer(Math.subtractExact(quantityDelta, other.quantityDelta), token, source, destination)
        }
    }

    /**
     * Convert the quantityDelta to a displayable format BigDecimal value. The conversion ratio is the same as for
     * [Amount] of the same token type.
     */
    fun toDecimal(): BigDecimal = BigDecimal.valueOf(quantityDelta, 0) * Amount.getDisplayTokenSize(token)

    fun copy(quantityDelta: Long = this.quantityDelta,
             token: T = this.token,
             source: P = this.source,
             destination: P = this.destination): AmountTransfer<T, P> = AmountTransfer(quantityDelta, token, source, destination)

    /**
     * Checks value equality of AmountTransfer objects, but also matches the reversed source and destination equivalent.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as AmountTransfer<*, *>

        if (token != other.token) return false
        if (source == other.source) {
            if (destination != other.destination) return false
            if (quantityDelta != other.quantityDelta) return false
            return true
        } else if (source == other.destination) {
            if (destination != other.source) return false
            if (quantityDelta != -other.quantityDelta) return false
            return true
        }

        return false
    }

    /**
     * HashCode ensures that reversed source and destination equivalents will hash to the same value.
     */
    override fun hashCode(): Int {
        var result = Math.abs(quantityDelta).hashCode() // ignore polarity reversed values
        result = 31 * result + token.hashCode()
        result = 31 * result + (source.hashCode() xor destination.hashCode()) // XOR to ensure the same hash for swapped source and destination
        return result
    }

    override fun toString(): String {
        return "Transfer from $source to $destination of ${this.toDecimal().toPlainString()} $token"
    }

    /**
     * Novation is a common financial operation in which a bilateral exchange is modified so that the same
     * relative asset exchange happens, but with each party exchanging versus a central counterparty, or clearing house.
     *
     * @param centralParty The central party to face the exchange against.
     * @return Returns two new AmountTransfers each between one of the original parties and the centralParty.
     * The net total exchange is the same as in the original input.
     */
    fun novate(centralParty: P): Pair<AmountTransfer<T, P>, AmountTransfer<T, P>> = Pair(copy(destination = centralParty), copy(source = centralParty))

    /**
     * Applies this AmountTransfer to a list of [SourceAndAmount] objects representing balances.
     * The list can be heterogeneous in terms of token types and parties, so long as there is sufficient balance
     * of the correct token type held with the party paying for the transfer.
     * @param balances The source list of [SourceAndAmount] objects containing the funds to satisfy the exchange.
     * @param newRef An optional marker object which is attached to any new [SourceAndAmount] objects created in the output.
     * i.e. To the new payment destination entry and to any residual change output.
     * @return The returned list is a copy of the original list, except that funds needed to cover the exchange
     * will have been removed and a new output and possibly residual amount entry will be added at the end of the list.
     * @throws ArithmeticException if there is underflow in the summations.
     */
    fun apply(balances: List<SourceAndAmount<T, P>>, newRef: Any? = null): List<SourceAndAmount<T, P>> {
        val (payer, payee) = if (quantityDelta >= 0L) Pair(source, destination) else Pair(destination, source)
        val transfer = Math.abs(quantityDelta)
        var residual = transfer
        val outputs = mutableListOf<SourceAndAmount<T, P>>()
        var remaining: SourceAndAmount<T, P>? = null
        var newAmount: SourceAndAmount<T, P>? = null
        for (balance in balances) {
            if (balance.source != payer
                    || balance.amount.token != token
                    || residual == 0L) {
                // Just copy across unmodified.
                outputs += balance
            } else if (balance.amount.quantity < residual) {
                // Consume the payers amount and do not copy across.
                residual -= balance.amount.quantity
            } else {
                // Calculate any residual spend left on the payers balance.
                if (balance.amount.quantity > residual) {
                    remaining = SourceAndAmount(payer, balance.amount.copy(quantity = Math.subtractExact(balance.amount.quantity, residual)), newRef)
                }
                // Build the new output payment to the payee.
                newAmount = SourceAndAmount(payee, balance.amount.copy(quantity = transfer), newRef)
                // Clear the residual.
                residual = 0L
            }
        }
        require(residual == 0L) { "Insufficient funds. Unable to process $this" }
        if (remaining != null) {
            outputs += remaining
        }
        outputs += newAmount!!
        return outputs
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Interest rate fixes
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/** A [FixOf] identifies the question side of a fix: what day, tenor and type of fix ("LIBOR", "EURIBOR" etc) */
@CordaSerializable
data class FixOf(val name: String, val forDay: LocalDate, val ofTenor: Tenor)

/** A [Fix] represents a named interest rate, on a given day, for a given duration. It can be embedded in a tx. */
data class Fix(val of: FixOf, val value: BigDecimal) : CommandData

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
            else -> throw IllegalStateException("Invalid tenor time unit: $unit")
        }
        // Move date to the closest business day when it falls on a weekend/holiday
        val adjustedMaturityDate = calendar.applyRollConvention(maturityDate, DateRollConvention.ModifiedFollowing)
        val daysToMaturity = calculateDaysBetween(startDate, adjustedMaturityDate, DayCountBasisYear.Y360, DayCountBasisDay.DActual)

        return daysToMaturity
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
    Y365F, Y365L, Y365Q, Y366, YActual, YActualA, Y365B, Y365, YISMA, YICMA, Y252;

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


@Suppress("unused") // This utility may be useful in future. TODO: Review before API stability guarantees in place.
fun LocalDate.isWorkingDay(accordingToCalendar: BusinessCalendar): Boolean = accordingToCalendar.isWorkingDay(this)

// TODO: Make Calendar data come from an oracle

/**
 * A business calendar performs date calculations that take into account national holidays and weekends. This is a
 * typical feature of financial contracts, in which a business may not want a payment event to fall on a day when
 * no staff are around to handle problems.
 */
@CordaSerializable
open class BusinessCalendar(val holidayDates: List<LocalDate>) {
    @CordaSerializable
    class UnknownCalendar(name: String) : Exception("$name not found")

    companion object {
        val calendars = listOf("London", "NewYork")

        val TEST_CALENDAR_DATA = calendars.map {
            it to BusinessCalendar::class.java.getResourceAsStream("${it}HolidayCalendar.txt").bufferedReader().readText()
        }.toMap()

        /** Parses a date of the form YYYY-MM-DD, like 2016-01-10 for 10th Jan. */
        fun parseDateFromString(it: String): LocalDate = LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)

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
@CordaSerializable
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
