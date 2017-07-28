package net.corda.vega.flows

import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.AbstractStateReplacementFlow
import net.corda.core.flows.StateReplacementException
import net.corda.core.identity.Party
import net.corda.core.utilities.seconds
import net.corda.vega.contracts.RevisionedState

/**
 * Flow that generates an update on a mutable deal state and commits the resulting transaction reaching consensus
 * on the update between two parties.
 */
object StateRevisionFlow {
    class Requester<T>(curStateRef: StateAndRef<RevisionedState<T>>,
                       updatedData: T) : AbstractStateReplacementFlow.Instigator<RevisionedState<T>, RevisionedState<T>, T>(curStateRef, updatedData) {
        override fun assembleTx(): AbstractStateReplacementFlow.UpgradeTx {
            val state = originalState.state.data
            val tx = state.generateRevision(originalState.state.notary, originalState, modification)
            tx.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
            val privacySalt = PrivacySalt()
            tx.setPrivacySalt(privacySalt)

            val stx = serviceHub.signInitialTransaction(tx)
            val participantKeys = state.participants.map { it.owningKey }
            // TODO: We need a much faster way of finding our key in the transaction
            val myKey = serviceHub.keyManagementService.filterMyKeys(participantKeys).single()
            return AbstractStateReplacementFlow.UpgradeTx(stx, participantKeys, myKey, privacySalt)
        }
    }

    open class Receiver<in T>(otherParty: Party) : AbstractStateReplacementFlow.Acceptor<T>(otherParty) {
        override fun verifyProposal(proposal: AbstractStateReplacementFlow.Proposal<T>) {
            val proposedTx = proposal.stx.tx
            val state = proposal.stateRef
            if (state !in proposedTx.inputs) {
                throw StateReplacementException("The proposed state $state is not in the proposed transaction inputs")
            }
        }
    }
}
