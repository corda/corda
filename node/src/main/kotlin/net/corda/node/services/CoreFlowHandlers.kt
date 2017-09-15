package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

// TODO: We should have a whitelist of contracts we're willing to accept at all, and reject if the transaction
//       includes us in any outside that list. Potentially just if it includes any outside that list at all.
// TODO: Do we want to be able to reject specific transactions on more complex rules, for example reject incoming
//       cash without from unknown parties?
class NotifyTransactionHandler(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val stx = subFlow(ReceiveTransactionFlow(otherSideSession))
        serviceHub.recordTransactions(stx)
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
    override fun verifyProposal(stx: SignedTransaction, proposal: AbstractStateReplacementFlow.Proposal<Party>): Unit {
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

class SwapIdentitiesHandler(val otherSide: Party, val revocationEnabled: Boolean) : FlowLogic<Unit>() {
    constructor(otherSide: Party) : this(otherSide, false)
    companion object {
        object SENDING_KEY : ProgressTracker.Step("Sending key")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(SENDING_KEY)

    @Suspendable
    override fun call(): Unit {
        val revocationEnabled = false
        progressTracker.currentStep = SENDING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(ourIdentity, revocationEnabled)
        sendAndReceive<PartyAndCertificate>(otherSide, legalIdentityAnonymous).unwrap { confidentialIdentity ->
            SwapIdentitiesFlow.validateAndRegisterIdentity(serviceHub.identityService, otherSide, confidentialIdentity)
        }
    }
}