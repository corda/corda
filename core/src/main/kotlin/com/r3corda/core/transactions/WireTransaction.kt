package com.r3corda.core.transactions

import com.esotericsoftware.kryo.Kryo
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.indexOfOrThrow
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.THREAD_LOCAL_KRYO
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.Emoji
import java.io.FileNotFoundException
import java.security.PublicKey

/**
 * A transaction ready for serialisation, without any signatures attached. A WireTransaction is usually wrapped
 * by a [SignedTransaction] that carries the signatures over this payload. The hash of the wire transaction is
 * the identity of the transaction, that is, it's possible for two [SignedTransaction]s with different sets of
 * signatures to have the same identity hash.
 */
data class WireTransaction(
        /** Pointers to the input states on the ledger, identified by (tx identity hash, output index). */
        val inputs: List<StateRef>,
        /** Hashes of the ZIP/JAR files that are needed to interpret the contents of this wire transaction. */
        val attachments: List<SecureHash>,
        /** Ordered list of states defined by this transaction, along with the associated notaries. */
        val outputs: List<TransactionState<ContractState>>,
        /** Ordered list of ([CommandData], [PublicKey]) pairs that instruct the contracts what to do. */
        val commands: List<Command>,
        /**
         * If present, the notary for this transaction. If absent then the transaction is not notarised at all.
         * This is intended for issuance/genesis transactions that don't consume any other states and thus can't
         * double spend anything.
         *
         * TODO: Ensure the invariant 'notary == null -> inputs.isEmpty' is enforced!
         */
        val notary: Party?,
        /**
         * Keys that are required to have signed the wrapping [SignedTransaction], ordered to match the list of
         * signatures. There is nothing that forces the list to be the _correct_ list of signers for this
         * transaction until the transaction is verified by using [LedgerTransaction.verify]. It includes the
         * notary key, if the notary field is set.
         */
        val signers: List<PublicKey>,
        /**
         * Pointer to a class that defines the behaviour of this transaction: either normal, or "notary changing".
         */
        val type: TransactionType,
        /**
         * If specified, a time window in which this transaction may have been notarised. Contracts can check this
         * time window to find out when a transaction is deemed to have occurred, from the ledger's perspective.
         *
         * TODO: Ensure the invariant 'timestamp != null -> notary != null' is enforced!
         */
        val timestamp: Timestamp?
) : NamedByHash {

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

    /**
     * Looks up identities and attachments from storage to generate a [LedgerTransaction]. A transaction is expected to
     * have been fully resolved using the resolution protocol by this point.
     *
     * @throws FileNotFoundException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     */
    @Throws(FileNotFoundException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub): LedgerTransaction {
        // Look up public keys to authenticated identities. This is just a stub placeholder and will all change in future.
        val authenticatedArgs = commands.map {
            val parties = it.signers.mapNotNull { pk -> services.identityService.partyFromKey(pk) }
            AuthenticatedObject(it.signers, parties, it.value)
        }
        // Open attachments specified in this transaction. If we haven't downloaded them, we fail.
        val attachments = attachments.map {
            services.storageService.attachments.openAttachment(it) ?: throw FileNotFoundException(it.toString())
        }
        val resolvedInputs = inputs.map { StateAndRef(services.loadState(it), it) }
        return LedgerTransaction(resolvedInputs, outputs, authenticatedArgs, attachments, id, notary, signers, timestamp, type)
    }
}
