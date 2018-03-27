package net.corda.docs.tutorial.flowstatemachines

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.annotations.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.flows.TwoPartyTradeFlow
import java.util.*

// DOCSTART 1
object TwoPartyTradeFlow {
    class UnacceptablePriceException(givenPrice: Amount<Currency>) : FlowException("Unacceptable price: $givenPrice")
    class AssetMismatchException(val expectedTypeName: String, val typeName: String) : FlowException() {
        override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
    }

    /**
     * This object is serialised to the network and is the first flow message the seller sends to the buyer.
     *
     * @param payToIdentity anonymous identity of the seller, for payment to be sent to.
     */
    @CordaSerializable
    data class SellerTradeInfo(
            val price: Amount<Currency>,
            val payToIdentity: PartyAndCertificate
    )

    open class Seller(private val otherSideSession: FlowSession,
                      private val assetToSell: StateAndRef<OwnableState>,
                      private val price: Amount<Currency>,
                      private val myParty: PartyAndCertificate,
                      override val progressTracker: ProgressTracker = TwoPartyTradeFlow.Seller.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            fun tracker() = ProgressTracker()
        }

        @Suspendable
        override fun call(): SignedTransaction {
            TODO()
        }
    }

    open class Buyer(private val sellerSession: FlowSession,
                     private val notary: Party,
                     private val acceptablePrice: Amount<Currency>,
                     private val typeToBuy: Class<out OwnableState>,
                     private val anonymous: Boolean) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            TODO()
        }
    }
}
// DOCEND 1