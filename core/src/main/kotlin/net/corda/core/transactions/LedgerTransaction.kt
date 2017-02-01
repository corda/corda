package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash

/**
 * A LedgerTransaction is derived from a [WireTransaction]. It is the result of doing the following operations:
 *
 * - Downloading and locally storing all the dependencies of the transaction.
 * - Resolving the input states and loading them into memory.
 * - Doing some basic key lookups on the [Command]s to see if any keys are from a recognised party, thus converting the
 *   [Command] objects into [AuthenticatedObject].
 * - Deserialising the output states.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 */
class LedgerTransaction(
        /** The resolved input states which will be consumed/invalidated by the execution of this transaction. */
        override val inputs: List<StateAndRef<*>>,
        outputs: List<TransactionState<ContractState>>,
        /** Arbitrary data passed to the program of each input state. */
        val commands: List<AuthenticatedObject<CommandData>>,
        /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
        val attachments: List<Attachment>,
        /** The hash of the original serialised WireTransaction. */
        override val id: SecureHash,
        notary: Party?,
        signers: List<CompositeKey>,
        timestamp: Timestamp?,
        type: TransactionType
) : BaseTransaction(inputs, outputs, notary, signers, type, timestamp) {
    init {
        checkInvariants()
    }

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
    @Throws(TransactionVerificationException::class)
    fun verify() = type.verify(this)

    // TODO: When we upgrade to Kotlin 1.1 we can make this a data class again and have the compiler generate these.

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        if (!super.equals(other)) return false

        other as LedgerTransaction

        if (inputs != other.inputs) return false
        if (outputs != other.outputs) return false
        if (commands != other.commands) return false
        if (attachments != other.attachments) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + inputs.hashCode()
        result = 31 * result + outputs.hashCode()
        result = 31 * result + commands.hashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}
