package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.DealState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.recordTransactions
import net.corda.core.node.services.ServiceType
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.trace
import net.corda.core.utilities.unwrap
import java.security.KeyPair

/**
 * Classes for manipulating a two party deal or agreement.
 *
 * TODO: The subclasses should probably be broken out into individual flows rather than making this an ever expanding collection of subclasses.
 *
 * TODO: Also, the term Deal is used here where we might prefer Agreement.
 *
 * TODO: Consider whether we can merge this with [TwoPartyTradeFlow]
 *
 */
object TwoPartyDealFlow {

    class DealMismatchException(val expectedDeal: ContractState, val actualDeal: ContractState) : Exception() {
        override fun toString() = "The submitted deal didn't match the expected: $expectedDeal vs $actualDeal"
    }

    class DealRefMismatchException(val expectedDeal: StateRef, val actualDeal: StateRef) : Exception() {
        override fun toString() = "The submitted deal didn't match the expected: $expectedDeal vs $actualDeal"
    }

    // This object is serialised to the network and is the first flow message the seller sends to the buyer.
    data class Handshake<out T>(val payload: T, val publicKey: CompositeKey)

    class SignaturesFromPrimary(val sellerSig: DigitalSignature.WithKey, val notarySig: DigitalSignature.WithKey)

    /**
     * [Primary] at the end sends the signed tx to all the regulator parties. This a seperate workflow which needs a
     * sepearate session with the regulator. This interface is used to do that in [Primary.getCounterpartyMarker].
     */
    interface MarkerForBogusRegulatorFlow

    /**
     * Abstracted bilateral deal flow participant that initiates communication/handshake.
     *
     * There's a good chance we can push at least some of this logic down into core flow logic
     * and helper methods etc.
     */
    abstract class Primary(override val progressTracker: ProgressTracker = Primary.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object AWAITING_PROPOSAL : ProgressTracker.Step("Handshaking and awaiting transaction proposal")
            object VERIFYING : ProgressTracker.Step("Verifying proposed transaction")
            object SIGNING : ProgressTracker.Step("Signing transaction")
            object NOTARY : ProgressTracker.Step("Getting notary signature")
            object SENDING_SIGS : ProgressTracker.Step("Sending transaction signatures to other party")
            object RECORDING : ProgressTracker.Step("Recording completed transaction")
            object COPYING_TO_REGULATOR : ProgressTracker.Step("Copying regulator")

            fun tracker() = ProgressTracker(AWAITING_PROPOSAL, VERIFYING, SIGNING, NOTARY, SENDING_SIGS, RECORDING, COPYING_TO_REGULATOR)
        }

        abstract val payload: Any
        abstract val notaryNode: NodeInfo
        abstract val otherParty: Party
        abstract val myKeyPair: KeyPair

        override fun getCounterpartyMarker(party: Party): Class<*> {
            return if (serviceHub.networkMapCache.regulatorNodes.any { it.legalIdentity == party }) {
                MarkerForBogusRegulatorFlow::class.java
            } else {
                super.getCounterpartyMarker(party)
            }
        }

        @Suspendable
        fun getPartialTransaction(): UntrustworthyData<SignedTransaction> {
            progressTracker.currentStep = AWAITING_PROPOSAL

            // Make the first message we'll send to kick off the flow.
            val hello = Handshake(payload, myKeyPair.public.composite)
            val maybeSTX = sendAndReceive<SignedTransaction>(otherParty, hello)

            return maybeSTX
        }

