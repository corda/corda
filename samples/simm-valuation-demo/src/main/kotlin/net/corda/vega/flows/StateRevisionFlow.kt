package net.corda.vega.flows

import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.AbstractStateReplacementFlow
import net.corda.flows.StateReplacementException
import net.corda.vega.contracts.RevisionedState

/**
 * Flow that generates an update on a mutable deal state and commits the resulting transaction reaching consensus
 * on the update between two parties
 */
object StateRevisionFlow {
    class Requester<T>(curStateRef: StateAndRef<RevisionedState<T>>,
                       updatedData: T) : AbstractStateReplacementFlow.Instigator<RevisionedState<T>, T>(curStateRef, updatedData) {
        override fun assembleTx(): Pair<SignedTransaction, List<CompositeKey>> {
            val state = originalState.state.data
            val tx = state.generateRevision(originalState.state.notary, originalState, modification)
            tx.setTime(serviceHub.clock.instant(), 30.seconds)
            tx.signWith(serviceHub.legalIdentityKey)

            val stx = tx.toSignedTransaction(false)
            return Pair(stx, state.participants)
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
