package demos.protocols

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import core.contracts.DealState
import core.crypto.Party
import core.contracts.SignedTransaction
import core.messaging.SingleMessageRecipient
import core.node.Node
import core.protocols.ProtocolLogic
import core.random63BitValue
import core.serialization.deserialize
import core.utilities.ANSIProgressRenderer
import core.utilities.ProgressTracker
import protocols.TwoPartyDealProtocol

/**
 * This whole class is really part of a demo just to initiate the agreement of a deal with a simple
 * API call from a single party without bi-directional access to the database of offers etc.
 *
 * In the "real world", we'd probably have the offers sitting in the platform prior to the agreement step
 * or the protocol would have to reach out to external systems (or users) to verify the deals
 */
object AutoOfferProtocol {
    val TOPIC = "autooffer.topic"

    data class AutoOfferMessage(val otherSide: SingleMessageRecipient,
                                val otherSessionID: Long, val dealBeingOffered: DealState)

    object Handler {

        object RECEIVED : ProgressTracker.Step("Received offer")
        object DEALING : ProgressTracker.Step("Starting the deal protocol")

        fun tracker() = ProgressTracker(RECEIVED, DEALING).apply {
            childrenFor[DEALING] = TwoPartyDealProtocol.Primary.tracker()
        }

        class Callback(val success: (SignedTransaction) -> Unit) : FutureCallback<SignedTransaction> {
            override fun onFailure(t: Throwable?) {
                // TODO handle exceptions
            }

            override fun onSuccess(st: SignedTransaction?) {
                success(st!!)
            }
        }

        fun register(node: Node) {
            node.net.addMessageHandler("$TOPIC.0") { msg, registration ->
                val progressTracker = tracker()
                ANSIProgressRenderer.progressTracker = progressTracker
                progressTracker.currentStep = RECEIVED
                val autoOfferMessage = msg.data.deserialize<AutoOfferMessage>()
                // Put the deal onto the ledger
                progressTracker.currentStep = DEALING
                // TODO required as messaging layer does not currently queue messages that arrive before we expect them
                Thread.sleep(100)
                val seller = TwoPartyDealProtocol.Instigator(autoOfferMessage.otherSide, node.services.networkMapCache.notaryNodes.first(),
                        autoOfferMessage.dealBeingOffered, node.services.keyManagementService.freshKey(), autoOfferMessage.otherSessionID, progressTracker.childrenFor[DEALING]!!)
                val future = node.smm.add("${TwoPartyDealProtocol.DEAL_TOPIC}.seller", seller)
                // This is required because we are doing child progress outside of a subprotocol.  In future, we should just wrap things like this in a protocol to avoid it
                Futures.addCallback(future, Callback() {
                    seller.progressTracker.currentStep = ProgressTracker.DONE
                    progressTracker.currentStep = ProgressTracker.DONE
                })
            }
        }

    }

    class Requester<T>(val dealToBeOffered: DealState) : ProtocolLogic<SignedTransaction>() {

        companion object {
            object RECEIVED : ProgressTracker.Step("Received API call")
            object ANNOUNCING : ProgressTracker.Step("Announcing to the peer node")
            object DEALING : ProgressTracker.Step("Starting the deal protocol")

            // We vend a progress tracker that already knows there's going to be a TwoPartyTradingProtocol involved at some
            // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
            // surprised when it appears as a new set of tasks below the current one.
            fun tracker() = ProgressTracker(RECEIVED, ANNOUNCING, DEALING).apply {
                childrenFor[DEALING] = TwoPartyDealProtocol.Secondary.tracker()
            }
        }

        override val progressTracker = tracker()

        init {
            progressTracker.currentStep = RECEIVED
        }

        @Suspendable
        override fun call(): SignedTransaction {
            require(serviceHub.networkMapCache.notaryNodes.isNotEmpty()) { "No notary nodes registered" }
            val ourSessionID = random63BitValue()

            val notary = serviceHub.networkMapCache.notaryNodes.first()
            // need to pick which ever party is not us
            val otherParty = notUs(*dealToBeOffered.parties).single()
            val otherNode = (serviceHub.networkMapCache.getNodeByLegalName(otherParty.name))
            requireNotNull(otherNode) { "Cannot identify other party " + otherParty.name + ", know about: " + serviceHub.networkMapCache.partyNodes.map { it.identity } }
            val otherSide = otherNode!!.address
            progressTracker.currentStep = ANNOUNCING
            send(TOPIC, otherSide, 0, AutoOfferMessage(serviceHub.networkService.myAddress, ourSessionID, dealToBeOffered))
            progressTracker.currentStep = DEALING
            val stx = subProtocol(TwoPartyDealProtocol.Acceptor(otherSide, notary.identity, dealToBeOffered, ourSessionID, progressTracker.childrenFor[DEALING]!!))
            return stx
        }

        fun notUs(vararg parties: Party): List<Party> {
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
