package core

import core.serialization.SerializeableWithKryo
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*
import kotlin.math.div

/**
 * Defines a simple domain specific language for the specificiation of financial contracts. Currently covers:
 *
 *  - Some utilities for working with commands.
 *  - Code for working with currencies.
 *  - An Amount type that represents a positive quantity of a specific currency.
 *  - A simple language extension for specifying requirements in English, along with logic to enforce them.
 *
 *  TODO: Look into replacing Currency and Amount with CurrencyUnit and MonetaryAmount from the javax.money API (JSR 354)
 */

//// Currencies ///////////////////////////////////////////////////////////////////////////////////////////////////////

fun currency(code: String) = Currency.getInstance(code)

val USD = currency("USD")
val GBP = currency("GBP")
val CHF = currency("CHF")

val Int.DOLLARS: Amount get() = Amount(this * 100, USD)
val Int.POUNDS: Amount get() = Amount(this * 100, GBP)
val Int.SWISS_FRANCS: Amount get() = Amount(this * 100, CHF)

//// Requirements /////////////////////////////////////////////////////////////////////////////////////////////////////

class Requirements {
    infix fun String.by(expr: Boolean) {
        if (!expr) throw IllegalArgumentException("Failed requirement: $this")
    }
}
val R = Requirements()
inline fun requireThat(body: Requirements.() -> Unit) {
    R.body()
}

//// Amounts //////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Amount represents a positive quantity of currency, measured in pennies, which are the smallest representable units.
 * Note that "pennies" are not necessarily 1/100ths of a currency unit, but are the actual smallest amount used in
 * whatever currency the amount represents.
 *
 * Amounts of different currencies *do not mix* and attempting to add or subtract two amounts of different currencies
 * will throw [IllegalArgumentException]. Amounts may not be negative.
 *
 * It probably makes sense to replace this with convenience extensions over the JSR 354 MonetaryAmount interface, if
 * that spec doesn't turn out to be too heavy (it looks fairly complicated).
 *
 * TODO: Think about how positive-only vs positive-or-negative amounts can be represented in the type system.
 */
data class Amount(val pennies: Int, val currency: Currency) : Comparable<Amount>, SerializeableWithKryo {
    init {
        // Negative amounts are of course a vital part of any ledger, but negative values are only valid in certain
        // contexts: you cannot send a negative amount of cash, but you can (sometimes) have a negative balance.
        require(pennies >= 0) { "Negative amounts are not allowed: $pennies" }
    }

    operator fun plus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(pennies + other.pennies, currency)
    }

    operator fun minus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(pennies - other.pennies, currency)
    }

    private fun checkCurrency(other: Amount) {
        require(other.currency == currency) { "Currency mismatch: ${other.currency} vs $currency" }
    }

    operator fun div(other: Int): Amount = Amount(pennies / other, currency)
    operator fun times(other: Int): Amount = Amount(pennies * other, currency)

    override fun toString(): String = currency.currencyCode + " " + (BigDecimal(pennies) / BigDecimal(100)).toPlainString()

    override fun compareTo(other: Amount): Int {
        checkCurrency(other)
        return pennies.compareTo(other.pennies)
    }
}

// Note: this will throw an exception if the iterable is empty.
fun Iterable<Amount>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()
fun Iterable<Amount>.sumOrThrow() = reduce { left, right -> left + right }
fun Iterable<Amount>.sumOrZero(currency: Currency) = if (iterator().hasNext()) sumOrThrow() else Amount(0, currency)

//// Authenticated commands ///////////////////////////////////////////////////////////////////////////////////////////
inline fun <reified T : Command> List<AuthenticatedObject<Command>>.select(signer: PublicKey? = null, institution: Institution? = null) =
        filter { it.value is T }.
                filter { if (signer == null) true else it.signers.contains(signer) }.
                filter { if (institution == null) true else it.signingInstitutions.contains(institution) }.
                map { AuthenticatedObject<T>(it.signers, it.signingInstitutions, it.value as T) }

inline fun <reified T : Command> List<AuthenticatedObject<Command>>.requireSingleCommand() = try {
    select<T>().single()
} catch (e: NoSuchElementException) {
    throw IllegalStateException("Required ${T::class.qualifiedName} command")   // Better error message.
}

// For Java
fun List<AuthenticatedObject<Command>>.requireSingleCommand(klass: Class<out Command>) = filter { klass.isInstance(it) }.single()
