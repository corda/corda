package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.sumCashBy
import net.corda.core.contracts.*
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.seconds
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*

/**
 * This asset trading flow implements a "delivery vs payment" type swap. It has two parties (B and S for buyer
 * and seller) and the following steps:
 *
 * 1. S sends the [StateAndRef] pointing to what they want to sell to B, along with info about the price they require
 *    B to pay. For example this has probably been agreed on an exchange.
 * 2. B sends to S a [SignedTransaction] that includes the state as input, B's cash as input, the state with the new
 *    owner key as output, and any change cash as output. It contains a single signature from B but isn't valid because
 *    it lacks a signature from S authorising movement of the asset.
 * 3. S signs it and commits it to the ledger, notarising it and distributing the final signed transaction back
 *    to B.
 *
 * Assuming no malicious termination, they both end the flow being in possession of a valid, signed transaction
 * that represents an atomic asset swap.
 *
 * Note that it's the *seller* who initiates contact with the buyer, not vice-versa as you might imagine.
 */
object TwoPartyTradeFlow {
    // TODO: Common elements in multi-party transaction consensus and signing should be refactored into a superclass of this
    // and [AbstractStateReplacementFlow].

    class UnacceptablePriceException(givenPrice: Amount<Currency>) : FlowException("Unacceptable price: $givenPrice")

    class AssetMismatchException(val expectedTypeName: String, val typeName: String) : FlowException() {
        override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
    }