        @Suspendable
        fun verifyPartialTransaction(untrustedPartialTX: UntrustworthyData<SignedTransaction>): SignedTransaction {
            progressTracker.currentStep = VERIFYING

            untrustedPartialTX.unwrap { stx ->
                progressTracker.nextStep()

                // Check that the tx proposed by the buyer is valid.
                val wtx: WireTransaction = stx.verifySignatures(myKeyPair.public.composite, notaryNode.notaryIdentity.owningKey)
                logger.trace { "Received partially signed transaction: ${stx.id}" }

                checkDependencies(stx)

                // This verifies that the transaction is contract-valid, even though it is missing signatures.
                wtx.toLedgerTransaction(serviceHub).verify()

                // There are all sorts of funny games a malicious secondary might play here, we should fix them:
                //
                // - This tx may attempt to send some assets we aren't intending to sell to the secondary, if
                //   we're reusing keys! So don't reuse keys!
                // - This tx may include output states that impose odd conditions on the movement of the cash,
                //   once we implement state pairing.
                //
                // but the goal of this code is not to be fully secure (yet), but rather, just to find good ways to
                // express flow state machines on top of the messaging layer.

                return stx
            }
        }

        @Suspendable
        private fun checkDependencies(stx: SignedTransaction) {
            // Download and check all the transactions that this transaction depends on, but do not check this
            // transaction itself.
            val dependencyTxIDs = stx.tx.inputs.map { it.txhash }.toSet()
            subFlow(ResolveTransactionsFlow(dependencyTxIDs, otherParty))
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val stx: SignedTransaction = verifyPartialTransaction(getPartialTransaction())

            // These two steps could be done in parallel, in theory. Our framework doesn't support that yet though.
            val ourSignature = computeOurSignature(stx)
            val allPartySignedTx = stx + ourSignature
            val notarySignature = getNotarySignature(allPartySignedTx)

            val fullySigned = sendSignatures(allPartySignedTx, ourSignature, notarySignature)

            progressTracker.currentStep = RECORDING

            serviceHub.recordTransactions(fullySigned)

            logger.trace { "Deal stored" }

            progressTracker.currentStep = COPYING_TO_REGULATOR
            val regulators = serviceHub.networkMapCache.regulatorNodes
            if (regulators.isNotEmpty()) {
                // Copy the transaction to every regulator in the network. This is obviously completely bogus, it's
                // just for demo purposes.
                regulators.forEach { send(it.serviceIdentities(ServiceType.regulator).first(), fullySigned) }
            }

            return fullySigned
        }

        @Suspendable
        private fun getNotarySignature(stx: SignedTransaction): DigitalSignature.WithKey {
            progressTracker.currentStep = NOTARY
            return subFlow(NotaryFlow.Client(stx))
        }

        open fun computeOurSignature(partialTX: SignedTransaction): DigitalSignature.WithKey {
            progressTracker.currentStep = SIGNING
            return myKeyPair.signWithECDSA(partialTX.id)
        }

