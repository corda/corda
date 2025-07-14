package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.internal.NotaryChangeTransactionBuilder
import net.corda.core.internal.digestService
import net.corda.core.internal.uncheckedCast
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.security.PublicKey

/**
 * A flow to be used for moving a list of states to a different Notary. This is required since all input states to a
 * transaction must point to the same notary.
 *
 * This assembles the transaction for notary replacement and sends out change proposals to all participants
 * of the states. If participants agree to the proposed change, they each sign the transaction.
 * Finally, the transaction containing all signatures is sent back to each participant so they can record it and
 * use the new updated states for future transactions.
 */
@InitiatingFlow
class MoveNotaryFlow<out T: ContractState>(
        val states: List<StateAndRef<T>>,
        val newNotary: Party,
        override val progressTracker: ProgressTracker = tracker()) : FlowLogic<List<StateAndRef<T>>>() {
    companion object {
        object BUILDING : ProgressTracker.Step("Resolving inputs and building transaction")
        object SIGNING : ProgressTracker.Step("Requesting signatures from other parties"){
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALIZING : ProgressTracker.Step("Invoking finality") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(BUILDING, SIGNING, FINALIZING)
    }

    private val oldNotary = states.map{it.state.notary}.singleOrNull()

    init {
        require(states.isNotEmpty()) { "Notary change flow must receive at least one state to work on" }
        require(oldNotary != null) { "All input states must be on the same notary" }
        require(oldNotary != newNotary) { "The new notary cannot be the same as the old notary" }
    }

    @Suspendable
    override fun call(): List<StateAndRef<T>> {
        progressTracker.currentStep = BUILDING
        val (stx, participants) = assembleTx()
        val sessions = getParticipantSessions(participants)
        progressTracker.currentStep = SIGNING
        val fullySigned = subFlow(CollectSignaturesFlow(stx, sessions, SIGNING.childProgressTracker()))
        progressTracker.currentStep = FINALIZING
        val finalized = subFlow(FinalityFlow(fullySigned, sessions, FINALIZING.childProgressTracker()))
        return finalized.resolveBaseTransaction(serviceHub).outputs
                .take(states.size)
                .mapIndexed{ index, state -> StateAndRef(uncheckedCast<TransactionState<ContractState>, TransactionState<T>>(state), StateRef(finalized.id, index))}
    }

    /**
     * This assembles the notary change transaction by resolving any encumbered states, adding all inputs and signing it.
     * The original n states are the first n inputs/outputs, states added due to encumbrances are added at the end
     */
    private fun assembleTx(): Pair<SignedTransaction, Set<AbstractParty>> {
        val inputs = resolveEncumbrances()
        val participants = inputs.flatMap { it.state.data.participants }.toSet()
        val tx = NotaryChangeTransactionBuilder(
                inputs.map { it.ref },
                oldNotary!!,
                newNotary,
                serviceHub.networkParametersService.currentHash,
                participants.map { it.owningKey }.toSet(),
                serviceHub.digestService
        ).build()

        // TODO: We need a much faster way of finding our key in the transaction
        val myKeys = serviceHub.keyManagementService.filterMyKeys(participants.map { it.owningKey })
        return SignedTransaction(tx, myKeys.map{ signTransaction(tx.id, it)}) to participants
    }

    private fun signTransaction( id: SecureHash, key: PublicKey) : TransactionSignature {
        val signableData = SignableData(id, SignatureMetadata(serviceHub.myInfo.platformVersion, Crypto.findSignatureScheme(key).schemeNumberID))
        return serviceHub.keyManagementService.sign(signableData, key)

    }

    /**
     * Find any states encumbered by any of the input states. Process each state at max once.
     * At the end, reorder the ouput to have the original states first, then adding any additional encumbrances.
     */
    private fun resolveEncumbrances() : List<StateAndRef<T>> {
        val resolvedStates = mutableSetOf<StateAndRef<T>>()
        states.forEach { state ->
            if (!resolvedStates.contains(state)){
                resolvedStates.addAll(resolveEncumbrancesForOneState(state))
            }
        }
        return states + (resolvedStates - states)
    }

    /** Resolves the encumbrance state chain for the given [state]. */
    private fun resolveEncumbrancesForOneState(state: StateAndRef<T>) : Set<StateAndRef<T>> {
        val resolvedStates = mutableSetOf(state)
        while (resolvedStates.last().state.encumbrance != null) {
            val encumbranceStateRef = StateRef(resolvedStates.last().ref.txhash, resolvedStates.last().state.encumbrance!!)
            val encumbranceState = serviceHub.toStateAndRef<T>(encumbranceStateRef)
            if (!resolvedStates.add(encumbranceState)) break // Stop if there is a cycle.
        }
        return resolvedStates
    }

    private fun getParticipantSessions(participants: Set<AbstractParty>): List<FlowSession> {
        return excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, participants)).map { initiateFlow(it.key)  }
    }
}