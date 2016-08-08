package com.r3corda.core.contracts

import com.esotericsoftware.kryo.Kryo
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.indexOfOrThrow
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.THREAD_LOCAL_KRYO
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.Emoji
import java.security.PublicKey
import java.security.SignatureException
import java.util.*

/**
 * Views of a transaction as it progresses through the pipeline, from bytes loaded from disk/network to the object
 * tree passed into a contract.
 *
 * SignedTransaction wraps a serialized WireTransaction. It contains one or more signatures, each one for
 * a public key that is mentioned inside a transaction command. SignedTransaction is the top level transaction type
 * and the type most frequently passed around the network and stored. The identity of a transaction is the hash
 * of a WireTransaction, therefore if you are storing data keyed by WT hash be aware that multiple different STs may
 * map to the same key (and they could be different in important ways, like validity!). The signatures on a
 * SignedTransaction might be invalid or missing: the type does not imply validity.
 *
 * WireTransaction is a transaction in a form ready to be serialised/unserialised. A WireTransaction can be hashed
 * in various ways to calculate a *signature hash* (or sighash), this is the hash that is signed by the various involved
 * keypairs.
 *
 * LedgerTransaction is derived from WireTransaction. It is the result of doing the following operations:
 *
 * - Downloading and locally storing all the dependencies of the transaction.
 * - Resolving the input states and loading them into memory.
 * - Doing some basic key lookups on WireCommand to see if any keys are from a recognised party, thus converting the
 *   WireCommand objects into AuthenticatedObject<Command>. Currently we just assume a hard coded pubkey->party map.
 *   In future it'd make more sense to use a certificate scheme and so that logic would get more complex.
 * - Deserialising the output states.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 *
 * There is also TransactionForContract, which is a lightly red-acted form of LedgerTransaction that's fed into the
 * contract's verify function. It may be removed in future.
 */

/** Transaction ready for serialisation, without any signatures attached. */
data class WireTransaction(val inputs: List<StateRef>,
                           val attachments: List<SecureHash>,
                           val outputs: List<TransactionState<ContractState>>,
                           val commands: List<Command>,
                           val signers: List<PublicKey>,
                           val type: TransactionType,
                           val timestamp: Timestamp?) : NamedByHash {

    // Cache the serialised form of the transaction and its hash to give us fast access to it.
    @Volatile @Transient private var cachedBits: SerializedBytes<WireTransaction>? = null
    val serialized: SerializedBytes<WireTransaction> get() = cachedBits ?: serialize().apply { cachedBits = this }
    override val id: SecureHash get() = serialized.hash

    companion object {
        fun deserialize(bits: SerializedBytes<WireTransaction>, kryo: Kryo = THREAD_LOCAL_KRYO.get()): WireTransaction {
            val wtx = bits.bits.deserialize<WireTransaction>(kryo)
            wtx.cachedBits = bits
            return wtx
        }
    }

    /** Returns a [StateAndRef] for the given output index. */
    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> outRef(index: Int): StateAndRef<T> {
        require(index >= 0 && index < outputs.size)
        return StateAndRef(outputs[index] as TransactionState<T>, StateRef(id, index))
    }

    /** Returns a [StateAndRef] for the requested output state, or throws [IllegalArgumentException] if not found. */
    fun <T : ContractState> outRef(state: ContractState): StateAndRef<T> = outRef(outputs.map { it.data }.indexOfOrThrow(state))

    override fun toString(): String {
        val buf = StringBuilder()
        buf.appendln("Transaction $id:")
        for (input in inputs) buf.appendln("${Emoji.rightArrow}INPUT:      $input")
        for (output in outputs) buf.appendln("${Emoji.leftArrow}OUTPUT:     $output")
        for (command in commands) buf.appendln("${Emoji.diamond}COMMAND:    $command")
        for (attachment in attachments) buf.appendln("${Emoji.paperclip}ATTACHMENT: $attachment")
        return buf.toString()
    }
}

