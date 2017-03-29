package net.corda.core.transactions

import co.paralleluniverse.strands.Strand
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.flows.FlowStateMachine
import net.corda.core.serialization.serialize
import java.security.KeyPair
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * A TransactionBuilder is a transaction class that's mutable (unlike the others which are all immutable). It is
 * intended to be passed around contracts that may edit it by adding new states/commands. Then once the states
 * and commands are right, this class can be used as a holding bucket to gather signatures from multiple parties.
 *
 * The builder can be customised for specific transaction types, e.g. where additional processing is needed
 * before adding a state/command.
 *
 * @param notary Notary used for the transaction. If null, this indicates the transaction DOES NOT have a notary.
 * When this is set to a non-null value, an output state can be added by just passing in a [ContractState] – a
 * [TransactionState] with this notary specified will be generated automatically.
 *
 * @param signers The set of public keys the transaction needs signatures for. The logic for building the signers set
 * can be customised for every [TransactionType]. E.g. in the general case it contains the command and notary public keys,
 * but for the [TransactionType.NotaryChange] transactions it is the set of all input [ContractState.participants].
 */
open class TransactionBuilder(
        protected val type: TransactionType = TransactionType.General(),
        var notary: Party? = null,
        var lockId: UUID = (Strand.currentStrand() as? FlowStateMachine<*>)?.id?.uuid ?: UUID.randomUUID(),
        protected val inputs: MutableList<StateRef> = arrayListOf(),
        protected val attachments: MutableList<SecureHash> = arrayListOf(),
        protected val outputs: MutableList<TransactionState<ContractState>> = arrayListOf(),
        protected val commands: MutableList<Command> = arrayListOf(),
        protected val signers: MutableSet<CompositeKey> = mutableSetOf(),
        protected var timestamp: Timestamp? = null) {

    val time: Timestamp? get() = timestamp

    /**
     * Creates a copy of the builder.
     */
    fun copy(): TransactionBuilder =
            TransactionBuilder(
                    type = type,
                    notary = notary,
                    inputs = ArrayList(inputs),
                    attachments = ArrayList(attachments),
                    outputs = ArrayList(outputs),
                    commands = ArrayList(commands),
                    signers = LinkedHashSet(signers),
                    timestamp = timestamp
            )

    /**
     * Places a [TimestampCommand] in this transaction, removing any existing command if there is one.
     * The command requires a signature from the Notary service, which acts as a Timestamp Authority.
     * The signature can be obtained using [NotaryFlow].
     *
     * The window of time in which the final timestamp may lie is defined as [time] +/- [timeTolerance].
     * If you want a non-symmetrical time window you must add the command via [addCommand] yourself. The tolerance
     * should be chosen such that your code can finish building the transaction and sending it to the TSA within that
     * window of time, taking into account factors such as network latency. Transactions being built by a group of
     * collaborating parties may therefore require a higher time tolerance than a transaction being built by a single
     * node.
     */
    fun setTime(time: Instant, timeTolerance: Duration) = setTime(Timestamp(time, timeTolerance))

    fun setTime(newTimestamp: Timestamp) {
        check(notary != null) { "Only notarised transactions can have a timestamp" }
        signers.add(notary!!.owningKey)
        check(currentSigs.isEmpty()) { "Cannot change timestamp after signing" }
        this.timestamp = newTimestamp
    }

    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    fun withItems(vararg items: Any): TransactionBuilder {
        for (t in items) {
            when (t) {
                is StateAndRef<*> -> addInputState(t)
                is TransactionState<*> -> addOutputState(t)
                is ContractState -> addOutputState(t)
                is Command -> addCommand(t)
                is CommandData -> throw IllegalArgumentException("You passed an instance of CommandData, but that lacks the pubkey. You need to wrap it in a Command object first.")
                else -> throw IllegalArgumentException("Wrong argument type: ${t.javaClass}")
            }
        }
        return this
    }

    /** The signatures that have been collected so far - might be incomplete! */
    protected val currentSigs = arrayListOf<DigitalSignature.WithKey>()

    fun signWith(key: KeyPair): TransactionBuilder {
        check(currentSigs.none { it.by == key.public }) { "This partial transaction was already signed by ${key.public}" }
        val data = toWireTransaction().id
        addSignatureUnchecked(key.signWithECDSA(data.bytes))
        return this
    }

    /**
     * Checks that the given signature matches one of the commands and that it is a correct signature over the tx, then
     * adds it.
     *
     * @throws SignatureException if the signature didn't match the transaction contents.
     * @throws IllegalArgumentException if the signature key doesn't appear in any command.
     */
    fun checkAndAddSignature(sig: DigitalSignature.WithKey) {
        checkSignature(sig)
        addSignatureUnchecked(sig)
    }

    /**
     * Checks that the given signature matches one of the commands and that it is a correct signature over the tx.
     *
     * @throws SignatureException if the signature didn't match the transaction contents.
     * @throws IllegalArgumentException if the signature key doesn't appear in any command.
     */
    fun checkSignature(sig: DigitalSignature.WithKey) {
        require(commands.any { it.signers.any { sig.by in it.keys } }) { "Signature key doesn't match any command" }
        sig.verifyWithECDSA(toWireTransaction().id)
    }

    /** Adds the signature directly to the transaction, without checking it for validity. */
    fun addSignatureUnchecked(sig: DigitalSignature.WithKey): TransactionBuilder {
        currentSigs.add(sig)
        return this
    }

    fun toWireTransaction() = WireTransaction(ArrayList(inputs), ArrayList(attachments),
            ArrayList(outputs), ArrayList(commands), notary, signers.toList(), type, timestamp)

    fun toSignedTransaction(checkSufficientSignatures: Boolean = true): SignedTransaction {
        if (checkSufficientSignatures) {
            val gotKeys = currentSigs.map { it.by }.toSet()
            val missing: Set<CompositeKey> = signers.filter { !it.isFulfilledBy(gotKeys) }.toSet()
            if (missing.isNotEmpty())
                throw IllegalStateException("Missing signatures on the transaction for the public keys: ${missing.joinToString()}")
        }
        val wtx = toWireTransaction()
        return SignedTransaction(wtx.serialize(), ArrayList(currentSigs))
    }

    open fun addInputState(stateAndRef: StateAndRef<*>) {
        check(currentSigs.isEmpty())
        val notary = stateAndRef.state.notary
        require(notary == this.notary) { "Input state requires notary \"${notary}\" which does not match the transaction notary \"${this.notary}\"." }
        signers.add(notary.owningKey)
        inputs.add(stateAndRef.ref)
    }

    fun addAttachment(attachmentId: SecureHash) {
        check(currentSigs.isEmpty())
        attachments.add(attachmentId)
    }

    fun addOutputState(state: TransactionState<*>): Int {
        check(currentSigs.isEmpty())
        outputs.add(state)
        return outputs.size - 1
    }

    @JvmOverloads
    fun addOutputState(state: ContractState, notary: Party, encumbrance: Int? = null) = addOutputState(TransactionState(state, notary, encumbrance))

    /** A default notary must be specified during builder construction to use this method */
    fun addOutputState(state: ContractState): Int {
        checkNotNull(notary) { "Need to specify a notary for the state, or set a default one on TransactionBuilder initialisation" }
        return addOutputState(state, notary!!)
    }

    fun addCommand(arg: Command) {
        check(currentSigs.isEmpty())
        // TODO: replace pubkeys in commands with 'pointers' to keys in signers
        signers.addAll(arg.signers)
        commands.add(arg)
    }

    fun addCommand(data: CommandData, vararg keys: CompositeKey) = addCommand(Command(data, listOf(*keys)))
    fun addCommand(data: CommandData, keys: List<CompositeKey>) = addCommand(Command(data, keys))

    // Accessors that yield immutable snapshots.
    fun inputStates(): List<StateRef> = ArrayList(inputs)

    fun outputStates(): List<TransactionState<*>> = ArrayList(outputs)
    fun commands(): List<Command> = ArrayList(commands)
    fun attachments(): List<SecureHash> = ArrayList(attachments)
}
