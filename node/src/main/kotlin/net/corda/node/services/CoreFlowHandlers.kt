package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.AbstractStateReplacementFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.StateReplacementException
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

// TODO: We should have a whitelist of contracts we're willing to accept at all, and reject if the transaction
//       includes us in any outside that list. Potentially just if it includes any outside that list at all.
// TODO: Do we want to be able to reject specific transactions on more complex rules, for example reject incoming
//       cash without from unknown parties?
class NotifyTransactionHandler(val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val stx = subFlow(ReceiveTransactionFlow(otherParty))
        serviceHub.recordTransactions(stx)
    }
}

class NotaryChangeHandler(otherSide: Party) : AbstractStateReplacementFlow.Acceptor<Party>(otherSide) {
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
        val newNotary = proposal.modification

        if (state !in proposedTx.inputs.map { it.ref }) {
            throw StateReplacementException("The proposed state $state is not in the proposed transaction inputs")
        }

        // TODO: load and compare against notary whitelist from config. Remove the check below
        val isNotary = serviceHub.networkMapCache.notaryNodes.any { it.notaryIdentity == newNotary }
        if (!isNotary) {
            throw StateReplacementException("The proposed node $newNotary does not run a Notary service")
        }
    }
}
