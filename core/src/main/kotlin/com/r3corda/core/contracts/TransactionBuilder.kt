package com.r3corda.core.contracts

import com.r3corda.core.crypto.*
import com.r3corda.core.serialization.serialize
import java.security.KeyPair
import java.security.PublicKey
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
 * @param notary The default notary that will be used for outputs that don't have a notary specified. When this is set,
 *               an output state can be added by just passing in a [ContractState] – a [TransactionState] with the
 *               default notary will be generated automatically.
 */
open class TransactionBuilder(
        protected val type: TransactionType = TransactionType.General(),
        protected val notary: Party? = null,
        protected val inputs: MutableList<StateRef> = arrayListOf(),
        protected val attachments: MutableList<SecureHash> = arrayListOf(),
        protected val outputs: MutableList<TransactionState<ContractState>> = arrayListOf(),
        protected val commands: MutableList<Command> = arrayListOf(),
        protected val signers: MutableSet<PublicKey> = mutableSetOf()) {

    val time: TimestampCommand? get() = commands.mapNotNull { it.value as? TimestampCommand }.singleOrNull()

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
                    signers = LinkedHashSet(signers)
            )

    /**
     * Places a [TimestampCommand] in this transaction, removing any existing command if there is one.
     * The command requires a signature from the Notary service, which acts as a Timestamp Authority.
     * The signature can be obtained using [NotaryProtocol].
     *
     * The window of time in which the final timestamp may lie is defined as [time] +/- [timeTolerance].
     * If you want a non-symmetrical time window you must add the command via [addCommand] yourself. The tolerance
     * should be chosen such that your code can finish building the transaction and sending it to the TSA within that
     * window of time, taking into account factors such as network latency. Transactions being built by a group of
     * collaborating parties may therefore require a higher time tolerance than a transaction being built by a single
     * node.
     */
    fun setTime(time: Instant, authority: Party, timeTolerance: Duration) {
        check(currentSigs.isEmpty()) { "Cannot change timestamp after signing" }
        commands.removeAll { it.value is TimestampCommand }
        addCommand(TimestampCommand(time, timeTolerance), authority.owningKey)
    }

    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    fun withItems(vararg items: Any): TransactionBuilder {
        for (t in items) {
            when (t) {
                is StateAndRef<*> -> addInputState(t)
                is TransactionState<*> -> addOutputState(t)
                is ContractState -> addOutputState(t)
                is Command -> addCommand(t)
                else -> throw IllegalArgumentException("Wrong argument type: ${t.javaClass}")
            }
        }
        return this
    }

    /** The signatures that have been collected so far - might be incomplete! */
    protected val currentSigs = arrayListOf<DigitalSignature.WithKey>()

    fun signWith(key: KeyPair) {
        check(currentSigs.none { it.by == key.public }) { "This partial transaction was already signed by ${key.public}" }
        val data = toWireTransaction().serialize()
        addSignatureUnchecked(key.signWithECDSA(data.bits))
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
        require(commands.any { it.signers.contains(sig.by) }) { "Signature key doesn't match any command" }
        sig.verifyWithECDSA(toWireTransaction().serialized)
    }

    /** Adds the signature directly to the transaction, without checking it for validity. */
    fun addSignatureUnchecked(sig: DigitalSignature.WithKey) {
        currentSigs.add(sig)
    }

    fun toWireTransaction() = WireTransaction(ArrayList(inputs), ArrayList(attachments),
            ArrayList(outputs), ArrayList(commands), signers.toList(), type)

    fun toSignedTransaction(checkSufficientSignatures: Boolean = true): SignedTransaction {
        if (checkSufficientSignatures) {
            val gotKeys = currentSigs.map { it.by }.toSet()
            val missing: Set<PublicKey> = signers - gotKeys
            if (missing.isNotEmpty())
                throw IllegalStateException("Missing signatures on the transaction for the public keys: ${missing.toStringsShort()}")
        }
        return SignedTransaction(toWireTransaction().serialize(), ArrayList(currentSigs))
    }

    open fun addInputState(stateAndRef: StateAndRef<*>) = addInputState(stateAndRef.ref, stateAndRef.state.notary)

    fun addInputState(stateRef: StateRef, notary: Party) {
        check(currentSigs.isEmpty())
        signers.add(notary.owningKey)
        inputs.add(stateRef)
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

    fun addOutputState(state: ContractState, notary: Party) = addOutputState(TransactionState(state, notary))

    /** A default notary must be specified during builder construction to use this method */
    fun addOutputState(state: ContractState): Int {
        checkNotNull(notary) { "Need to specify a Notary for the state, or set a default one on TransactionBuilder initialisation" }
        return addOutputState(state, notary!!)
    }

    fun addCommand(arg: Command) {
        check(currentSigs.isEmpty())
        // TODO: replace pubkeys in commands with 'pointers' to keys in signers
        signers.addAll(arg.signers)
        commands.add(arg)
    }

    fun addCommand(data: CommandData, vararg keys: PublicKey) = addCommand(Command(data, listOf(*keys)))
    fun addCommand(data: CommandData, keys: List<PublicKey>) = addCommand(Command(data, keys))

    // Accessors that yield immutable snapshots.
    fun inputStates(): List<StateRef> = ArrayList(inputs)

    fun outputStates(): List<TransactionState<*>> = ArrayList(outputs)
    fun commands(): List<Command> = ArrayList(commands)
    fun attachments(): List<SecureHash> = ArrayList(attachments)
}
