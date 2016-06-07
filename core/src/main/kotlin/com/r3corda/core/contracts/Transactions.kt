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

/**
 * Views of a transaction as it progresses through the pipeline, from bytes loaded from disk/network to the object
 * tree passed into a contract.
 *
 * SignedTransaction wraps a serialized WireTransaction. It contains one or more signatures, each one for
 * a public key that is mentioned inside a transaction command. SignedTransaction is the top level transaction type
 * and the type most frequently passed around the network and stored. The identity of a transaction is the hash
 * of a WireTransaction, therefore if you are storing data keyed by WT hash be aware that multiple different SWTs may
 * map to the same key (and they could be different in important ways!).
 *
 * WireTransaction is a transaction in a form ready to be serialised/unserialised. A WireTransaction can be hashed
 * in various ways to calculate a *signature hash* (or sighash), this is the hash that is signed by the various involved
 * keypairs. Note that a sighash is not the same thing as a *transaction id*, which is the hash of the entire
 * WireTransaction i.e. the outermost serialised form with everything included.
 *
 * LedgerTransaction is derived from WireTransaction. It is the result of doing some basic key lookups on WireCommand
 * to see if any keys are from a recognised party, thus converting the WireCommand objects into
 * AuthenticatedObject<Command>. Currently we just assume a hard coded pubkey->party map. In future it'd make more
 * sense to use a certificate scheme and so that logic would get more complex.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 *
 * TransactionForVerification is the same as LedgerTransaction but with the input states looked up from a local
 * database and replaced with the real objects. Likewise, attachments are fully resolved at this point.
 * TFV is the form that is finally fed into the contracts.
 */

/** Transaction ready for serialisation, without any signatures attached. */
data class WireTransaction(val inputs: List<StateRef>,
                           val attachments: List<SecureHash>,
                           val outputs: List<TransactionState<ContractState>>,
                           val commands: List<Command>,
                           val type: TransactionType) : NamedByHash {

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
        buf.appendln("Transaction:")
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
     * Verifies the given signatures against the serialized transaction data. Does NOT deserialise or check the contents
     * to ensure there are no missing signatures: use verify() to do that. This weaker version can be useful for
     * checking a partially signed transaction being prepared by multiple co-operating parties.
     *
     * @throws SignatureException if the signature is invalid or does not match.
     */
    fun verifySignatures() {
        for (sig in sigs)
            sig.verifyWithECDSA(txBits.bits)
    }

    /**
     * Verify the signatures, deserialise the wire transaction and then check that the set of signatures found contains
     * the set of pubkeys in the commands. If any signatures are missing, either throws an exception (by default) or
     * returns the list of keys that have missing signatures, depending on the parameter.
     *
     * @throws SignatureException if a signature is invalid, does not match or if any signature is missing.
     */
    fun verify(throwIfSignaturesAreMissing: Boolean = true): Set<PublicKey> {
        verifySignatures()

        val missing = getMissingSignatures()
        if (missing.isNotEmpty() && throwIfSignaturesAreMissing)
            throw SignatureException("Missing signatures on transaction ${id.prefixChars()} for: ${missing.map { it.toStringShort() }}")

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
     * Returns the set of missing signatures - a signature must be present for every command pub key
     * and the Notary (if it is specified)
     */
    fun getMissingSignatures(): Set<PublicKey> {
        val requiredKeys = tx.commands.flatMap { it.signers }.toSet()
        val sigKeys = sigs.map { it.by }.toSet()

        if (sigKeys.containsAll(requiredKeys)) return emptySet()
        return requiredKeys - sigKeys
    }
}

/**
 * A LedgerTransaction wraps the data needed to calculate one or more successor states from a set of input states.
 * It is the first step after extraction from a WireTransaction. The signatures at this point have been lined up
 * with the commands from the wire, and verified/looked up.
 *
 * TODO: This class needs a bit more thought. Should inputs be fully resolved by this point too?
 */
data class LedgerTransaction(
        /** The input states which will be consumed/invalidated by the execution of this transaction. */
        val inputs: List<StateRef>,
        /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
        val attachments: List<Attachment>,
        /** The states that will be generated by the execution of this transaction. */
        val outputs: List<TransactionState<*>>,
        /** Arbitrary data passed to the program of each input state. */
        val commands: List<AuthenticatedObject<CommandData>>,
        /** The hash of the original serialised WireTransaction */
        override val id: SecureHash,
        val type: TransactionType
) : NamedByHash {
    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> outRef(index: Int) = StateAndRef(outputs[index] as TransactionState<T>, StateRef(id, index))
}