package com.r3corda.demos.protocols

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.r3corda.core.contracts.DealState
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.TwoPartyDealProtocol

/**
 * This whole class is really part of a demo just to initiate the agreement of a deal with a simple
 * API call from a single party without bi-directional access to the database of offers etc.
 *
 * In the "real world", we'd probably have the offers sitting in the platform prior to the agreement step
 * or the protocol would have to reach out to external systems (or users) to verify the deals.
 */
object AutoOfferProtocol {
    val TOPIC = "autooffer.topic"

    data class AutoOfferMessage(val otherSide: Party,
                                val notary: Party,
                                val otherSessionID: Long, val dealBeingOffered: DealState)

    class Plugin: CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }


    class Service(services: ServiceHubInternal) {

        object RECEIVED : ProgressTracker.Step("Received offer")
        object DEALING : ProgressTracker.Step("Starting the deal protocol") {
            override fun childProgressTracker(): ProgressTracker = TwoPartyDealProtocol.Primary.tracker()
        }

        fun tracker() = ProgressTracker(RECEIVED, DEALING)

        class Callback(val success: (SignedTransaction) -> Unit) : FutureCallback<SignedTransaction> {
            override fun onFailure(t: Throwable?) {
                // TODO handle exceptions
            }

            override fun onSuccess(st: SignedTransaction?) {
                success(st!!)
            }
        }

        init {
            services.networkService.addMessageHandler(TOPIC, DEFAULT_SESSION_ID) { msg, registration ->
                val progressTracker = tracker()
                progressTracker.currentStep = RECEIVED
                val autoOfferMessage = msg.data.deserialize<AutoOfferMessage>()
                // Put the deal onto the ledger
                progressTracker.currentStep = DEALING
                val seller = TwoPartyDealProtocol.Instigator(autoOfferMessage.otherSide, autoOfferMessage.notary,
                        autoOfferMessage.dealBeingOffered, services.keyManagementService.freshKey(), autoOfferMessage.otherSessionID, progressTracker.getChildProgressTracker(DEALING)!!)
                val future = services.startProtocol("${TwoPartyDealProtocol.DEAL_TOPIC}.seller", seller)
                // This is required because we are doing child progress outside of a subprotocol.  In future, we should just wrap things like this in a protocol to avoid it
                Futures.addCallback(future, Callback() {
                    seller.progressTracker.currentStep = ProgressTracker.DONE
                    progressTracker.currentStep = ProgressTracker.DONE
                })
            }
        }

    }

    class Requester(val dealToBeOffered: DealState) : ProtocolLogic<SignedTransaction>() {

        companion object {
            object RECEIVED : ProgressTracker.Step("Received API call")
            object ANNOUNCING : ProgressTracker.Step("Announcing to the peer node")
            object DEALING : ProgressTracker.Step("Starting the deal protocol") {
                override fun childProgressTracker(): ProgressTracker = TwoPartyDealProtocol.Secondary.tracker()
            }

            // We vend a progress tracker that already knows there's going to be a TwoPartyTradingProtocol involved at some
            // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
            // surprised when it appears as a new set of tasks below the current one.
            fun tracker() = ProgressTracker(RECEIVED, ANNOUNCING, DEALING)
        }

        override val topic: String get() = TOPIC
        override val progressTracker = tracker()

        init {
            progressTracker.currentStep = RECEIVED
        }

        @Suspendable
        override fun call(): SignedTransaction {
            require(serviceHub.networkMapCache.notaryNodes.isNotEmpty()) { "No notary nodes registered" }
            val ourSessionID = random63BitValue()

            val notary = serviceHub.networkMapCache.notaryNodes.first().identity
            // need to pick which ever party is not us
            val otherParty = notUs(dealToBeOffered.parties).single()
            progressTracker.currentStep = ANNOUNCING
            send(otherParty, 0, AutoOfferMessage(serviceHub.storageService.myLegalIdentity, notary, ourSessionID, dealToBeOffered))
            progressTracker.currentStep = DEALING
            val stx = subProtocol(TwoPartyDealProtocol.Acceptor(otherParty, notary, dealToBeOffered, ourSessionID, progressTracker.getChildProgressTracker(DEALING)!!))
            return stx
        }

        private fun notUs(parties: List<Party>): List<Party> {
            val notUsParties: MutableList<Party> = arrayListOf()
            for (party in parties) {
                if (serviceHub.storageService.myLegalIdentity != party) {
                    notUsParties.add(party)
                }
            }
            return notUsParties
        }

    }
}
