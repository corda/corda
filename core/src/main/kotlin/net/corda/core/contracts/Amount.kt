package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.crypto.CompositeKey
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.exactAdd
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * This interface is used by [Amount] to determine the conversion ratio from
 * indicative/displayed asset amounts in [BigDecimal] to fungible tokens represented by Amount objects.
 */
interface TokenizableAssetInfo {
    /** The nominal display unit size of a single token, potentially with trailing decimal display places if the scale parameter is non-zero. */
    val displayTokenSize: BigDecimal
}

/**
 * Amount represents a positive quantity of some token (currency, asset, etc.), measured in quantity of the smallest
 * representable units. The nominal quantity represented by each individual token is equal to the [displayTokenSize].
 * The scale property of the [displayTokenSize] should correctly reflect the displayed decimal places and is used
 * when rounding conversions from indicative/displayed amounts in [BigDecimal] to Amount occur via the
 * [Amount.fromDecimal] method.
 *
 * Amounts of different tokens *do not mix* and attempting to add or subtract two amounts of different currencies
 * will throw [IllegalArgumentException]. Amounts may not be negative. Amounts are represented internally using a signed
 * 64 bit value, therefore, the maximum expressable amount is 2^63 - 1 == Long.MAX_VALUE. Addition, subtraction and
 * multiplication are overflow checked and will throw [ArithmeticException] if the operation would have caused integer
 * overflow.
 *
 * @property quantity the number of tokens as a long value.
 * @property displayTokenSize the nominal display unit size of a single token, potentially with trailing decimal display
 * places if the scale parameter is non-zero.
 * @property token the type of token this is an amount of. This is usually a singleton.
 * @param T the type of the token, for example [Currency]. T should implement [TokenizableAssetInfo] if automatic conversion to/from a display format is required.
 */
