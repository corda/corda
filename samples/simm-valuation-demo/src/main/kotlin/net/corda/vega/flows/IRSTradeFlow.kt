package net.corda.vega.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.PluginServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.TwoPartyDealFlow
import net.corda.vega.contracts.IRSState
import net.corda.vega.contracts.OGTrade
import net.corda.vega.contracts.SwapData

object IRSTradeFlow {
    data class OfferMessage(val notary: Party, val dealBeingOffered: IRSState)

    class Requester(val swap: SwapData, val otherParty: Party) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            require(serviceHub.networkMapCache.notaryNodes.isNotEmpty()) { "No notary nodes registered" }
            val notary = serviceHub.networkMapCache.notaryNodes.first().notaryIdentity
            val myIdentity = serviceHub.myInfo.legalIdentity
            val (buyer, seller) =
                    if (swap.buyer.second == myIdentity.name) {
                        Pair(myIdentity, otherParty)
                    } else {
                        Pair(otherParty, myIdentity)
                    }
            val offer = IRSState(swap, buyer, seller, OGTrade())

            logger.info("Handshake finished, sending IRS trade offer message")
            val otherPartyAgreeFlag = sendAndReceive<Boolean>(otherParty, OfferMessage(notary, offer)).unwrap { it }
            require(otherPartyAgreeFlag)

            return subFlow(TwoPartyDealFlow.Instigator(
                    otherParty,
                    TwoPartyDealFlow.AutoOffer(notary, offer),
                    serviceHub.legalIdentityKey), shareParentSessions = true)
        }
    }

    class Service(services: PluginServiceHub) {
        init {
            services.registerFlowInitiator(Requester::class, ::Receiver)
        }
    }

    class Receiver(private val replyToParty: Party) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            logger.info("IRSTradeFlow receiver started")
            logger.info("Handshake finished, awaiting IRS trade offer")

            val offer = receive<OfferMessage>(replyToParty).unwrap { it }
            // Automatically agree - in reality we'd vet the offer message
            require(serviceHub.networkMapCache.notaryNodes.map { it.notaryIdentity }.contains(offer.notary))
            send(replyToParty, true)
            subFlow(TwoPartyDealFlow.Acceptor(replyToParty), shareParentSessions = true)
        }
    }
}
