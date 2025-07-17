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
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.internal.NotaryChangeTransactionBuilder
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.digestService
import net.corda.core.internal.uncheckedCast
import net.corda.core.internal.verification.toVerifyingServiceHub
import net.corda.core.transactions.NotaryChangeLedgerTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
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

    private val oldNotary = states.map{it.state.notary}.toSet().singleOrNull()

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
        require(states.all {
            serviceHub.keyManagementService.filterMyKeys(it.state.data.participants.map { it.owningKey }).toList().isNotEmpty()
        }) { "Cannot request move for state we are not a participant in" }

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

@InitiatedBy(MoveNotaryFlow::class)
class MoveNotaryResponder( val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(object : SignTransactionFlow(otherSideSession) {

            override fun checkTransaction(stx: SignedTransaction) {
                checkInitiatorSignedAppropriately(stx)
            }
        })

        subFlow(ReceiveFinalityFlow(otherSideSession))
    }

    @VisibleForTesting
    fun checkInitiatorSignedAppropriately(stx: SignedTransaction) {
        val ltx = NotaryChangeLedgerTransaction.resolve(serviceHub.toVerifyingServiceHub(), stx.coreTransaction as NotaryChangeWireTransaction, stx.sigs)
        val encumbranceGroups = groupOutputsByEncumbrances(ltx.outputs.mapIndexed { index, state -> StateAndRef(state, StateRef(ltx.id, index)) })
        require(encumbranceGroups.first().all { stateRef ->
            ltx.outputs[stateRef.index].data.participants.any { participant -> stx.sigs.any { sig -> participant.owningKey.isFulfilledBy(sig.by) } }
        })
        { "Initiator did not sign for all non-encumbered states!" }
        require(encumbranceGroups.drop(1).all {
            it.any { stateRef ->
                ltx.outputs[stateRef.index].data.participants.any { participant -> stx.sigs.any { sig -> participant.owningKey.isFulfilledBy(sig.by) } }
            }
        }) { "Initiator added encumbered state and did not sign for any of the group of encumbered states." }
    }

    private fun groupOutputsByEncumbrances(outputs: List<StateAndRef<*>>): List<List<StateRef>> {
        val seenStates = mutableSetOf<StateRef>()
        val result = mutableListOf(mutableListOf<StateRef>())

        outputs.forEach {
            if (!seenStates.add(it.ref)) {  // add the state to the list of seen states - if it's already in the set, we have already seen it
                return@forEach
            }
            if (it.state.encumbrance == null) { // if not encumbered, it goes into the first bin of the result
                result[0].add(it.ref)
                return@forEach
            }

            // We have an encumbered state - add a new bin to the result
            result.add(mutableListOf())
            var currentState = it
            while (true) {
                result.last().add(currentState.ref) // add state to the new bin
                val encumbrance = currentState.state.encumbrance!! // get next encumbrance (can't be null because encumbrances need to cyclic)
                currentState = outputs[encumbrance]  // fetch the encumbering state
                if (!seenStates.add(currentState.ref)) { // try to add it to seen states - if it's already in, we're done with this set of encumbrances.
                    break
                }
            }
        }
        return result
    }
}