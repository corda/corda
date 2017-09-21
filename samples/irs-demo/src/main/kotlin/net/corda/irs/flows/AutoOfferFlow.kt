package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.DealState
import net.corda.finance.flows.TwoPartyDealFlow
import net.corda.finance.flows.TwoPartyDealFlow.Acceptor
import net.corda.finance.flows.TwoPartyDealFlow.AutoOffer
import net.corda.finance.flows.TwoPartyDealFlow.Instigator

/**
 * This whole class is really part of a demo just to initiate the agreement of a deal with a simple
 * API call from a single party without bi-directional access to the database of offers etc.
 *
 * In the "real world", we'd probably have the offers sitting in the platform prior to the agreement step
 * or the flow would have to reach out to external systems (or users) to verify the deals.
 */
object AutoOfferFlow {
    @InitiatingFlow
    @StartableByRPC
    class Requester(val dealToBeOffered: DealState) : FlowLogic<SignedTransaction>() {

        companion object {
            object RECEIVED : ProgressTracker.Step("Received API call")
            object DEALING : ProgressTracker.Step("Starting the deal flow") {
                override fun childProgressTracker(): ProgressTracker = TwoPartyDealFlow.Primary.tracker()
            }

            // We vend a progress tracker that already knows there's going to be a TwoPartyTradingFlow involved at some
            // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
            // surprised when it appears as a new set of tasks below the current one.
            fun tracker() = ProgressTracker(RECEIVED, DEALING)
        }

        override val progressTracker = tracker()

        init {
            progressTracker.currentStep = RECEIVED
        }

        @Suspendable
        override fun call(): SignedTransaction {
            require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
            val notary = serviceHub.networkMapCache.notaryIdentities.first() // TODO We should pass the notary as a parameter to the flow, not leave it to random choice.
            // need to pick which ever party is not us
            val otherParty = notUs(dealToBeOffered.participants).map { serviceHub.identityService.partyFromAnonymous(it) }.requireNoNulls().single()
            progressTracker.currentStep = DEALING
            val instigator = Instigator(
                    otherParty,
                    AutoOffer(notary, dealToBeOffered),
                    progressTracker.getChildProgressTracker(DEALING)!!
            )
            val stx = subFlow(instigator)
            return stx
        }

        private fun <T : AbstractParty> notUs(parties: List<T>): List<T> {
            return parties.filter { ourIdentity != it }
        }
    }

    @InitiatedBy(Requester::class)
    class AutoOfferAcceptor(otherParty: Party) : Acceptor(otherParty)
}
