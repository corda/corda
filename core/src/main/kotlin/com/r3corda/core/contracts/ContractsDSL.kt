package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import java.security.PublicKey
import java.util.*

/**
 * Defines a simple domain specific language for the specification of financial contracts. Currently covers:
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

// Java interop
object JavaTestHelpers {
    @JvmStatic val USD: Currency get() = currency("USD")
    @JvmStatic val GBP: Currency get() = currency("GBP")
    @JvmStatic val CHF: Currency get() = currency("CHF")

    @JvmStatic fun DOLLARS(amount: Int) = Amount(amount.toLong() * 100, USD)
    @JvmStatic fun DOLLARS(amount: Double) = Amount((amount * 100).toLong(), USD)
    @JvmStatic fun POUNDS(amount: Int) = Amount(amount.toLong() * 100, GBP)
    @JvmStatic fun SWISS_FRANCS(amount: Int) = Amount(amount.toLong() * 100, CHF)

    @JvmStatic fun issuedBy(currency: Currency, deposit: PartyAndReference) = Issued<Currency>(deposit, currency)
    @JvmStatic fun issuedBy(amount: Amount<Currency>, deposit: PartyAndReference) = Amount(amount.quantity, issuedBy(amount.token, deposit))
}

val USD = JavaTestHelpers.USD
val GBP = JavaTestHelpers.GBP
val CHF = JavaTestHelpers.CHF

val Int.DOLLARS: Amount<Currency> get() = JavaTestHelpers.DOLLARS(this)
val Double.DOLLARS: Amount<Currency> get() = JavaTestHelpers.DOLLARS(this)
val Int.POUNDS: Amount<Currency> get() = JavaTestHelpers.POUNDS(this)
val Int.SWISS_FRANCS: Amount<Currency> get() = JavaTestHelpers.SWISS_FRANCS(this)

infix fun Currency.`issued by`(deposit: PartyAndReference) = JavaTestHelpers.issuedBy(this, deposit)
infix fun Amount<Currency>.`issued by`(deposit: PartyAndReference) = JavaTestHelpers.issuedBy(this, deposit)

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
inline fun <reified T : CommandData> Collection<AuthenticatedObject<CommandData>>.select(signer: PublicKey? = null,
                                                                                   party: Party? = null) =
        filter { it.value is T }.
        filter { if (signer == null) true else signer in it.signers }.
        filter { if (party == null) true else party in it.signingParties }.
        map { AuthenticatedObject<T>(it.signers, it.signingParties, it.value as T) }

/** Filters the command list by type, parties and public keys all at once. */
inline fun <reified T : CommandData> Collection<AuthenticatedObject<CommandData>>.select(signers: Collection<PublicKey>?,
                                                                                   parties: Collection<Party>?) =
        filter { it.value is T }.
        filter { if (signers == null) true else it.signers.containsAll(signers)}.
        filter { if (parties == null) true else it.signingParties.containsAll(parties) }.
        map { AuthenticatedObject<T>(it.signers, it.signingParties, it.value as T) }

inline fun <reified T : CommandData> Collection<AuthenticatedObject<CommandData>>.requireSingleCommand() = try {
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

/**
 * Simple functionality for verifying a move command. Verifies that each input has a signature from its owning key.
 *
 * @param T the type of the move command
 */
@Throws(IllegalArgumentException::class)
// TODO: Can we have a common Move command for all contracts and avoid the reified type parameter here?
inline fun <reified T : MoveCommand> verifyMoveCommand(inputs: List<OwnableState>,
                                                       tx: TransactionForContract)
        : MoveCommand
        = verifyMoveCommand<T>(inputs, tx.commands)

/**
 * Simple functionality for verifying a move command. Verifies that each input has a signature from its owning key.
 *
 * @param T the type of the move command
 */
@Throws(IllegalArgumentException::class)
inline fun <reified T : MoveCommand> verifyMoveCommand(inputs: List<OwnableState>,
                                                       commands: List<AuthenticatedObject<CommandData>>)
        : MoveCommand {
    // Now check the digital signatures on the move command. Every input has an owning public key, and we must
    // see a signature from each of those keys. The actual signatures have been verified against the transaction
    // data by the platform before execution.
    val owningPubKeys = inputs.map { it.owner }.toSet()
    val command = commands.requireSingleCommand<T>()
    val keysThatSigned = command.signers.toSet()
    requireThat {
        "the owning keys are the same as the signing keys" by keysThatSigned.containsAll(owningPubKeys)
    }
    return command.value
}
