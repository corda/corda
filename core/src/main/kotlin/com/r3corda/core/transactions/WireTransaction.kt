package com.r3corda.core.transactions

import com.esotericsoftware.kryo.Kryo
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.indexOfOrThrow
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.THREAD_LOCAL_KRYO
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.Emoji
import java.security.PublicKey

/**
 * A transaction ready for serialisation, without any signatures attached. A WireTransaction is usually wrapped
 * by a [SignedTransaction] that carries the signatures over this payload. The hash of the wire transaction is
 * the identity of the transaction, that is, it's possible for two [SignedTransaction]s with different sets of
 * signatures to have the same identity hash.
 */
data class WireTransaction(val inputs: List<StateRef>,
                           val attachments: List<SecureHash>,
                           val outputs: List<TransactionState<ContractState>>,
                           val commands: List<Command>,
                           val notary: Party?,
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
