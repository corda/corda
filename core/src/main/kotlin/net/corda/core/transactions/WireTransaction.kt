package net.corda.core.transactions

import com.esotericsoftware.kryo.Kryo
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.indexOfOrThrow
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.THREAD_LOCAL_KRYO
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Emoji
import java.io.FileNotFoundException
import java.security.PublicKey

/**
 * A transaction ready for serialisation, without any signatures attached. A WireTransaction is usually wrapped
 * by a [SignedTransaction] that carries the signatures over this payload. The hash of the wire transaction is
 * the identity of the transaction, that is, it's possible for two [SignedTransaction]s with different sets of
 * signatures to have the same identity hash.
 */
class WireTransaction(
        /** Pointers to the input states on the ledger, identified by (tx identity hash, output index). */
        override val inputs: List<StateRef>,
        /** Hashes of the ZIP/JAR files that are needed to interpret the contents of this wire transaction. */
        val attachments: List<SecureHash>,
        outputs: List<TransactionState<ContractState>>,
        /** Ordered list of ([CommandData], [PublicKey]) pairs that instruct the contracts what to do. */
        val commands: List<Command>,
        notary: Party.Full?,
        signers: List<CompositeKey>,
        type: TransactionType,
        timestamp: Timestamp?
) : BaseTransaction(inputs, outputs, notary, signers, type, timestamp) {
    init {
        checkInvariants()
    }

    // Cache the serialised form of the transaction and its hash to give us fast access to it.
    @Volatile @Transient private var cachedBytes: SerializedBytes<WireTransaction>? = null
    val serialized: SerializedBytes<WireTransaction> get() = cachedBytes ?: serialize().apply { cachedBytes = this }

    //We need cashed leaves hashes and whole tree for an id and Partial Merkle Tree calculation.
    @Volatile @Transient private var cachedLeavesHashes: List<SecureHash>? = null
    val allLeavesHashes: List<SecureHash> get() = cachedLeavesHashes ?: calculateLeavesHashes().apply { cachedLeavesHashes = this }

    @Volatile @Transient var cachedTree: MerkleTree? = null
    val merkleTree: MerkleTree get() = cachedTree ?: MerkleTree.getMerkleTree(allLeavesHashes).apply { cachedTree = this }

    override val id: SecureHash get() = merkleTree.hash

    companion object {
        fun deserialize(data: SerializedBytes<WireTransaction>, kryo: Kryo = THREAD_LOCAL_KRYO.get()): WireTransaction {
            val wtx = data.bytes.deserialize<WireTransaction>(kryo)
            wtx.cachedBytes = data
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

    /**
     * Looks up identities and attachments from storage to generate a [LedgerTransaction]. A transaction is expected to
     * have been fully resolved using the resolution flow by this point.
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
        return LedgerTransaction(resolvedInputs, outputs, authenticatedArgs, attachments, id, notary, mustSign, timestamp, type)
    }

    override fun toString(): String {
        val buf = StringBuilder()
        buf.appendln("Transaction $id:")
        for (input in inputs) buf.appendln("${Emoji.rightArrow}INPUT:      $input")
        for (output in outputs) buf.appendln("${Emoji.leftArrow}OUTPUT:     $output")
        for (command in commands) buf.appendln("${Emoji.diamond}COMMAND:    $command")
        for (attachment in attachments) buf.appendln("${Emoji.paperclip}ATTACHMENT: $attachment")
        return buf.toString()
    }

    // TODO: When Kotlin 1.1 comes out we can make this class a data class again, and have these be autogenerated.

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        if (!super.equals(other)) return false

        other as WireTransaction

        if (inputs != other.inputs) return false
        if (attachments != other.attachments) return false
        if (outputs != other.outputs) return false
        if (commands != other.commands) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + inputs.hashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + outputs.hashCode()
        result = 31 * result + commands.hashCode()
        return result
    }
}
