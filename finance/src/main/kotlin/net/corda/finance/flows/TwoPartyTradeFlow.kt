package net.corda.finance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCashBy
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
                      private val myParty: PartyAndCertificate, // TODO Left because in tests it's used to pass anonymous party.
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
            val hello = SellerTradeInfo(price, myParty)
            // What we get back from the other side is a transaction that *might* be valid and acceptable to us,
            // but we must check it out thoroughly before we sign!
            // SendTransactionFlow allows seller to access our data to resolve the transaction.
            subFlow(SendStateAndRefFlow(otherSideSession, listOf(assetToSell)))
            otherSideSession.send(hello)

            // Verify and sign the transaction.
            progressTracker.currentStep = VERIFYING_AND_SIGNING

            // DOCSTART 07
            // Sync identities to ensure we know all of the identities involved in the transaction we're about to
            // be asked to sign
            subFlow(IdentitySyncFlow.Receive(otherSideSession))
            // DOCEND 07

            // DOCSTART 5
            val signTransactionFlow = object : SignTransactionFlow(otherSideSession, VERIFYING_AND_SIGNING.childProgressTracker()) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // Verify that we know who all the participants in the transaction are
                    val states: Iterable<ContractState> = serviceHub.loadStates(stx.tx.inputs.toSet()).map { it.state.data } + stx.tx.outputs.map { it.data }
                    states.forEach { state ->
                        state.participants.forEach { anon ->
                            require(serviceHub.identityService.wellKnownPartyFromAnonymous(anon) != null) {
                                "Transaction state $state involves unknown participant $anon"
                            }
                        }
                    }

                    if (stx.tx.outputStates.sumCashBy(myParty.party).withoutIssuer() != price)
                        throw FlowException("Transaction is not sending us the right amount of cash")
                }
            }

            val txId = subFlow(signTransactionFlow).id
            // DOCEND 5

            return waitForLedgerCommit(txId)
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

    open class Buyer(private val sellerSession: FlowSession,
                     private val notary: Party,
                     private val acceptablePrice: Amount<Currency>,
                     private val typeToBuy: Class<out OwnableState>,
                     private val anonymous: Boolean) : FlowLogic<SignedTransaction>() {
        constructor(otherSideSession: FlowSession, notary: Party, acceptablePrice: Amount<Currency>, typeToBuy: Class<out OwnableState>) :
                this(otherSideSession, notary, acceptablePrice, typeToBuy, true)

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
            val (assetForSale, tradeRequest) = receiveAndValidateTradeRequest()

            // Create the identity we'll be paying to, and send the counterparty proof we own the identity
            val buyerAnonymousIdentity = if (anonymous)
                serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)
            else
                ourIdentityAndCert
            // Put together a proposed transaction that performs the trade, and sign it.
            progressTracker.currentStep = SIGNING
            val (ptx, cashSigningPubKeys) = assembleSharedTX(assetForSale, tradeRequest, buyerAnonymousIdentity)

            // DOCSTART 6
            // Now sign the transaction with whatever keys we need to move the cash.
            val partSignedTx = serviceHub.signInitialTransaction(ptx, cashSigningPubKeys)

            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow.Send(sellerSession, ptx.toWireTransaction2(serviceHub)))

            // Send the signed transaction to the seller, who must then sign it themselves and commit
            // it to the ledger by sending it to the notary.
            progressTracker.currentStep = COLLECTING_SIGNATURES
            val sellerSignature = subFlow(CollectSignatureFlow(partSignedTx, sellerSession, sellerSession.counterparty.owningKey))
            val twiceSignedTx = partSignedTx + sellerSignature
            // DOCEND 6

            // Notarise and record the transaction.
            progressTracker.currentStep = RECORDING
            return subFlow(FinalityFlow(twiceSignedTx))
        }

        @Suspendable
        private fun receiveAndValidateTradeRequest(): Pair<StateAndRef<OwnableState>, SellerTradeInfo> {
            val assetForSale = subFlow(ReceiveStateAndRefFlow<OwnableState>(sellerSession)).single()
            return assetForSale to sellerSession.receive<SellerTradeInfo>().unwrap {
                progressTracker.currentStep = VERIFYING
                // What is the seller trying to sell us?
                val asset = assetForSale.state.data
                val assetTypeName = asset.javaClass.name

                // The asset must either be owned by the well known identity of the counterparty, or we must be able to
                // prove the owner is a confidential identity of the counterparty.
                val assetForSaleIdentity = serviceHub.identityService.wellKnownPartyFromAnonymous(asset.owner)
                require(assetForSaleIdentity == sellerSession.counterparty)

                // Register the identity we're about to send payment to. This shouldn't be the same as the asset owner
                // identity, so that anonymity is enforced.
                val wellKnownPayToIdentity = serviceHub.identityService.verifyAndRegisterIdentity(it.payToIdentity) ?: it.payToIdentity
                require(wellKnownPayToIdentity.party == sellerSession.counterparty) { "Well known identity to pay to must match counterparty identity" }

                if (it.price > acceptablePrice)
                    throw UnacceptablePriceException(it.price)
                if (!typeToBuy.isInstance(asset))
                    throw AssetMismatchException(typeToBuy.name, assetTypeName)

                it
            }
        }

        @Suspendable
        private fun assembleSharedTX(assetForSale: StateAndRef<OwnableState>, tradeRequest: SellerTradeInfo, buyerAnonymousIdentity: PartyAndCertificate): SharedTx {
            val ptx = TransactionBuilder(notary)

            // Add input and output states for the movement of cash, by using the Cash contract to generate the states
            val (tx, cashSigningPubKeys) = Cash.generateSpend(serviceHub, ptx, tradeRequest.price, ourIdentityAndCert, tradeRequest.payToIdentity.party)

            // Add inputs/outputs/a command for the movement of the asset.
            tx.addInputState(assetForSale)

            val (command, state) = assetForSale.state.data.withNewOwner(buyerAnonymousIdentity.party)
            tx.addOutputState(state, assetForSale.state.contract, assetForSale.state.notary)
            tx.addCommand(command, assetForSale.state.data.owner.owningKey)

            // We set the transaction's time-window: it may be that none of the contracts need this!
            // But it can't hurt to have one.
            val currentTime = serviceHub.clock.instant()
            tx.setTimeWindow(currentTime, 30.seconds)

            return SharedTx(tx, cashSigningPubKeys)
        }
        // DOCEND 1

        data class SharedTx(val tx: TransactionBuilder, val cashSigningPubKeys: List<PublicKey>)
    }
}
