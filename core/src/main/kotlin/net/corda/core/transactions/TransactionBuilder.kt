package net.corda.core.transactions

import co.paralleluniverse.strands.Strand
import com.google.common.annotations.VisibleForTesting
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.internal.FlowStateMachine
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.serialize
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException
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
        protected val type: TransactionType = TransactionType.General,
        var notary: Party? = null,
        var lockId: UUID = (Strand.currentStrand() as? FlowStateMachine<*>)?.id?.uuid ?: UUID.randomUUID(),
        protected val inputs: MutableList<StateRef> = arrayListOf(),
        protected val attachments: MutableList<SecureHash> = arrayListOf(),
        protected val outputs: MutableList<TransactionState<ContractState>> = arrayListOf(),
        protected val commands: MutableList<Command> = arrayListOf(),
        protected val signers: MutableSet<PublicKey> = mutableSetOf(),
        window: TimeWindow? = null) {
    constructor(type: TransactionType, notary: Party) : this(type, notary, (Strand.currentStrand() as? FlowStateMachine<*>)?.id?.uuid ?: UUID.randomUUID())

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
                    window = timeWindow
            )

    // DOCSTART 1
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
    // DOCEND 1

    fun toWireTransaction() = WireTransaction(ArrayList(inputs), ArrayList(attachments),
            ArrayList(outputs), ArrayList(commands), notary, signers.toList(), type, timeWindow)

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub) = toWireTransaction().toLedgerTransaction(services)

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class, TransactionVerificationException::class)
    fun verify(services: ServiceHub) {
        toLedgerTransaction(services).verify()
    }

    open fun addInputState(stateAndRef: StateAndRef<*>) {
        val notary = stateAndRef.state.notary
        require(notary == this.notary) { "Input state requires notary \"$notary\" which does not match the transaction notary \"${this.notary}\"." }
        signers.add(notary.owningKey)
        inputs.add(stateAndRef.ref)
    }

    fun addAttachment(attachmentId: SecureHash) {
        attachments.add(attachmentId)
    }

    fun addOutputState(state: TransactionState<*>): Int {
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
        // TODO: replace pubkeys in commands with 'pointers' to keys in signers
        signers.addAll(arg.signers)
        commands.add(arg)
    }

    fun addCommand(data: CommandData, vararg keys: PublicKey) = addCommand(Command(data, listOf(*keys)))
    fun addCommand(data: CommandData, keys: List<PublicKey>) = addCommand(Command(data, keys))

    var timeWindow: TimeWindow? = window
        /**
         * Sets the [TimeWindow] for this transaction, replacing the existing [TimeWindow] if there is one. To be valid, the
         * transaction must then be signed by the notary service within this window of time. In this way, the notary acts as
         * the Timestamp Authority.
         */
        set(value) {
            check(notary != null) { "Only notarised transactions can have a time-window" }
            signers.add(notary!!.owningKey)
            field = value
        }

    /**
     * The [TimeWindow] for the transaction can also be defined as [time] +/- [timeTolerance]. The tolerance should be
     * chosen such that your code can finish building the transaction and sending it to the Timestamp Authority within
     * that window of time, taking into account factors such as network latency. Transactions being built by a group of
     * collaborating parties may therefore require a higher time tolerance than a transaction being built by a single
     * node.
     */
    fun setTimeWindow(time: Instant, timeTolerance: Duration) {
        timeWindow = TimeWindow.withTolerance(time, timeTolerance)
    }

    // Accessors that yield immutable snapshots.
    fun inputStates(): List<StateRef> = ArrayList(inputs)
    fun attachments(): List<SecureHash> = ArrayList(attachments)
    fun outputStates(): List<TransactionState<*>> = ArrayList(outputs)
    fun commands(): List<Command> = ArrayList(commands)

    /** The signatures that have been collected so far - might be incomplete! */
    @Deprecated("Signatures should be gathered on a SignedTransaction instead.")
    protected val currentSigs = arrayListOf<DigitalSignature.WithKey>()

    @Deprecated("Use ServiceHub.signInitialTransaction() instead.")
    fun signWith(key: KeyPair): TransactionBuilder {
        val data = toWireTransaction().id
        addSignatureUnchecked(key.sign(data.bytes))
        return this
    }

    /** Adds the signature directly to the transaction, without checking it for validity. */
    @Deprecated("Use ServiceHub.signInitialTransaction() instead.")
    fun addSignatureUnchecked(sig: DigitalSignature.WithKey): TransactionBuilder {
        currentSigs.add(sig)
        return this
    }

    @Deprecated("Use ServiceHub.signInitialTransaction() instead.")
    fun toSignedTransaction(checkSufficientSignatures: Boolean = true): SignedTransaction {
        if (checkSufficientSignatures) {
            val gotKeys = currentSigs.map { it.by }.toSet()
            val missing: Set<PublicKey> = signers.filter { !it.isFulfilledBy(gotKeys) }.toSet()
            if (missing.isNotEmpty())
                throw IllegalStateException("Missing signatures on the transaction for the public keys: ${missing.joinToString()}")
        }
        val wtx = toWireTransaction()
        return SignedTransaction(wtx.serialize(), ArrayList(currentSigs))
    }

    /**
     * Checks that the given signature matches one of the commands and that it is a correct signature over the tx, then
     * adds it.
     *
     * @throws SignatureException if the signature didn't match the transaction contents.
     * @throws IllegalArgumentException if the signature key doesn't appear in any command.
     */
    @Deprecated("Use WireTransaction.checkSignature() instead.")
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
    @Deprecated("Use WireTransaction.checkSignature() instead.")
    fun checkSignature(sig: DigitalSignature.WithKey) {
        require(commands.any { it.signers.any { sig.by in it.keys } }) { "Signature key doesn't match any command" }
        sig.verify(toWireTransaction().id)
    }
}