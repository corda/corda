package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.sumCashBy
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
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

    /**
     * This object is serialised to the network and is the first flow message the seller sends to the buyer.
     *
     * @param assetForSaleIdentity anonymous identity of the seller, who currently owns of the asset for sale. If
     * null the asset must be owned by the well known identity of the seller.
     * @param payToIdentity anonymous identity of the seller, for payment to be sent to.
     */
    @CordaSerializable
    data class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val assetForSaleIdentity: AnonymousPartyAndPath?,
            val price: Amount<Currency>,
            val payToIdentity: AnonymousPartyAndPath
    )

    open class Seller(val otherParty: Party,
                      val notaryNode: NodeInfo,
                      val assetToSell: StateAndRef<OwnableState>,
                      val price: Amount<Currency>,
                      val me: AnonymousPartyAndPath,
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
            val identity = if (assetToSell.state.data.owner == serviceHub.myInfo.legalIdentity)
                null
            else
                serviceHub.identityService.anonymousFromKey(assetToSell.state.data.owner.owningKey) ?: throw IllegalArgumentException("Asset to sell is owned by an unknown identity")
            // Make the first message we'll send to kick off the flow.
            val hello = SellerTradeInfo(assetToSell, identity, price, me)
            // What we get back from the other side is a transaction that *might* be valid and acceptable to us,
            // but we must check it out thoroughly before we sign!
            // SendTransactionFlow allows otherParty to access our data to resolve the transaction.
            subFlow(SendStateAndRefFlow(otherParty, listOf(assetToSell)))
            send(otherParty, hello)

            // Verify and sign the transaction.
            progressTracker.currentStep = VERIFYING_AND_SIGNING
            // DOCSTART 5
            val signTransactionFlow = object : SignTransactionFlow(otherParty, VERIFYING_AND_SIGNING.childProgressTracker()) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // Do KYC checks on all participants. Should this reject transactions involving anyone except us and
                    // the counterparty?
                    val states: Iterable<ContractState> = (stx.tx.inputs.map { serviceHub.loadState(it).data } + stx.tx.outputs.map { it.data })
                    states.forEach { state ->
                        state.participants.forEach { anon ->
                            require(serviceHub.identityService.partyFromAnonymous(anon) != null) { "Transaction state ${state} involves unknown participant ${anon}" }
                        }
                    }

                    if (stx.tx.outputStates.sumCashBy(me.party).withoutIssuer() != price)
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
            val (assetForSale, tradeRequest) = receiveAndValidateTradeRequest()

            // Create the identity we'll be paying to, and send the counterparty proof it's us for KYC purposes
            val buyerAnonymousIdentity = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, false)

            // Put together a proposed transaction that performs the trade, and sign it.
            progressTracker.currentStep = SIGNING
            val (ptx, identities, cashSigningPubKeys) = assembleSharedTX(assetForSale, tradeRequest, buyerAnonymousIdentity)

            // Now sign the transaction with whatever keys we need to move the cash.
            val partSignedTx = serviceHub.signInitialTransaction(ptx, cashSigningPubKeys)

            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow(otherParty, ptx.toWireTransaction()))

            // Send the signed transaction to the seller, who must then sign it themselves and commit
            // it to the ledger by sending it to the notary.
            progressTracker.currentStep = COLLECTING_SIGNATURES
            val inputKeys = (cashSigningPubKeys + tradeRequest.assetForSaleIdentity?.party?.owningKey).filterNotNull()
            val twiceSignedTx = subFlow(CollectSignaturesFlow(partSignedTx, identities, inputKeys, COLLECTING_SIGNATURES.childProgressTracker()))

            // Notarise and record the transaction.
            progressTracker.currentStep = RECORDING
            return subFlow(FinalityFlow(twiceSignedTx)).single()
        }

        @Suspendable
        private fun receiveAndValidateTradeRequest(): Pair<StateAndRef<OwnableState>, SellerTradeInfo> {
            val assetForSale = subFlow(ReceiveStateAndRefFlow<OwnableState>(otherParty)).single()
            return assetForSale to receive<SellerTradeInfo>(otherParty).unwrap {
                progressTracker.currentStep = VERIFYING
                // What is the seller trying to sell us?
                val asset = it.assetForSale.state.data
                val assetTypeName = asset.javaClass.name

                // Perform KYC checks on the asset we're being sold. The asset must either be owned by the well known
                // identity of the counterparty, or we must be able to prove the owner is a confidential identity of
                // the counterparty.
                if (it.assetForSaleIdentity == null) {
                    require(asset.owner == otherParty)
                } else {
                    serviceHub.identityService.verifyAndRegisterAnonymousIdentity(it.assetForSaleIdentity, otherParty)
                    require(asset.owner == it.assetForSaleIdentity.party) { "KYC: Owner of the asset being sold must be the seller" }
                }

                // Register the identity we're about to send payment to. This shouldn't be the same as the asset owner
                // identity, so that anonymity is enforced.
                serviceHub.identityService.verifyAndRegisterAnonymousIdentity(it.payToIdentity, otherParty)

                if (it.price > acceptablePrice)
                    throw UnacceptablePriceException(it.price)
                if (!typeToBuy.isInstance(asset))
                    throw AssetMismatchException(typeToBuy.name, assetTypeName)

                it
            }
        }

        @Suspendable
        private fun assembleSharedTX(assetForSale: StateAndRef<OwnableState>, tradeRequest: SellerTradeInfo, buyerAnonymousIdentity: AnonymousPartyAndPath): SharedTx {
            val ptx = TransactionBuilder(notary)

            // Add input and output states for the movement of cash, by using the Cash contract to generate the states
            val (tx, cashSigningPubKeys) = serviceHub.vaultService.generateSpend(ptx, tradeRequest.price, tradeRequest.payToIdentity.party)

            // Add inputs/outputs/a command for the movement of the asset.
            tx.addInputState(assetForSale)

            val (command, state) = tradeRequest.assetForSale.state.data.withNewOwner(buyerAnonymousIdentity.party)
            tx.addOutputState(state, tradeRequest.assetForSale.state.notary)
            tx.addCommand(command, tradeRequest.assetForSale.state.data.owner.owningKey)

            // We set the transaction's time-window: it may be that none of the contracts need this!
            // But it can't hurt to have one.
            val currentTime = serviceHub.clock.instant()
            tx.setTimeWindow(currentTime, 30.seconds)

            // TODO: Should have helper functions to do this automatically for us rather than manually
            val identities = listOf(
                    Pair(serviceHub.myInfo.legalIdentity, buyerAnonymousIdentity),
                    Pair(otherParty, tradeRequest.payToIdentity)
            ).toMap()

            return SharedTx(tx, identities, cashSigningPubKeys)
        }
        // DOCEND 1

        data class SharedTx(val tx: TransactionBuilder, val identities: Map<Party, AnonymousPartyAndPath>, val cashSigningPubKeys: List<PublicKey>>)
    }
}
