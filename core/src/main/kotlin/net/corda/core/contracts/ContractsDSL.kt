@file:JvmName("ContractsDSL")

package net.corda.core.contracts

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
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

fun currency(code: String) = Currency.getInstance(code)!!

fun commodity(code: String) = Commodity.getInstance(code)!!

@JvmField val USD = currency("USD")
@JvmField val GBP = currency("GBP")
@JvmField val EUR = currency("EUR")
@JvmField val CHF = currency("CHF")
@JvmField val FCOJ = commodity("FCOJ")

fun DOLLARS(amount: Int): Amount<Currency> = Amount(amount.toLong() * 100, USD)
fun DOLLARS(amount: Double): Amount<Currency> = Amount((amount * 100).toLong(), USD)
fun POUNDS(amount: Int): Amount<Currency> = Amount(amount.toLong() * 100, GBP)
fun SWISS_FRANCS(amount: Int): Amount<Currency> = Amount(amount.toLong() * 100, CHF)
fun FCOJ(amount: Int): Amount<Commodity> = Amount(amount.toLong() * 100, FCOJ)

val Int.DOLLARS: Amount<Currency> get() = DOLLARS(this)
val Double.DOLLARS: Amount<Currency> get() = DOLLARS(this)
val Int.POUNDS: Amount<Currency> get() = POUNDS(this)
val Int.SWISS_FRANCS: Amount<Currency> get() = SWISS_FRANCS(this)
val Int.FCOJ: Amount<Commodity> get() = FCOJ(this)

infix fun Currency.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Commodity.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Amount<Currency>.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Currency.issuedBy(deposit: PartyAndReference) = Issued(deposit, this)
infix fun Commodity.issuedBy(deposit: PartyAndReference) = Issued(deposit, this)
infix fun Amount<Currency>.issuedBy(deposit: PartyAndReference) = Amount(quantity, token.issuedBy(deposit))

//// Requirements /////////////////////////////////////////////////////////////////////////////////////////////////////

object Requirements {
    @Suppress("NOTHING_TO_INLINE")   // Inlining this takes it out of our committed ABI.
    infix inline fun String.by(expr: Boolean) {
        if (!expr) throw IllegalArgumentException("Failed requirement: $this")
    }
}

inline fun <R> requireThat(body: Requirements.() -> R) = Requirements.body()

//// Authenticated commands ///////////////////////////////////////////////////////////////////////////////////////////

// TODO: Provide a version of select that interops with Java

/** Filters the command list by type, party and public key all at once. */
inline fun <reified T : CommandData> Collection<AuthenticatedObject<CommandData>>.select(signer: CompositeKey? = null,
                                                                                         party: Party? = null) =
        filter { it.value is T }.
                filter { if (signer == null) true else signer in it.signers }.
                filter { if (party == null) true else party in it.signingParties }.
                map { AuthenticatedObject(it.signers, it.signingParties, it.value as T) }

// TODO: Provide a version of select that interops with Java

/** Filters the command list by type, parties and public keys all at once. */
inline fun <reified T : CommandData> Collection<AuthenticatedObject<CommandData>>.select(signers: Collection<CompositeKey>?,
                                                                                         parties: Collection<Party>?) =
        filter { it.value is T }.
                filter { if (signers == null) true else it.signers.containsAll(signers) }.
                filter { if (parties == null) true else it.signingParties.containsAll(parties) }.
                map { AuthenticatedObject(it.signers, it.signingParties, it.value as T) }

inline fun <reified T : CommandData> Collection<AuthenticatedObject<CommandData>>.requireSingleCommand() = try {
    select<T>().single()
} catch (e: NoSuchElementException) {
    throw IllegalStateException("Required ${T::class.qualifiedName} command")   // Better error message.
}

// For Java
fun <C : CommandData> Collection<AuthenticatedObject<CommandData>>.requireSingleCommand(klass: Class<C>) =
        mapNotNull { @Suppress("UNCHECKED_CAST") if (klass.isInstance(it.value)) it as AuthenticatedObject<C> else null }.single()

/**
 * Simple functionality for verifying a move command. Verifies that each input has a signature from its owning key.
 *
 * @param T the type of the move command.
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
 * @param T the type of the move command.
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
        "the owning keys are a subset of the signing keys" by keysThatSigned.containsAll(owningPubKeys)
    }
    return command.value
}
