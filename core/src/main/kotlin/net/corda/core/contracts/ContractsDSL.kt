@file:JvmName("ContractsDSL")
@file:KeepForDJVM
package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import java.security.PublicKey
import java.util.*

/**
 * Defines a simple domain specific language for the specification of financial contracts. Currently covers:
 *
 *  - Some utilities for working with commands.
 *  - A simple language extension for specifying requirements in English, along with logic to enforce them.
 */

//// Requirements /////////////////////////////////////////////////////////////////////////////////////////////////////

object Requirements {
    /** Throws [IllegalArgumentException] if the given expression evaluates to false. */
    @Suppress("NOTHING_TO_INLINE")   // Inlining this takes it out of our committed ABI.
    inline infix fun String.using(expr: Boolean) {
        if (!expr) throw IllegalArgumentException("Failed requirement: $this")
    }
}

inline fun <R> requireThat(body: Requirements.() -> R) = Requirements.body()

//// Authenticated commands ///////////////////////////////////////////////////////////////////////////////////////////

/** Filters the command list by type, party and public key all at once. */
inline fun <reified T : CommandData> Collection<CommandWithParties<CommandData>>.select(signer: PublicKey? = null,
                                                                                        party: AbstractParty? = null) =
        select(T::class.java, signer, party)

/** Filters the command list by type, party and public key all at once. */
fun <C : CommandData> Collection<CommandWithParties<CommandData>>.select(klass: Class<C>,
                                                                         signer: PublicKey? = null,
                                                                         party: AbstractParty? = null) =
        mapNotNull { if (klass.isInstance(it.value)) uncheckedCast<CommandWithParties<CommandData>, CommandWithParties<C>>(it) else null }.
                filter { if (signer == null) true else signer in it.signers }.
                filter { if (party == null) true else party in it.signingParties }.
                map { CommandWithParties(it.signers, it.signingParties, it.value) }

/** Filters the command list by type, parties and public keys all at once. */
inline fun <reified T : CommandData> Collection<CommandWithParties<CommandData>>.select(signers: Collection<PublicKey>?,
                                                                                        parties: Collection<Party>?) =
        select(T::class.java, signers, parties)

/** Filters the command list by type, parties and public keys all at once. */
fun <C : CommandData> Collection<CommandWithParties<CommandData>>.select(klass: Class<C>,
                                                                         signers: Collection<PublicKey>?,
                                                                         parties: Collection<Party>?) =
        mapNotNull { if (klass.isInstance(it.value)) uncheckedCast<CommandWithParties<CommandData>, CommandWithParties<C>>(it) else null }.
                filter { if (signers == null) true else it.signers.containsAll(signers) }.
                filter { if (parties == null) true else it.signingParties.containsAll(parties) }.
                map { CommandWithParties(it.signers, it.signingParties, it.value) }

/** Ensures that a transaction has only one command that is of the given type, otherwise throws an exception. */
inline fun <reified T : CommandData> Collection<CommandWithParties<CommandData>>.requireSingleCommand() = requireSingleCommand(T::class.java)

/** Ensures that a transaction has only one command that is of the given type, otherwise throws an exception. */
fun <C : CommandData> Collection<CommandWithParties<CommandData>>.requireSingleCommand(klass: Class<C>) = try {
    select(klass).single()
} catch (e: NoSuchElementException) {
    throw IllegalStateException("Required ${klass.kotlin.qualifiedName} command")   // Better error message.
}

/**
 * Simple functionality for verifying a move command. Verifies that each input has a signature from its owning key.
 *
 * @param T the type of the move command.
 */
@Throws(IllegalArgumentException::class)
inline fun <reified T : MoveCommand> verifyMoveCommand(inputs: List<OwnableState>,
                                                       commands: List<CommandWithParties<CommandData>>)
        : MoveCommand {
    // Now check the digital signatures on the move command. Every input has an owning public key, and we must
    // see a signature from each of those keys. The actual signatures have been verified against the transaction
    // data by the platform before execution.
    val owningPubKeys = inputs.map { it.owner.owningKey }.toSet()
    val command = commands.requireSingleCommand<T>()
    val keysThatSigned = command.signers.toSet()
    requireThat {
        "the owning keys are a subset of the signing keys" using keysThatSigned.containsAll(owningPubKeys)
    }
    return command.value
}
