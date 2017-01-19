package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.sumCashBy
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.trace
import java.security.KeyPair
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
 * 3. S signs it and hands the now finalised SignedWireTransaction back to B.
 *
 * Assuming no malicious termination, they both end the flow being in posession of a valid, signed transaction
 * that represents an atomic asset swap.
 *
 * Note that it's the *seller* who initiates contact with the buyer, not vice-versa as you might imagine.
 *
 * To initiate the flow, use either the [runBuyer] or [runSeller] methods, depending on which side of the trade
 * your node is taking. These methods return a future which will complete once the trade is over and a fully signed
 * transaction is available: you can either block your thread waiting for the flow to complete by using
 * [ListenableFuture.get] or more usefully, register a callback that will be invoked when the time comes.
 *
 * To see an example of how to use this class, look at the unit tests.
 */
// TODO: Common elements in multi-party transaction consensus and signing should be refactored into a superclass of this
// and [AbstractStateReplacementFlow].
object TwoPartyTradeFlow {

    class UnacceptablePriceException(val givenPrice: Amount<Currency>) : Exception("Unacceptable price: $givenPrice")
    class AssetMismatchException(val expectedTypeName: String, val typeName: String) : Exception() {
        override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
    }

    // This object is serialised to the network and is the first flow message the seller sends to the buyer.
    data class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val price: Amount<Currency>,
            val sellerOwnerKey: CompositeKey
    )

    data class SignaturesFromSeller(val sellerSig: DigitalSignature.WithKey,
                                    val notarySig: DigitalSignature.WithKey)

    open class Seller(val otherParty: Party,
                      val notaryNode: NodeInfo,
                      val assetToSell: StateAndRef<OwnableState>,
                      val price: Amount<Currency>,
                      val myKeyPair: KeyPair,
                      override val progressTracker: ProgressTracker = Seller.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object AWAITING_PROPOSAL : ProgressTracker.Step("Awaiting transaction proposal")

            object VERIFYING : ProgressTracker.Step("Verifying transaction proposal")

            object SIGNING : ProgressTracker.Step("Signing transaction")

            // DOCSTART 3
            object NOTARY : ProgressTracker.Step("Getting notary signature") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }
            // DOCEND 3

            object SENDING_SIGS : ProgressTracker.Step("Sending transaction signatures to buyer")

            fun tracker() = ProgressTracker(AWAITING_PROPOSAL, VERIFYING, SIGNING, NOTARY, SENDING_SIGS)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val partialTX: SignedTransaction = receiveAndCheckProposedTransaction()

            // These two steps could be done in parallel, in theory. Our framework doesn't support that yet though.
            val ourSignature = calculateOurSignature(partialTX)
            val allPartySignedTx = partialTX + ourSignature
            val notarySignature = getNotarySignature(allPartySignedTx)
            return sendSignatures(allPartySignedTx, ourSignature, notarySignature)
        }

        @Suspendable
        private fun getNotarySignature(stx: SignedTransaction): DigitalSignature.WithKey {
            progressTracker.currentStep = NOTARY
            return subFlow(NotaryFlow.Client(stx))
        }

        @Suspendable
        private fun receiveAndCheckProposedTransaction(): SignedTransaction {
            progressTracker.currentStep = AWAITING_PROPOSAL

            val myPublicKey = myKeyPair.public.composite
            // Make the first message we'll send to kick off the flow.
            val hello = SellerTradeInfo(assetToSell, price, myPublicKey)

            val maybeSTX = sendAndReceive<SignedTransaction>(otherParty, hello)

            progressTracker.currentStep = VERIFYING

            maybeSTX.unwrap {
                progressTracker.nextStep()

                // Check that the tx proposed by the buyer is valid.
                val wtx: WireTransaction = it.verifySignatures(myPublicKey, notaryNode.notaryIdentity.owningKey)
                logger.trace { "Received partially signed transaction: ${it.id}" }

                // Download and check all the things that this transaction depends on and verify it is contract-valid,
                // even though it is missing signatures.
                subFlow(ResolveTransactionsFlow(wtx, otherParty))

                if (wtx.outputs.map { it.data }.sumCashBy(myPublicKey).withoutIssuer() != price)
                    throw IllegalArgumentException("Transaction is not sending us the right amount of cash")

                // There are all sorts of funny games a malicious secondary might play here, we should fix them:
                //
                // - This tx may attempt to send some assets we aren't intending to sell to the secondary, if
                //   we're reusing keys! So don't reuse keys!
                // - This tx may include output states that impose odd conditions on the movement of the cash,
                //   once we implement state pairing.
                //
                // but the goal of this code is not to be fully secure (yet), but rather, just to find good ways to
                // express flow state machines on top of the messaging layer.

                return it
            }
        }

        open fun calculateOurSignature(partialTX: SignedTransaction): DigitalSignature.WithKey {
            progressTracker.currentStep = SIGNING
            return myKeyPair.signWithECDSA(partialTX.id)
        }

        @Suspendable
        private fun sendSignatures(allPartySignedTx: SignedTransaction, ourSignature: DigitalSignature.WithKey,
                                   notarySignature: DigitalSignature.WithKey): SignedTransaction {
            progressTracker.currentStep = SENDING_SIGS
            val fullySigned = allPartySignedTx + notarySignature

            logger.trace { "Built finished transaction, sending back to secondary!" }

            send(otherParty, SignaturesFromSeller(ourSignature, notarySignature))
            return fullySigned
        }
    }

    // DOCSTART 2
    open class Buyer(val otherParty: Party,
                     val notary: Party,
                     val acceptablePrice: Amount<Currency>,
                     val typeToBuy: Class<out OwnableState>) : FlowLogic<SignedTransaction>() {

        object RECEIVING : ProgressTracker.Step("Waiting for seller trading info")

        object VERIFYING : ProgressTracker.Step("Verifying seller assets")

        object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")

        object SWAPPING_SIGNATURES : ProgressTracker.Step("Swapping signatures with the seller")

        override val progressTracker = ProgressTracker(RECEIVING, VERIFYING, SIGNING, SWAPPING_SIGNATURES)

        // DOCSTART 1
        @Suspendable
        override fun call(): SignedTransaction {
            val tradeRequest = receiveAndValidateTradeRequest()

            progressTracker.currentStep = SIGNING
            val (ptx, cashSigningPubKeys) = assembleSharedTX(tradeRequest)
            val stx = signWithOurKeys(cashSigningPubKeys, ptx)

            val signatures = swapSignaturesWithSeller(stx)

            logger.trace { "Got signatures from seller, verifying ... " }

            val fullySigned = stx + signatures.sellerSig + signatures.notarySig
            fullySigned.verifySignatures()

            logger.trace { "Signatures received are valid. Trade complete! :-)" }
            return fullySigned
        }

        @Suspendable
        private fun receiveAndValidateTradeRequest(): SellerTradeInfo {
            progressTracker.currentStep = RECEIVING
            // Wait for a trade request to come in from the other side
            val maybeTradeRequest = receive<SellerTradeInfo>(otherParty)

            progressTracker.currentStep = VERIFYING
            maybeTradeRequest.unwrap {
                // What is the seller trying to sell us?
                val asset = it.assetForSale.state.data
                val assetTypeName = asset.javaClass.name
                logger.trace { "Got trade request for a $assetTypeName: ${it.assetForSale}" }

                if (it.price > acceptablePrice)
                    throw UnacceptablePriceException(it.price)
                if (!typeToBuy.isInstance(asset))
                    throw AssetMismatchException(typeToBuy.name, assetTypeName)

                // Check the transaction that contains the state which is being resolved.
                // We only have a hash here, so if we don't know it already, we have to ask for it.
                subFlow(ResolveTransactionsFlow(setOf(it.assetForSale.ref.txhash), otherParty))

                return it
            }
        }

        @Suspendable
        private fun swapSignaturesWithSeller(stx: SignedTransaction): SignaturesFromSeller {
            progressTracker.currentStep = SWAPPING_SIGNATURES
            logger.trace { "Sending partially signed transaction to seller" }

            // TODO: Protect against the seller terminating here and leaving us in the lurch without the final tx.

            return sendAndReceive<SignaturesFromSeller>(otherParty, stx).unwrap { it }
        }

        private fun signWithOurKeys(cashSigningPubKeys: List<CompositeKey>, ptx: TransactionBuilder): SignedTransaction {
            // Now sign the transaction with whatever keys we need to move the cash.
            for (publicKey in cashSigningPubKeys.keys) {
                val privateKey = serviceHub.keyManagementService.toPrivate(publicKey)
                ptx.signWith(KeyPair(publicKey, privateKey))
            }

            return ptx.toSignedTransaction(checkSufficientSignatures = false)
        }

        private fun assembleSharedTX(tradeRequest: SellerTradeInfo): Pair<TransactionBuilder, List<CompositeKey>> {
            val ptx = TransactionType.General.Builder(notary)

            // Add input and output states for the movement of cash, by using the Cash contract to generate the states
            val (tx, cashSigningPubKeys) = serviceHub.vaultService.generateSpend(ptx, tradeRequest.price, tradeRequest.sellerOwnerKey)

            // Add inputs/outputs/a command for the movement of the asset.
            tx.addInputState(tradeRequest.assetForSale)

            // Just pick some new public key for now. This won't be linked with our identity in any way, which is what
            // we want for privacy reasons: the key is here ONLY to manage and control ownership, it is not intended to
            // reveal who the owner actually is. The key management service is expected to derive a unique key from some
            // initial seed in order to provide privacy protection.
            val freshKey = serviceHub.keyManagementService.freshKey()
            val (command, state) = tradeRequest.assetForSale.state.data.withNewOwner(freshKey.public.composite)
            tx.addOutputState(state, tradeRequest.assetForSale.state.notary)
            tx.addCommand(command, tradeRequest.assetForSale.state.data.owner)

            // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
            // to have one.
            val currentTime = serviceHub.clock.instant()
            tx.setTime(currentTime, 30.seconds)
            return Pair(tx, cashSigningPubKeys)
        }
        // DOCEND 1
    }
}