@KeepForDJVM
@CordaSerializable
data class Amount<T : Any>(val quantity: Long, val displayTokenSize: BigDecimal, val token: T) : Comparable<Amount<T>> {
    // TODO Proper lookup of currencies in a locale and context sensitive fashion is not supported and is left to the application.
    companion object {
        /**
         * Build an Amount from a decimal representation. For example, with an input of "12.34 GBP",
         * returns an amount with a quantity of "1234" tokens. The function [getDisplayTokenSize] is used to determine the
         * conversion scaling, for example bonds might be in nominal amounts of 100, currencies in 0.01 penny units.
         *
         * @see Amount.toDecimal
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
        @JvmStatic
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

        /**
         * If the given iterable of [Amount]s yields any elements, sum them, throwing an [IllegalArgumentException] if
         * any of the token types are mismatched; if the iterator yields no elements, return null.
         */
        @JvmStatic
        fun <T : Any> Iterable<Amount<T>>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()

        /**
         * Sums the amounts yielded by the given iterable, throwing an [IllegalArgumentException] if any of the token
         * types are mismatched.
         */
        @JvmStatic
        fun <T : Any> Iterable<Amount<T>>.sumOrThrow() = reduce { left, right -> left + right }

        /**
         * If the given iterable of [Amount]s yields any elements, sum them, throwing an [IllegalArgumentException] if
         * any of the token types are mismatched; if the iterator yields no elements, return a zero amount of the given
         * token type.
         */
        @JvmStatic
        fun <T : Any> Iterable<Amount<T>>.sumOrZero(token: T) = if (iterator().hasNext()) sumOrThrow() else Amount.zero(token)

        private val currencySymbols: Map<String, Currency> = mapOf(
                "$" to Currency.getInstance("USD"),
                "£" to Currency.getInstance("GBP"),
                "€" to Currency.getInstance("EUR"),
                "¥" to Currency.getInstance("JPY"),
                "₽" to Currency.getInstance("RUB")
        )

        private val currencyCodes: Map<String, Currency> by lazy {
            Currency.getAvailableCurrencies().associateBy { it.currencyCode }
        }

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
        @JvmStatic
        fun parseCurrency(input: String): Amount<Currency> {
            val i = input.filter { it != ',' }
            try {
                // First check the symbols at the front.
                for ((symbol, currency) in currencySymbols) {
                    if (i.startsWith(symbol)) {
                        val rest = i.substring(symbol.length)
                        return Amount.fromDecimal(BigDecimal(rest), currency)
                    }
                }
                // Now check the codes at the end.
                val split = i.split(' ')
                if (split.size == 2) {
                    val (rest, code) = split
                    for ((cc, currency) in currencyCodes) {
                        if (cc == code) {
                            return Amount.fromDecimal(BigDecimal(rest), currency)
                        }
                    }
                }
            } catch (e: Exception) {
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
     * Mixing non-identical token types will throw [IllegalArgumentException].
     *
     * @throws ArithmeticException if there is overflow of Amount tokens during the summation
     * @throws IllegalArgumentException if mixing non-identical token types.
     */
    operator fun plus(other: Amount<T>): Amount<T> {
        checkToken(other)
        return Amount(quantity exactAdd other.quantity, displayTokenSize, token)
    }

    /**
     * A checked subtraction operator is supported to simplify netting of Amounts.
     *
     * @throws ArithmeticException if there is numeric underflow.
     * @throws IllegalArgumentException if this leads to the amount going negative, or would mix non-identical token
     * types.
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
     *
     * @throws ArithmeticException if there is overflow of Amount tokens during the multiplication.
     */
    operator fun times(other: Long): Amount<T> = Amount(Math.multiplyExact(quantity, other), displayTokenSize, token)

    /**
     * The multiplication operator is supported to allow easy calculation for multiples of a primitive Amount.
     * Note this is not a conserving operation, so it may not always be correct modelling of proper token behaviour.
     * N.B. Division is not supported as fractional tokens are not representable by an Amount.
     *
     * @throws ArithmeticException if there is overflow of Amount tokens during the multiplication.
     */
    operator fun times(other: Int): Amount<T> = Amount(Math.multiplyExact(quantity, other.toLong()), displayTokenSize, token)

    /**
     * This method provides a token conserving divide mechanism.
     * @param partitions the number of amounts to divide the current quantity into.
     * @return 'partitions' separate Amount objects which sum to the same quantity as this Amount
     * and differ by no more than a single token in size.
     */
    fun splitEvenly(partitions: Int): List<Amount<T>> {
        require(partitions >= 1) { "Must split amount into one, or more pieces" }
        val commonTokensPerPartition = quantity.div(partitions)
        val residualTokens = quantity - (commonTokensPerPartition * partitions)
        val splitAmount = Amount(commonTokensPerPartition, displayTokenSize, token)
        val splitAmountPlusOne = Amount(commonTokensPerPartition + 1L, displayTokenSize, token)
        return (0 until partitions).map { if (it < residualTokens) splitAmountPlusOne else splitAmount }.toList()
    }

    /**
     * Convert a currency [Amount] to a decimal representation. For example, with an amount with a quantity
     * of "1234" GBP, returns "12.34". The precise representation is controlled by the display token size (
     * from [getDisplayTokenSize]), which determines the size of a single token and controls the trailing decimal
     * places via its scale property. *Note* that currencies such as the Bahraini Dinar use 3 decimal places,
     * and it must not be presumed that this converts amounts to 2 decimal places.
     *
     * @see Amount.fromDecimal
     */
    fun toDecimal(): BigDecimal = BigDecimal.valueOf(quantity, 0) * displayTokenSize


    /**
     * Convert a currency [Amount] to a display string representation.
     *
     * For example, with an amount with a quantity of "1234" GBP, returns "12.34 GBP".
     * The result of fromDecimal is used to control the numerical formatting and
     * the token specifier appended is taken from token.toString.
     *
     * @see Amount.fromDecimal
     */
    override fun toString(): String {
        return toDecimal().toPlainString() + " " + token
    }

    /** @suppress */
    override fun compareTo(other: Amount<T>): Int {
        checkToken(other)
        return quantity.compareTo(other.quantity)
    }
}

/**
 * Simple data class to associate the origin, owner, or holder of a particular Amount object.
 *
 * @param P Any class type that can disambiguate where the amount came from.
 * @param T The token type of the underlying [Amount].
 * @property source the holder of the Amount.
 * @property amount the Amount of asset available.
 * @property ref is an optional field used for housekeeping in the caller.
 * e.g. to point back at the original Vault state objects.
 * @see SourceAndAmount.apply which processes a list of SourceAndAmount objects
 * and calculates the resulting Amount distribution as a new list of SourceAndAmount objects.
 */
data class SourceAndAmount<T : Any, out P : Any>(val source: P, val amount: Amount<T>, val ref: Any? = null)

/**
 * This class represents a possibly negative transfer of tokens from one vault state to another, possibly at a future date.
 *
 * @property quantityDelta is a signed Long value representing the exchanged number of tokens. If positive then
 * it represents the movement of Math.abs(quantityDelta) tokens away from source and receipt of Math.abs(quantityDelta)
 * at the destination. If the quantityDelta is negative then the source will receive Math.abs(quantityDelta) tokens
 * and the destination will lose Math.abs(quantityDelta) tokens.
 * Where possible the source and destination should be coded to ensure a positive quantityDelta,
 * but in various scenarios it may be more consistent to allow positive and negative values.
 * For example it is common for a bank to code asset flows as gains and losses from its perspective i.e. always the destination.
 * @property token represents the type of asset token as would be used to construct Amount<T> objects.
 * @property source is the [Party], [CompositeKey], or other identifier of the token source if quantityDelta is positive,
 * or the token sink if quantityDelta is negative. The type P should support value equality.
 * @property destination is the [Party], [CompositeKey], or other identifier of the token sink if quantityDelta is positive,
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

        /** Helper to make a zero size AmountTransfer. */
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
            AmountTransfer(quantityDelta exactAdd other.quantityDelta, token, source, destination)
        } else {
            AmountTransfer(Math.subtractExact(quantityDelta, other.quantityDelta), token, source, destination)
        }
    }