/** Container for a [WireTransaction] and attached signatures. */
data class SignedTransaction(val txBits: SerializedBytes<WireTransaction>,
                             val sigs: List<DigitalSignature.WithKey>) : NamedByHash {
    init {
        check(sigs.isNotEmpty())
    }

    // TODO: This needs to be reworked to ensure that the inner WireTransaction is only ever deserialised sandboxed.

    /** Lazily calculated access to the deserialised/hashed transaction data. */
    val tx: WireTransaction by lazy { WireTransaction.deserialize(txBits) }

    /** A transaction ID is the hash of the [WireTransaction]. Thus adding or removing a signature does not change it. */
    override val id: SecureHash get() = txBits.hash

    /**
     * Verify the signatures, deserialise the wire transaction and then check that the set of signatures found contains
     * the set of pubkeys in the signers list. If any signatures are missing, either throws an exception (by default) or
     * returns the list of keys that have missing signatures, depending on the parameter.
     *
     * @throws SignatureException if a signature is invalid, does not match or if any signature is missing.
     */
    fun verifySignatures(throwIfSignaturesAreMissing: Boolean = true): Set<PublicKey> {
        // Embedded WireTransaction is not deserialised until after we check the signatures.
        for (sig in sigs)
            sig.verifyWithECDSA(txBits.bits)

        // Now examine the contents and ensure the sigs we have line up with the advertised list of signers.
        val missing = getMissingSignatures()
        if (missing.isNotEmpty() && throwIfSignaturesAreMissing) {
            // Take a best guess at where the signatures are required from, for debugging
            // TODO: We need a much better way of structuring this data
            val missingElements = ArrayList<String>()
            this.tx.commands.forEach { command ->
                if (command.signers.any { signer -> missing.contains(signer) })
                    missingElements.add(command.toString())
            }
            this.tx.notary?.owningKey.apply {
                if (missing.contains(this))
                    missingElements.add("notary")
            }
            throw SignatureException("Missing signatures for ${missingElements} on transaction ${id.prefixChars()} for ${missing.map { it.toStringShort() }}")
        }

        return missing
    }

    /** Returns the same transaction but with an additional (unchecked) signature */
    fun withAdditionalSignature(sig: DigitalSignature.WithKey): SignedTransaction {
        // TODO: need to make sure the Notary signs last
        return copy(sigs = sigs + sig)
    }

    fun withAdditionalSignatures(sigList: Iterable<DigitalSignature.WithKey>): SignedTransaction {
        return copy(sigs = sigs + sigList)
    }

    /** Alias for [withAdditionalSignature] to let you use Kotlin operator overloading. */
    operator fun plus(sig: DigitalSignature.WithKey) = withAdditionalSignature(sig)

    operator fun plus(sigList: Collection<DigitalSignature.WithKey>) = withAdditionalSignatures(sigList)

    /**
     * Returns the set of missing signatures - a signature must be present for each signer public key.
     */
    private fun getMissingSignatures(): Set<PublicKey> {
        val requiredKeys = tx.signers.toSet()
        val sigKeys = sigs.map { it.by }.toSet()

        if (sigKeys.containsAll(requiredKeys)) return emptySet()
        return requiredKeys - sigKeys
    }
}

/**
 * A LedgerTransaction wraps the data needed to calculate one or more successor states from a set of input states.
 * It is the first step after extraction from a WireTransaction. The signatures at this point have been lined up
 * with the commands from the wire, and verified/looked up.
 */
data class LedgerTransaction(
        /** The input states which will be consumed/invalidated by the execution of this transaction. */
        val inputs: List<StateAndRef<*>>,
        /** The states that will be generated by the execution of this transaction. */
        val outputs: List<TransactionState<*>>,
        /** Arbitrary data passed to the program of each input state. */
        val commands: List<AuthenticatedObject<CommandData>>,
        /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
        val attachments: List<Attachment>,
        /** The hash of the original serialised WireTransaction */
        override val id: SecureHash,
        /** The notary key and the command keys together: a signed transaction must provide signatures for all of these. */
        val signers: List<PublicKey>,
        val timestamp: Timestamp?,
        val type: TransactionType
) : NamedByHash {
    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> outRef(index: Int) = StateAndRef(outputs[index] as TransactionState<T>, StateRef(id, index))

    // TODO: Remove this concept.
    // There isn't really a good justification for hiding this data from the contract, it's just a backwards compat hack.
    /** Strips the transaction down to a form that is usable by the contract verify functions */
    fun toTransactionForContract(): TransactionForContract {
        return TransactionForContract(inputs.map { it.state.data }, outputs.map { it.data }, attachments, commands, id,
                inputs.map { it.state.notary }.singleOrNull(), timestamp)
    }

    /**
     * Verifies this transaction and throws an exception if not valid, depending on the type. For general transactions:
     *
     * - The contracts are run with the transaction as the input.
     * - The list of keys mentioned in commands is compared against the signers list.
     *
     * @throws TransactionVerificationException if anything goes wrong.
     */
    fun verify() = type.verify(this)
}