package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ContractUpgradeUtils
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.verification.toVerifyingServiceHub
import net.corda.core.internal.warnOnce
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeLedgerTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction

class FinalityHandler(private val sender: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveTransactionFlow(sender, true, StatesToRecord.ONLY_RELEVANT))
        logger.warnOnce("Insecure API to record finalised transaction was used by ${sender.counterparty} (${sender.getCounterpartyFlowInfo()})")
    }
}

class NotaryChangeHandler(otherSideSession: FlowSession) : AbstractStateReplacementFlow.Acceptor<Party>(otherSideSession) {
    /**
     * Check the notary change proposal.
     *
     * For example, if the proposed new notary has the same behaviour (e.g. both are non-validating)
     * and is also in a geographically convenient location we can just automatically approve the change.
     * TODO: In more difficult cases this should call for human attention to manually verify and approve the proposal
     */
    override fun verifyProposal(stx: SignedTransaction, proposal: AbstractStateReplacementFlow.Proposal<Party>) {
        val state = proposal.stateRef
        val proposedTx = stx.resolveNotaryChangeTransaction(serviceHub)
        // TODO: Right now all nodes will automatically approve the notary change. We need to figure out if stricter controls are necessary.

        if (state !in proposedTx.inputs.map { it.ref }) {
            throw StateReplacementException("The proposed state $state is not in the proposed transaction inputs")
        }

        val newNotary = proposal.modification
        val isNotary = serviceHub.networkMapCache.isNotary(newNotary)
        if (!isNotary) {
            throw StateReplacementException("The proposed node $newNotary does not run a Notary service")
        }
    }
}

class ContractUpgradeHandler(otherSide: FlowSession) : AbstractStateReplacementFlow.Acceptor<Class<out UpgradedContract<ContractState, *>>>(otherSide) {
    @Suspendable
    override fun verifyProposal(stx: SignedTransaction, proposal: AbstractStateReplacementFlow.Proposal<Class<out UpgradedContract<ContractState, *>>>) {
        // Retrieve signed transaction from our side, we will apply the upgrade logic to the transaction on our side, and
        // verify outputs matches the proposed upgrade.
        val ourSTX = requireNotNull(serviceHub.validatedTransactions.getTransaction(proposal.stateRef.txhash)) {
            "We don't have a copy of the referenced state"
        }
        val oldStateAndRef = ourSTX.resolveBaseTransaction(serviceHub).outRef<ContractState>(proposal.stateRef.index)
        val authorisedUpgrade = checkNotNull(serviceHub.contractUpgradeService.getAuthorisedContractUpgrade(oldStateAndRef.ref)) {
            "Contract state upgrade is unauthorised. State hash : ${oldStateAndRef.ref}"
        }
        val proposedTx = stx.coreTransaction as ContractUpgradeWireTransaction
        val expectedTx = ContractUpgradeUtils.assembleUpgradeTx(oldStateAndRef, proposal.modification, proposedTx.privacySalt, serviceHub)
        requireThat {
            "The instigator is one of the participants" using (initiatingSession.counterparty in oldStateAndRef.state.data.participants)
            "The proposed upgrade ${proposal.modification.javaClass} is a trusted upgrade path" using (proposal.modification.name == authorisedUpgrade)
            "The proposed tx matches the expected tx for this upgrade" using (proposedTx == expectedTx)
        }
        proposedTx.resolve(serviceHub, stx.sigs)
    }
}

class MoveNotaryHandler( val otherSideSession: FlowSession) : FlowLogic<Unit>() {
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