    /**
     * Convert the quantityDelta to a displayable format BigDecimal value. The conversion ratio is the same as for
     * [Amount] of the same token type.
     */
    fun toDecimal(): BigDecimal = BigDecimal.valueOf(quantityDelta, 0) * Amount.getDisplayTokenSize(token)

    /** @suppress */
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
     * This hash code function ensures that reversed source and destination equivalents will hash to the same value.
     */
    override fun hashCode(): Int {
        var result = Math.abs(quantityDelta).hashCode() // ignore polarity reversed values
        result = 31 * result + token.hashCode()
        result = 31 * result + (source.hashCode() xor destination.hashCode()) // XOR to ensure the same hash for swapped source and destination
        return result
    }

    /** @suppress */
    override fun toString(): String {
        return "Transfer from $source to $destination of ${this.toDecimal().toPlainString()} $token"
    }

    /**
     * Returns a list of two new AmountTransfers each between one of the original parties and the centralParty. The net
     * total exchange is the same as in the original input. Novation is a common financial operation in which a
     * bilateral exchange is modified so that the same relative asset exchange happens, but with each party exchanging
     * versus a central counterparty, or clearing house.
     *
     * @param centralParty The central party to face the exchange against.
     */
    @Suppress("UNUSED")
    fun novate(centralParty: P): List<AmountTransfer<T, P>> = listOf(copy(destination = centralParty), copy(source = centralParty))

    /**
     * Applies this AmountTransfer to a list of [SourceAndAmount] objects representing balances.
     * The list can be heterogeneous in terms of token types and parties, so long as there is sufficient balance
     * of the correct token type held with the party paying for the transfer.
     * @param balances The source list of [SourceAndAmount] objects containing the funds to satisfy the exchange.
     * @param newRef An optional marker object which is attached to any new [SourceAndAmount] objects created in the output.
     * i.e. To the new payment destination entry and to any residual change output.
     * @return A copy of the original list, except that funds needed to cover the exchange
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