    // This object is serialised to the network and is the first flow message the seller sends to the buyer.
    @CordaSerializable
    data class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val price: Amount<Currency>,
            val sellerOwner: AbstractParty
    )

    open class Seller(val otherParty: Party,
                      val notaryNode: NodeInfo,
                      val assetToSell: StateAndRef<OwnableState>,
                      val price: Amount<Currency>,
                      val me: AbstractParty,
                      override val progressTracker: ProgressTracker = Seller.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object AWAITING_PROPOSAL : ProgressTracker.Step("Awaiting transaction proposal")
            // DOCSTART 3
            object VERIFYING_AND_SIGNING : ProgressTracker.Step("Verifying and signing transaction proposal") {
                override fun childProgressTracker() = SignTransactionFlow.tracker()
            }
            // DOCEND 3

            fun tracker() = ProgressTracker(AWAITING_PROPOSAL, VERIFYING_AND_SIGNING)
        }

        // DOCSTART 4
        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = AWAITING_PROPOSAL
            // Make the first message we'll send to kick off the flow.
            val hello = SellerTradeInfo(assetToSell, price, me)
            // What we get back from the other side is a transaction that *might* be valid and acceptable to us,
            // but we must check it out thoroughly before we sign!
            send(otherParty, hello)

            // Verify and sign the transaction.
            progressTracker.currentStep = VERIFYING_AND_SIGNING
            // DOCSTART 5
            val signTransactionFlow = object : SignTransactionFlow(otherParty, VERIFYING_AND_SIGNING.childProgressTracker()) {
                override fun checkTransaction(stx: SignedTransaction) {
                    if (stx.tx.outputs.map { it.data }.sumCashBy(me).withoutIssuer() != price)
                        throw FlowException("Transaction is not sending us the right amount of cash")
                }
            }
            return subFlow(signTransactionFlow)
            // DOCEND 5
        }
        // DOCEND 4

        // Following comment moved here so that it doesn't appear in the docsite:
        // There are all sorts of funny games a malicious secondary might play with it sends maybeSTX,
        // we should fix them:
        //
        // - This tx may attempt to send some assets we aren't intending to sell to the secondary, if
        //   we're reusing keys! So don't reuse keys!
        // - This tx may include output states that impose odd conditions on the movement of the cash,
        //   once we implement state pairing.
        //
        // but the goal of this code is not to be fully secure (yet), but rather, just to find good ways to
        // express flow state machines on top of the messaging layer.
    }

    open class Buyer(val otherParty: Party,
                     val notary: Party,
                     val acceptablePrice: Amount<Currency>,
                     val typeToBuy: Class<out OwnableState>) : FlowLogic<SignedTransaction>() {
        // DOCSTART 2
        object RECEIVING : ProgressTracker.Step("Waiting for seller trading info")
        object VERIFYING : ProgressTracker.Step("Verifying seller assets")
        object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object RECORDING : ProgressTracker.Step("Recording completed transaction") {
            // TODO: Currently triggers a race condition on Team City. See https://github.com/corda/corda/issues/733.
            // override fun childProgressTracker() = FinalityFlow.tracker()
        }

        override val progressTracker = ProgressTracker(RECEIVING, VERIFYING, SIGNING, COLLECTING_SIGNATURES, RECORDING)
        // DOCEND 2

        // DOCSTART 1
        @Suspendable
        override fun call(): SignedTransaction {
            // Wait for a trade request to come in from the other party.
            progressTracker.currentStep = RECEIVING
            val tradeRequest = receiveAndValidateTradeRequest()

            // Put together a proposed transaction that performs the trade, and sign it.
            progressTracker.currentStep = SIGNING
            val (ptx, cashSigningPubKeys) = assembleSharedTX(tradeRequest)
            val partSignedTx = signWithOurKeys(cashSigningPubKeys, ptx)

            // Send the signed transaction to the seller, who must then sign it themselves and commit
            // it to the ledger by sending it to the notary.
            progressTracker.currentStep = COLLECTING_SIGNATURES
            val twiceSignedTx = subFlow(CollectSignaturesFlow(partSignedTx, COLLECTING_SIGNATURES.childProgressTracker()))

            // Notarise and record the transaction.
            progressTracker.currentStep = RECORDING
            return subFlow(FinalityFlow(twiceSignedTx, setOf(otherParty, serviceHub.myInfo.legalIdentity))).single()
        }

        @Suspendable
        private fun receiveAndValidateTradeRequest(): SellerTradeInfo {
            val maybeTradeRequest = receive<SellerTradeInfo>(otherParty)

            progressTracker.currentStep = VERIFYING
            maybeTradeRequest.unwrap {
                // What is the seller trying to sell us?
                val asset = it.assetForSale.state.data
                val assetTypeName = asset.javaClass.name

                if (it.price > acceptablePrice)
                    throw UnacceptablePriceException(it.price)
                if (!typeToBuy.isInstance(asset))
                    throw AssetMismatchException(typeToBuy.name, assetTypeName)

                // Check that the state being sold to us is in a valid chain of transactions, i.e. that the
                // seller has a valid chain of custody proving that they own the thing they're selling.
                subFlow(ResolveTransactionsFlow(setOf(it.assetForSale.ref.txhash), otherParty))

                return it
            }
        }

        private fun signWithOurKeys(cashSigningPubKeys: List<PublicKey>, ptx: TransactionBuilder): SignedTransaction {
            // Now sign the transaction with whatever keys we need to move the cash.
            return serviceHub.signInitialTransaction(ptx, cashSigningPubKeys)
        }

        @Suspendable
        private fun assembleSharedTX(tradeRequest: SellerTradeInfo): Pair<TransactionBuilder, List<PublicKey>> {
            val ptx = TransactionType.General.Builder(notary)

            // Add input and output states for the movement of cash, by using the Cash contract to generate the states
            val (tx, cashSigningPubKeys) = serviceHub.vaultService.generateSpend(ptx, tradeRequest.price, tradeRequest.sellerOwner)

            // Add inputs/outputs/a command for the movement of the asset.
            tx.addInputState(tradeRequest.assetForSale)

            // Just pick some new public key for now. This won't be linked with our identity in any way, which is what
            // we want for privacy reasons: the key is here ONLY to manage and control ownership, it is not intended to
            // reveal who the owner actually is. The key management service is expected to derive a unique key from some
            // initial seed in order to provide privacy protection.
            val freshKey = serviceHub.keyManagementService.freshKey()
            val (command, state) = tradeRequest.assetForSale.state.data.withNewOwner(AnonymousParty(freshKey))
            tx.addOutputState(state, tradeRequest.assetForSale.state.notary)
            tx.addCommand(command, tradeRequest.assetForSale.state.data.owner.owningKey)

            // We set the transaction's time-window: it may be that none of the contracts need this!
            // But it can't hurt to have one.
            val currentTime = serviceHub.clock.instant()
            tx.setTimeWindow(currentTime, 30.seconds)
            return Pair(tx, cashSigningPubKeys)
        }
        // DOCEND 1
    }
}
