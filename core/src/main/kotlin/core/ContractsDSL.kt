/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

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
inline fun <R> requireThat(body: Requirements.() -> R) = R.body()

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
        filter { klass.isInstance(it.value) }.single()

/** Returns a timestamp that was signed by the given authority, or returns null if missing. */
fun List<AuthenticatedObject<CommandData>>.getTimestampBy(timestampingAuthority: Party): TimestampCommand? {
    val timestampCmds = filter { it.signers.contains(timestampingAuthority.owningKey) && it.value is TimestampCommand }
    return timestampCmds.singleOrNull()?.value as? TimestampCommand
}

/**
 * Returns a timestamp that was signed by any of the the named authorities, or returns null if missing.
 * Note that matching here is done by (verified, legal) name, not by public key. Any signature by any
 * party with a name that matches (case insensitively) any of the given names will yield a match.
 */
fun List<AuthenticatedObject<CommandData>>.getTimestampByName(vararg names: String): TimestampCommand? {
    val timestampCmd = filter { it.value is TimestampCommand }.singleOrNull() ?: return null
    val tsaNames = timestampCmd.signingParties.map { it.name.toLowerCase() }
    val acceptableNames = names.map { it.toLowerCase() }
    val acceptableNameFound = tsaNames.intersect(acceptableNames).isNotEmpty()
    if (acceptableNameFound)
        return timestampCmd.value as TimestampCommand
    else
        return null
}

