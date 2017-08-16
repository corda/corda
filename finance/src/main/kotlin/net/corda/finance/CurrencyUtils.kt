@file:JvmName("CurrencyUtils")

package net.corda.finance

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import java.math.BigDecimal
import java.util.*

@JvmField val USD: Currency = Currency.getInstance("USD")
@JvmField val GBP: Currency = Currency.getInstance("GBP")
@JvmField val EUR: Currency = Currency.getInstance("EUR")
@JvmField val CHF: Currency = Currency.getInstance("CHF")
@JvmField val JPY: Currency = Currency.getInstance("JPY")
@JvmField val RUB: Currency = Currency.getInstance("RUB")

fun <T : Any> AMOUNT(amount: Int, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount.toLong()), token)
fun <T : Any> AMOUNT(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun DOLLARS(amount: Int): Amount<Currency> = AMOUNT(amount, USD)
fun DOLLARS(amount: Double): Amount<Currency> = AMOUNT(amount, USD)
fun POUNDS(amount: Int): Amount<Currency> = AMOUNT(amount, GBP)
fun SWISS_FRANCS(amount: Int): Amount<Currency> = AMOUNT(amount, CHF)

val Int.DOLLARS: Amount<Currency> get() = DOLLARS(this)
val Double.DOLLARS: Amount<Currency> get() = DOLLARS(this)
val Int.POUNDS: Amount<Currency> get() = POUNDS(this)
val Int.SWISS_FRANCS: Amount<Currency> get() = SWISS_FRANCS(this)

infix fun Currency.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Amount<Currency>.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Currency.issuedBy(deposit: PartyAndReference) = Issued(deposit, this)
infix fun Amount<Currency>.issuedBy(deposit: PartyAndReference) = Amount(quantity, displayTokenSize, token.issuedBy(deposit))

private val currencySymbols: Map<String, Currency> = mapOf(
        "$" to USD,
        "£" to GBP,
        "€" to EUR,
        "¥" to JPY,
        "₽" to RUB
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
    } catch(e: Exception) {
        throw IllegalArgumentException("Could not parse $input as a currency", e)
    }
    throw IllegalArgumentException("Did not recognise the currency in $input or could not parse")
}