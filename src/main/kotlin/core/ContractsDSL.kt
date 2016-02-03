/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

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

val Int.DOLLARS: Amount get() = Amount(this.toLong() * 100, USD)
val Int.POUNDS: Amount get() = Amount(this.toLong() * 100, GBP)
val Int.SWISS_FRANCS: Amount get() = Amount(this.toLong() * 100, CHF)

val Double.DOLLARS: Amount get() = Amount((this * 100).toLong(), USD)

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
 *
 * Note that "pennies" are not necessarily 1/100ths of a currency unit, but are the actual smallest amount used in
 * whatever currency the amount represents.
 *
 * Amounts of different currencies *do not mix* and attempting to add or subtract two amounts of different currencies
 * will throw [IllegalArgumentException]. Amounts may not be negative. Amounts are represented internally using a signed
 * 64 bit value, therefore, the maximum expressable amount is 2^63 - 1 == Long.MAX_VALUE. Addition, subtraction and
 * multiplication are overflow checked and will throw [ArithmeticException] if the operation would have caused integer
 * overflow.
 *
 * TODO: It may make sense to replace this with convenience extensions over the JSR 354 MonetaryAmount interface
 * TODO: Should amount be abstracted to cover things like quantities of a stock, bond, commercial paper etc? Probably.
 * TODO: Think about how positive-only vs positive-or-negative amounts can be represented in the type system.
 */
data class Amount(val pennies: Long, val currency: Currency) : Comparable<Amount> {
    init {
        // Negative amounts are of course a vital part of any ledger, but negative values are only valid in certain
        // contexts: you cannot send a negative amount of cash, but you can (sometimes) have a negative balance.
        // If you want to express a negative amount, for now, use a long.
        require(pennies >= 0) { "Negative amounts are not allowed: $pennies" }
    }

    operator fun plus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(Math.addExact(pennies, other.pennies), currency)
    }

    operator fun minus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(Math.subtractExact(pennies, other.pennies), currency)
    }

    private fun checkCurrency(other: Amount) {
        require(other.currency == currency) { "Currency mismatch: ${other.currency} vs $currency" }
    }

    operator fun div(other: Long): Amount = Amount(pennies / other, currency)
    operator fun times(other: Long): Amount = Amount(Math.multiplyExact(pennies, other), currency)
    operator fun div(other: Int): Amount = Amount(pennies / other, currency)
    operator fun times(other: Int): Amount = Amount(Math.multiplyExact(pennies, other.toLong()), currency)

    override fun toString(): String = currency.currencyCode + " " + (BigDecimal(pennies) / BigDecimal(100)).toPlainString()

    override fun compareTo(other: Amount): Int {
        checkCurrency(other)
        return pennies.compareTo(other.pennies)
    }
}

fun Iterable<Amount>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()
fun Iterable<Amount>.sumOrThrow() = reduce { left, right -> left + right }
fun Iterable<Amount>.sumOrZero(currency: Currency) = if (iterator().hasNext()) sumOrThrow() else Amount(0, currency)

//// Authenticated commands ///////////////////////////////////////////////////////////////////////////////////////////

/** Filters the command list by type, party and public key all at once. */
inline fun <reified T : CommandData> List<AuthenticatedObject<CommandData>>.select(signer: PublicKey? = null,
                                                                                   party: Party? = null) =
        filter { it.value is T }.
        filter { if (signer == null) true else it.signers.contains(signer) }.
        filter { if (party == null) true else it.signingParties.contains(party) }.
        map { AuthenticatedObject<T>(it.signers, it.signingParties, it.value as T) }

inline fun <reified T : CommandData> List<AuthenticatedObject<CommandData>>.requireSingleCommand() = try {
    select<T>().single()
} catch (e: NoSuchElementException) {
    throw IllegalStateException("Required ${T::class.qualifiedName} command")   // Better error message.
}

// For Java
fun List<AuthenticatedObject<CommandData>>.requireSingleCommand(klass: Class<out CommandData>) =
        filter { klass.isInstance(it) }.single()

/** Returns a timestamp that was signed by the given authority, or returns null if missing. */
fun List<AuthenticatedObject<CommandData>>.getTimestampBy(timestampingAuthority: Party): TimestampCommand? {
    val timestampCmds = filter { it.signers.contains(timestampingAuthority.owningKey) && it.value is TimestampCommand }
    return timestampCmds.singleOrNull()?.value as? TimestampCommand
}

