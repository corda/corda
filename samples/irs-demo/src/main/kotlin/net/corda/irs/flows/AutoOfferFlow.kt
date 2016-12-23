package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.DealState
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.TwoPartyDealFlow
import net.corda.flows.TwoPartyDealFlow.Acceptor
import net.corda.flows.TwoPartyDealFlow.AutoOffer
import net.corda.flows.TwoPartyDealFlow.Instigator
import java.util.function.Function

/**
 * This whole class is really part of a demo just to initiate the agreement of a deal with a simple
 * API call from a single party without bi-directional access to the database of offers etc.
 *
 * In the "real world", we'd probably have the offers sitting in the platform prior to the agreement step
 * or the flow would have to reach out to external systems (or users) to verify the deals.
 */
object AutoOfferFlow {

    class Plugin : CordaPluginRegistry() {
        override val servicePlugins = listOf(Function(::Service))
    }


    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {

        object DEALING : ProgressTracker.Step("Starting the deal flow") {
            override fun childProgressTracker(): ProgressTracker = TwoPartyDealFlow.Secondary.tracker()
        }

        init {
            services.registerFlowInitiator(Instigator::class) { Acceptor(it) }
        }
    }

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
            require(serviceHub.networkMapCache.notaryNodes.isNotEmpty()) { "No notary nodes registered" }
            val notary = serviceHub.networkMapCache.notaryNodes.first().notaryIdentity
            // need to pick which ever party is not us
            val otherParty = notUs(dealToBeOffered.parties).single()
            progressTracker.currentStep = DEALING
            val myKey = serviceHub.legalIdentityKey
            val instigator = Instigator(
                    otherParty,
                    AutoOffer(notary, dealToBeOffered),
                    myKey,
                    progressTracker.getChildProgressTracker(DEALING)!!
            )
            val stx = subFlow(instigator)
            return stx
        }

        private fun notUs(parties: List<Party>): List<Party> {
            val notUsParties: MutableList<Party> = arrayListOf()
            for (party in parties) {
                if (serviceHub.myInfo.legalIdentity != party) {
                    notUsParties.add(party)
                }
            }
            return notUsParties
        }

    }
}
