/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.transactions

import co.paralleluniverse.strands.Strand
import net.corda.core.DeleteForDJVM
import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.KeyManagementService
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
open class TransactionBuilder @JvmOverloads constructor(
        var notary: Party? = null,
        var lockId: UUID = (Strand.currentStrand() as? FlowStateMachine<*>)?.id?.uuid ?: UUID.randomUUID(),
        protected val inputs: MutableList<StateRef> = arrayListOf(),
        protected val attachments: MutableList<SecureHash> = arrayListOf(),
        protected val outputs: MutableList<TransactionState<ContractState>> = arrayListOf(),
        protected val commands: MutableList<Command<*>> = arrayListOf(),
        protected var window: TimeWindow? = null,
        protected var privacySalt: PrivacySalt = PrivacySalt(),
        protected val references: MutableList<StateRef> = arrayListOf()
) {
    private val inputsWithTransactionState = arrayListOf<TransactionState<ContractState>>()
    private val referencesWithTransactionState = arrayListOf<TransactionState<ContractState>>()

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
                privacySalt = privacySalt,
                references = references
        )
        t.inputsWithTransactionState.addAll(this.inputsWithTransactionState)
        t.referencesWithTransactionState.addAll(this.referencesWithTransactionState)
        return t
    }

    // DOCSTART 1
    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    fun withItems(vararg items: Any): TransactionBuilder {
        for (t in items) {
            when (t) {
                is StateAndRef<*> -> addInputState(t)
                is ReferencedStateAndRef<*> -> @Suppress("DEPRECATION") addReferenceState(t) // Will remove when feature finalised.
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
    fun toWireTransaction(services: ServicesForResolution): WireTransaction = toWireTransactionWithContext(services)

    internal fun toWireTransactionWithContext(services: ServicesForResolution, serializationContext: SerializationContext? = null): WireTransaction {

        // Resolves the AutomaticHashConstraints to HashAttachmentConstraints or WhitelistedByZoneAttachmentConstraint based on a global parameter.
        // The AutomaticHashConstraint allows for less boiler plate when constructing transactions since for the typical case the named contract
        // will be available when building the transaction. In exceptional cases the TransactionStates must be created
        // with an explicit [AttachmentConstraint]
        val resolvedOutputs = outputs.map { state ->
            when {
                state.constraint !== AutomaticHashConstraint -> state
                useWhitelistedByZoneAttachmentConstraint(state.contract, services.networkParameters) -> state.copy(constraint = WhitelistedByZoneAttachmentConstraint)
                else -> services.cordappProvider.getContractAttachmentID(state.contract)?.let {
                    state.copy(constraint = HashAttachmentConstraint(it))
                } ?: throw MissingContractAttachments(listOf(state))
            }
        }

        return SerializationFactory.defaultFactory.withCurrentContext(serializationContext) {
            WireTransaction(WireTransaction.createComponentGroups(inputStates(), resolvedOutputs, commands, attachments + makeContractAttachments(services.cordappProvider), notary, window, referenceStates()), privacySalt)
        }
    }

    private fun useWhitelistedByZoneAttachmentConstraint(contractClassName: ContractClassName, networkParameters: NetworkParameters): Boolean {
        return contractClassName in networkParameters.whitelistedContractImplementations.keys
    }

    /**
     * The attachments added to the current transaction contain only the hashes of the current cordapps.
     * NOT the hashes of the cordapps that were used when the input states were created ( in case they changed in the meantime)
     * TODO - review this logic
     */
    private fun makeContractAttachments(cordappProvider: CordappProvider): List<AttachmentId> {
        // Reference inputs not included as it is not necessary to verify them.
        return (inputsWithTransactionState + outputs).map { state ->
            cordappProvider.getContractAttachmentID(state.contract)
                    ?: throw MissingContractAttachments(listOf(state))
        }.distinct()
    }

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub) = toWireTransaction(services).toLedgerTransaction(services)

    internal fun toLedgerTransactionWithContext(services: ServicesForResolution, serializationContext: SerializationContext): LedgerTransaction {
        return toWireTransactionWithContext(services, serializationContext).toLedgerTransaction(services)
    }

    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class, TransactionVerificationException::class)
    fun verify(services: ServiceHub) {
        toLedgerTransaction(services).verify()
    }

    private fun checkNotary(stateAndRef: StateAndRef<*>) {
        val notary = stateAndRef.state.notary
        require(notary == this.notary) {
            "Input state requires notary \"$notary\" which does not match the transaction notary \"${this.notary}\"."
        }
    }

    // This check is performed here as well as in BaseTransaction.
    private fun checkForInputsAndReferencesOverlap() {
        val intersection = inputs intersect references
        require(intersection.isEmpty()) {
            "A StateRef cannot be both an input and a reference input in the same transaction."
        }
    }

    private fun checkReferencesUseSameNotary() = referencesWithTransactionState.map { it.notary }.toSet().size == 1

    /**
     * Adds a reference input [StateRef] to the transaction.
     *
     * This feature was added in version 4 of Corda, so will throw an exception for any Corda networks with a minimum
     * platform version less than 4.
     *
     * @throws UncheckedVersionException
     */
    @Deprecated(message = "Feature not yet released. Pending stabilisation.")
    open fun addReferenceState(referencedStateAndRef: ReferencedStateAndRef<*>): TransactionBuilder {
        val stateAndRef = referencedStateAndRef.stateAndRef
        referencesWithTransactionState.add(stateAndRef.state)

        // It is likely the case that users of reference states do not have permission to change the notary assigned
        // to a reference state. Even if users _did_ have this permission the result would likely be a bunch of
        // notary change races. As such, if a reference state is added to a transaction which is assigned to a
        // different notary to the input and output states then all those inputs and outputs must be moved to the
        // notary which the reference state uses.
        //
        // If two or more reference states assigned to different notaries are added to a transaction then it follows
        // that this transaction likely _cannot_ be committed to the ledger as it unlikely that the party using the
        // reference state can change the assigned notary for one of the reference states.
        //
        // As such, if reference states assigned to multiple different notaries are added to a transaction builder
        // then the check below will fail.
        check(checkReferencesUseSameNotary()) {
            "Transactions with reference states using multiple different notaries are currently unsupported."
        }

        checkNotary(stateAndRef)
        references.add(stateAndRef.ref)
        checkForInputsAndReferencesOverlap()
        return this
    }

    /** Adds an input [StateRef] to the transaction. */
    open fun addInputState(stateAndRef: StateAndRef<*>): TransactionBuilder {
        checkNotary(stateAndRef)
        inputs.add(stateAndRef.ref)
        inputsWithTransactionState.add(stateAndRef.state)
        return this
    }

    /** Adds an attachment with the specified hash to the TransactionBuilder. */
    fun addAttachment(attachmentId: SecureHash): TransactionBuilder {
        attachments.add(attachmentId)
        return this
    }

    /** Adds an output state to the transaction. */
    fun addOutputState(state: TransactionState<*>): TransactionBuilder {
        outputs.add(state)
        return this
    }

    /** Adds an output state, with associated contract code (and constraints), and notary, to the transaction. */
    @JvmOverloads
    fun addOutputState(
            state: ContractState,
            contract: ContractClassName,
            notary: Party, encumbrance: Int? = null,
            constraint: AttachmentConstraint = AutomaticHashConstraint
    ): TransactionBuilder {
        return addOutputState(TransactionState(state, contract, notary, encumbrance, constraint))
    }

    /** A default notary must be specified during builder construction to use this method */
    @JvmOverloads
    fun addOutputState(
            state: ContractState, contract: ContractClassName,
            constraint: AttachmentConstraint = AutomaticHashConstraint
    ): TransactionBuilder {
        checkNotNull(notary) {
            "Need to specify a notary for the state, or set a default one on TransactionBuilder initialisation"
        }
        addOutputState(state, contract, notary!!, constraint = constraint)
        return this
    }

    /** Adds a [Command] to the transaction. */
    fun addCommand(arg: Command<*>): TransactionBuilder {
        commands.add(arg)
        return this
    }

    /**
     * Adds a [Command] to the transaction, specified by the encapsulated [CommandData] object and required list of
     * signing [PublicKey]s.
     */
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

    /** Returns an immutable list of input [StateRefs]. */
    fun inputStates(): List<StateRef> = ArrayList(inputs)

    /** Returns an immutable list of reference input [StateRefs]. */
    fun referenceStates(): List<StateRef> = ArrayList(references)

    /** Returns an immutable list of attachment hashes. */
    fun attachments(): List<SecureHash> = ArrayList(attachments)

    /** Returns an immutable list of output [TransactionState]s. */
    fun outputStates(): List<TransactionState<*>> = ArrayList(outputs)

    /** Returns an immutable list of [Command]s. */
    fun commands(): List<Command<*>> = ArrayList(commands)

    /**
     * Sign the built transaction and return it. This is an internal function for use by the service hub, please use
     * [ServiceHub.signInitialTransaction] instead.
     */
    fun toSignedTransaction(keyManagementService: KeyManagementService, publicKey: PublicKey, signatureMetadata: SignatureMetadata, services: ServicesForResolution): SignedTransaction {
        val wtx = toWireTransaction(services)
        val signableData = SignableData(wtx.id, signatureMetadata)
        val sig = keyManagementService.sign(signableData, publicKey)
        return SignedTransaction(wtx, listOf(sig))
    }
}