        @Suspendable
        private fun sendSignatures(allPartySignedTx: SignedTransaction, ourSignature: DigitalSignature.WithKey,
                                   notarySignature: DigitalSignature.WithKey): SignedTransaction {
            progressTracker.currentStep = SENDING_SIGS
            val fullySigned = allPartySignedTx + notarySignature

            logger.trace { "Built finished transaction, sending back to other party!" }

            send(otherParty, SignaturesFromPrimary(ourSignature, notarySignature))
            return fullySigned
        }
    }


    /**
     * Abstracted bilateral deal flow participant that is recipient of initial communication.
     *
     * There's a good chance we can push at least some of this logic down into core flow logic
     * and helper methods etc.
     */
    abstract class Secondary<U>(override val progressTracker: ProgressTracker = Secondary.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object RECEIVING : ProgressTracker.Step("Waiting for deal info")
            object VERIFYING : ProgressTracker.Step("Verifying deal info")
            object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")
            object SWAPPING_SIGNATURES : ProgressTracker.Step("Swapping signatures with the other party")
            object RECORDING : ProgressTracker.Step("Recording completed transaction")

            fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING, SWAPPING_SIGNATURES, RECORDING)
        }

        abstract val otherParty: Party

        @Suspendable
        override fun call(): SignedTransaction {
            val handshake = receiveAndValidateHandshake()

            progressTracker.currentStep = SIGNING
            val (ptx, additionalSigningPubKeys) = assembleSharedTX(handshake)
            val stx = signWithOurKeys(additionalSigningPubKeys, ptx)

            val signatures = swapSignaturesWithPrimary(stx)

            logger.trace { "Got signatures from other party, verifying ... " }

            val fullySigned = stx + signatures.sellerSig + signatures.notarySig
            fullySigned.verifySignatures()

            logger.trace { "Signatures received are valid. Deal transaction complete! :-)" }

            progressTracker.currentStep = RECORDING
            serviceHub.recordTransactions(fullySigned)

            logger.trace { "Deal transaction stored" }
            return fullySigned
        }

        @Suspendable
        private fun receiveAndValidateHandshake(): Handshake<U> {
            progressTracker.currentStep = RECEIVING
            // Wait for a trade request to come in on our pre-provided session ID.
            val handshake = receive<Handshake<U>>(otherParty)

            progressTracker.currentStep = VERIFYING
            return handshake.unwrap { validateHandshake(it) }
        }

        @Suspendable
        private fun swapSignaturesWithPrimary(stx: SignedTransaction): SignaturesFromPrimary {
            progressTracker.currentStep = SWAPPING_SIGNATURES
            logger.trace { "Sending partially signed transaction to other party" }

            // TODO: Protect against the seller terminating here and leaving us in the lurch without the final tx.

            return sendAndReceive<SignaturesFromPrimary>(otherParty, stx).unwrap { it }
        }

        private fun signWithOurKeys(signingPubKeys: List<CompositeKey>, ptx: TransactionBuilder): SignedTransaction {
            // Now sign the transaction with whatever keys we need to move the cash.
            for (publicKey in signingPubKeys.keys) {
                val privateKey = serviceHub.keyManagementService.toPrivate(publicKey)
                ptx.signWith(KeyPair(publicKey, privateKey))
            }

            return ptx.toSignedTransaction(checkSufficientSignatures = false)
        }

        @Suspendable protected abstract fun validateHandshake(handshake: Handshake<U>): Handshake<U>
        @Suspendable protected abstract fun assembleSharedTX(handshake: Handshake<U>): Pair<TransactionBuilder, List<CompositeKey>>
    }


    data class AutoOffer(val notary: Party, val dealBeingOffered: DealState)


    /**
     * One side of the flow for inserting a pre-agreed deal.
     */
    open class Instigator(override val otherParty: Party,
                          override val payload: AutoOffer,
                          override val myKeyPair: KeyPair,
                          override val progressTracker: ProgressTracker = Primary.tracker()) : Primary() {

        override val notaryNode: NodeInfo get() =
        serviceHub.networkMapCache.notaryNodes.filter { it.notaryIdentity == payload.notary }.single()
    }

    /**
     * One side of the flow for inserting a pre-agreed deal.
     */
    open class Acceptor(override val otherParty: Party,
                        override val progressTracker: ProgressTracker = Secondary.tracker()) : Secondary<AutoOffer>() {

        override fun validateHandshake(handshake: Handshake<AutoOffer>): Handshake<AutoOffer> {
            // What is the seller trying to sell us?
            val autoOffer = handshake.payload
            val deal = autoOffer.dealBeingOffered
            logger.trace { "Got deal request for: ${deal.ref}" }
            return handshake.copy(payload = autoOffer.copy(dealBeingOffered = deal))
        }

        override fun assembleSharedTX(handshake: Handshake<AutoOffer>): Pair<TransactionBuilder, List<CompositeKey>> {
            val deal = handshake.payload.dealBeingOffered
            val ptx = deal.generateAgreement(handshake.payload.notary)

            // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
            // to have one.
            ptx.setTime(serviceHub.clock.instant(), 30.seconds)
            return Pair(ptx, arrayListOf(deal.parties.single { it.name == serviceHub.myInfo.legalIdentity.name }.owningKey))
        }
    }

}
