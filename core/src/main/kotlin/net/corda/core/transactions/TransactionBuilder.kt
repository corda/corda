package net.corda.core.transactions

import co.paralleluniverse.strands.Strand
import net.corda.core.DeleteForDJVM
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.isUploaderTrusted
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList

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
 */
@DeleteForDJVM
open class TransactionBuilder(
        var notary: Party? = null,
        var lockId: UUID = (Strand.currentStrand() as? FlowStateMachine<*>)?.id?.uuid ?: UUID.randomUUID(),
        protected val inputs: MutableList<StateRef> = arrayListOf(),
        protected val attachments: MutableList<SecureHash> = arrayListOf(),
        protected val outputs: MutableList<TransactionState<ContractState>> = arrayListOf(),
        protected val commands: MutableList<Command<*>> = arrayListOf(),
        protected var window: TimeWindow? = null,
        protected var privacySalt: PrivacySalt = PrivacySalt()
) {
    constructor(notary: Party) : this(notary, (Strand.currentStrand() as? FlowStateMachine<*>)?.id?.uuid
            ?: UUID.randomUUID())

    private val inputsWithTransactionState = arrayListOf<StateAndRef<*>>()

    /**
     * Creates a copy of the builder.
     */
    fun copy(): TransactionBuilder {
        val t = TransactionBuilder(
                notary = notary,
                inputs = ArrayList(inputs),
                attachments = ArrayList(attachments),
                outputs = ArrayList(outputs),
                commands = ArrayList(commands),
                window = window,
                privacySalt = privacySalt
        )
        t.inputsWithTransactionState.addAll(this.inputsWithTransactionState)
        return t
    }

    // DOCSTART 1
    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    fun withItems(vararg items: Any): TransactionBuilder {
        for (t in items) {
            when (t) {
                is StateAndRef<*> -> addInputState(t)
                is SecureHash -> addAttachment(t)
                is TransactionState<*> -> addOutputState(t)
                is StateAndContract -> addOutputState(t.state, t.contract)
                is ContractState -> throw UnsupportedOperationException("Removed as of V1: please use a StateAndContract instead")
                is Command<*> -> addCommand(t)
                is CommandData -> throw IllegalArgumentException("You passed an instance of CommandData, but that lacks the pubkey. You need to wrap it in a Command object first.")
                is TimeWindow -> setTimeWindow(t)
                is PrivacySalt -> setPrivacySalt(t)
                else -> throw IllegalArgumentException("Wrong argument type: ${t.javaClass}")
            }
        }
        return this
    }
    // DOCEND 1

    /**
     * Generates a [WireTransaction] from this builder and resolves any [AutomaticHashConstraint] on contracts to
     * [HashAttachmentConstraint].
     *
     * @returns A new [WireTransaction] that will be unaffected by further changes to this [TransactionBuilder].
     */
    @Throws(MissingContractAttachments::class)
    @Deprecated("Please use the new method that throws better exceptions.", replaceWith = ReplaceWith("toWireTransactionNew"))
    fun toWireTransaction(services: ServicesForResolution): WireTransaction {
        try {
            return toWireTransactionNew(services)
        } catch (e: TransactionBuildingException) {
            throw MissingContractAttachments(states = emptyList(), wrappedException = e)
        }
    }

    /**
     * Generates a [WireTransaction] from this builder and resolves any [AutomaticHashConstraint] on contracts to
     * [HashAttachmentConstraint].
     *
     * @returns A new [WireTransaction] that will be unaffected by further changes to this [TransactionBuilder].
     */
    @Throws(TransactionBuildingException::class)
    fun toWireTransactionNew(services: ServicesForResolution): WireTransaction = toWireTransactionWithContext(services)

    internal fun toWireTransactionWithContext(services: ServicesForResolution, serializationContext: SerializationContext? = null): WireTransaction {

        val contractAttachments: Map<ContractClassName, AttachmentId> = determineContractAttachments(services)

        // Resolves the AutomaticHashConstraints to HashAttachmentConstraints or WhitelistedByZoneAttachmentConstraint based on a global parameter.
        // The AutomaticHashConstraint allows for less boiler plate when constructing transactions since for the typical case the named contract
        // will be available when building the transaction. In exceptional cases the TransactionStates must be created
        // with an explicit [AttachmentConstraint]
        val resolvedOutputs = outputs.map { state ->
            when {
                state.constraint !== AutomaticHashConstraint -> state
                useWhitelistedByZoneAttachmentConstraint(state.contract, services.networkParameters) -> state.copy(constraint = WhitelistedByZoneAttachmentConstraint)
                else -> state.copy(constraint = HashAttachmentConstraint(contractAttachments[state.contract]!!))
            }
        }

        return SerializationFactory.defaultFactory.withCurrentContext(serializationContext) {
            WireTransaction(WireTransaction.createComponentGroups(inputStates(), resolvedOutputs, commands, attachments + contractAttachments.values.distinct(), notary, window), privacySalt)
        }
    }

    private fun useWhitelistedByZoneAttachmentConstraint(contractClassName: ContractClassName, networkParameters: NetworkParameters): Boolean {
        return contractClassName in networkParameters.whitelistedContractImplementations.keys
    }

    /**
     * This method is responsible for selecting the contract attachments to be used for the current transaction.
     *
     * The logic depends on the input states:
     *
     * * For input states with [HashAttachmentConstraint], if the attachment with the hash is installed by the current node, then it will be inherited to the output states.
     * * For input states with [WhitelistedByZoneAttachmentConstraint] or custom [AttachmentConstraint] implementations, then the currently installed cordapp version is used.
     *
     * It is called *before* the [AutomaticConstraint] on output states is transformed into a real constraint.
     *
     * For outputs states, the only case (so far) that can really affect the contract jar selection is manually setting a specific HashConstraint on an output state.
     * The other constraints won't point to an actual jar version, but to some rules that the jar needs to obey.
     */
    private fun determineContractAttachments(services: ServicesForResolution): Map<ContractClassName, AttachmentId> {
        val inputContracts = inputsWithTransactionState.map { inputState ->
            val constraint = inputState.state.constraint
            val contractClassName = inputState.state.contract

            // If the input state is a HashConstraint, we check if we have that contract attachment, and if we trust it.
            val attachment = if (constraint is HashAttachmentConstraint) {
                val attachment = services.attachments.openAttachment(constraint.attachmentId)
                if (attachment == null || attachment !is ContractAttachment || !isUploaderTrusted(attachment.uploader)) {
                    // This should never happen because these are input states that should have been validated already.
                    throw MissingContractAttachments(listOf(inputState.state))
                }
                constraint.attachmentId
            } else {
                // For all other constraint types we set the currently installed CorDapp version.
                services.cordappProvider.getContractAttachmentID(contractClassName)
            }
            contractClassName to attachment
        }

        val allInputContracts = inputContracts.map { it.first }.toSet()

        // Find output states that have no corresponding input state.
        // If they are hand-crafted with the HashAttachmentConstraint -for some reason-, then use that hash
        val outputContracts = outputStates()
                .filter { state -> state.contract !in allInputContracts }
                .map { state ->
                    val constraint = state.constraint
                    val attachment = if (constraint is HashAttachmentConstraint) {
                        constraint.attachmentId
                    } else {
                        services.cordappProvider.getContractAttachmentID(state.contract)
                    }
                    state.contract to attachment
                }

        // Check that there is only one hash per contract. E.g.: 2 input states with different HashConstraints
        val grouped = (outputContracts + inputContracts)
                .groupBy { it.first }
                .map { (contract, hashesList) ->
                    val hashes = hashesList.map { it.second }.toSet().filterNotNull()
                    if (hashes.size != 1) throw ConflictingAttachmentsRejection(contract)
                    contract to hashes.single()
                }

        return grouped.toMap()
    }

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub) = toWireTransactionNew(services).toLedgerTransaction(services)

    internal fun toLedgerTransactionWithContext(services: ServicesForResolution, serializationContext: SerializationContext): LedgerTransaction {
        return toWireTransactionWithContext(services, serializationContext).toLedgerTransaction(services)
    }

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class, TransactionVerificationException::class)
    fun verify(services: ServiceHub) {
        toLedgerTransaction(services).verify()
    }

    open fun addInputState(stateAndRef: StateAndRef<*>): TransactionBuilder {
        val notary = stateAndRef.state.notary
        require(notary == this.notary) { "Input state requires notary \"$notary\" which does not match the transaction notary \"${this.notary}\"." }
        inputs.add(stateAndRef.ref)
        inputsWithTransactionState.add(stateAndRef)
        return this
    }

    fun addAttachment(attachmentId: SecureHash): TransactionBuilder {
        attachments.add(attachmentId)
        return this
    }

    fun addOutputState(state: TransactionState<*>): TransactionBuilder {
        outputs.add(state)
        return this
    }

    @JvmOverloads
    fun addOutputState(state: ContractState, contract: ContractClassName, notary: Party, encumbrance: Int? = null, constraint: AttachmentConstraint = AutomaticHashConstraint): TransactionBuilder {
        return addOutputState(TransactionState(state, contract, notary, encumbrance, constraint))
    }

    /** A default notary must be specified during builder construction to use this method */
    @JvmOverloads
    fun addOutputState(state: ContractState, contract: ContractClassName, constraint: AttachmentConstraint = AutomaticHashConstraint): TransactionBuilder {
        checkNotNull(notary) { "Need to specify a notary for the state, or set a default one on TransactionBuilder initialisation" }
        addOutputState(state, contract, notary!!, constraint = constraint)
        return this
    }

    fun addCommand(arg: Command<*>): TransactionBuilder {
        commands.add(arg)
        return this
    }

    fun addCommand(data: CommandData, vararg keys: PublicKey) = addCommand(Command(data, listOf(*keys)))
    fun addCommand(data: CommandData, keys: List<PublicKey>) = addCommand(Command(data, keys))

    /**
     * Sets the [TimeWindow] for this transaction, replacing the existing [TimeWindow] if there is one. To be valid, the
     * transaction must then be signed by the notary service within this window of time. In this way, the notary acts as
     * the Timestamp Authority.
     */
    fun setTimeWindow(timeWindow: TimeWindow): TransactionBuilder {
        check(notary != null) { "Only notarised transactions can have a time-window" }
        window = timeWindow
        return this
    }

    /**
     * The [TimeWindow] for the transaction can also be defined as [time] +/- [timeTolerance]. The tolerance should be
     * chosen such that your code can finish building the transaction and sending it to the Timestamp Authority within
     * that window of time, taking into account factors such as network latency. Transactions being built by a group of
     * collaborating parties may therefore require a higher time tolerance than a transaction being built by a single
     * node.
     */
    fun setTimeWindow(time: Instant, timeTolerance: Duration) = setTimeWindow(TimeWindow.withTolerance(time, timeTolerance))

    fun setPrivacySalt(privacySalt: PrivacySalt): TransactionBuilder {
        this.privacySalt = privacySalt
        return this
    }

    // Accessors that yield immutable snapshots.
    fun inputStates(): List<StateRef> = ArrayList(inputs)

    fun attachments(): List<SecureHash> = ArrayList(attachments)
    fun outputStates(): List<TransactionState<*>> = ArrayList(outputs)
    fun commands(): List<Command<*>> = ArrayList(commands)

    /**
     * Sign the built transaction and return it. This is an internal function for use by the service hub, please use
     * [ServiceHub.signInitialTransaction] instead.
     */
    fun toSignedTransaction(keyManagementService: KeyManagementService, publicKey: PublicKey, signatureMetadata: SignatureMetadata, services: ServicesForResolution): SignedTransaction {
        val wtx = toWireTransactionNew(services)
        val signableData = SignableData(wtx.id, signatureMetadata)
        val sig = keyManagementService.sign(signableData, publicKey)
        return SignedTransaction(wtx, listOf(sig))
    }
}
