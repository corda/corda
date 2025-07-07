package net.corda.notarychange

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.internal.NotaryChangeTransactionBuilder
import net.corda.core.internal.digestService
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
class NotaryChangeFlow<out T: ContractState>(
        val originalState: StateAndRef<T>,
        val newNotary: Party
) : FlowLogic<StateAndRef<T>>() {
    @Suspendable
    override fun call(): StateAndRef<T> {
        val (stx, participants) = assembleTx()

        val sessions = getParticipantSessions(participants)
        val fullySigned = subFlow(CollectSignaturesFlow(stx, sessions))
        val finalized = subFlow(FinalityFlow(fullySigned, sessions))
        return finalized.resolveBaseTransaction(serviceHub).outRef(0)
    }

    private fun assembleTx(): Pair<SignedTransaction, Set<AbstractParty>> {
        val inputs = resolveEncumbrances(originalState)
        val participants = inputs.flatMap { it.state.data.participants }.toSet()

        val tx = NotaryChangeTransactionBuilder(
                inputs.map { it.ref },
                originalState.state.notary,
                newNotary,
                serviceHub.networkParametersService.currentHash,
                participants.map { it.owningKey }.toSet(),
                serviceHub.digestService
        ).build()

        // TODO: We need a much faster way of finding our key in the transaction
        val myKey = serviceHub.keyManagementService.filterMyKeys(participants.map { it.owningKey }).single()
        val signableData = SignableData(tx.id, SignatureMetadata(serviceHub.myInfo.platformVersion, Crypto.findSignatureScheme(myKey).schemeNumberID))
        val mySignature = serviceHub.keyManagementService.sign(signableData, myKey)
        return SignedTransaction(tx, listOf(mySignature)) to participants
    }

    /** Resolves the encumbrance state chain for the given [state]. */
    private fun resolveEncumbrances(state: StateAndRef<T>): List<StateAndRef<T>> {
        val states = mutableSetOf(state)
        while (states.last().state.encumbrance != null) {
            val encumbranceStateRef = StateRef(states.last().ref.txhash, states.last().state.encumbrance!!)
            val encumbranceState = serviceHub.toStateAndRef<T>(encumbranceStateRef)
            if (!states.add(encumbranceState)) break // Stop if there is a cycle.
        }
        return states.toList()
    }

    private fun getParticipantSessions(participants: Set<AbstractParty>): List<FlowSession> {
        return excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, participants)).map { initiateFlow(it.key)  }
    }

}

@InitiatedBy(NotaryChangeFlow::class)
class NotaryChangeResponder(  val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    override fun call() {
        subFlow(object : SignTransactionFlow(otherSideSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                // no op for now
            }
        })

        subFlow(ReceiveFinalityFlow(otherSideSession))
    }
}