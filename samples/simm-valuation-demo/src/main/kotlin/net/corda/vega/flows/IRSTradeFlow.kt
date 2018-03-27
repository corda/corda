package net.corda.vega.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.annotations.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.finance.flows.TwoPartyDealFlow
import net.corda.vega.contracts.IRSState
import net.corda.vega.contracts.SwapData

object IRSTradeFlow {
    @CordaSerializable
    data class OfferMessage(val notary: Party, val dealBeingOffered: IRSState)

    @InitiatingFlow
    @StartableByRPC
    class Requester(val swap: SwapData, val otherParty: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
            val notary = serviceHub.networkMapCache.notaryIdentities.first() // TODO We should pass the notary as a parameter to the flow, not leave it to random choice.
            val (buyer, seller) =
                    if (swap.buyer.second == ourIdentity.owningKey) {
                        Pair(ourIdentity, otherParty)
                    } else {
                        Pair(otherParty, ourIdentity)
                    }
            val offer = IRSState(swap, buyer, seller)

            logger.info("Handshake finished, sending IRS trade offer message")
            val session = initiateFlow(otherParty)
            val otherPartyAgreeFlag = session.sendAndReceive<Boolean>(OfferMessage(notary, offer)).unwrap { it }
            require(otherPartyAgreeFlag)

            return subFlow(TwoPartyDealFlow.Instigator(
                    session,
                    TwoPartyDealFlow.AutoOffer(notary, offer)))
        }

    }

    @InitiatedBy(Requester::class)
    class Receiver(private val replyToSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("IRSTradeFlow receiver started")
            logger.info("Handshake finished, awaiting IRS trade offer")

            val offer = replyToSession.receive<OfferMessage>().unwrap { it }
            // Automatically agree - in reality we'd vet the offer message
            require(serviceHub.networkMapCache.notaryIdentities.contains(offer.notary))
            replyToSession.send(true)
            subFlow(TwoPartyDealFlow.Acceptor(replyToSession))
        }
    }
}
