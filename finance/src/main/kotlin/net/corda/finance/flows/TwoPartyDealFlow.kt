package net.corda.finance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.excludeNotary
import net.corda.core.identity.groupPublicKeysByWellKnownParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.core.utilities.trace
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.DealState
import java.security.PublicKey

/**
 * Classes for manipulating a two party deal or agreement.
 */
// TODO: The subclasses should probably be broken out into individual flows rather than making this an ever expanding collection of subclasses.
// TODO: Also, the term Deal is used here where we might prefer Agreement.
// TODO: Make this flow more generic.
object TwoPartyDealFlow {
    /**
     * This object is serialised to the network and is the first flow message the seller sends to the buyer.
     *
     * @param primaryIdentity the (anonymised) identity of the participant that initiates communication/handshake.
     * @param secondaryIdentity the (anonymised) identity of the participant that is recipient of initial communication.
     */
    @CordaSerializable
    data class Handshake<out T>(val payload: T, val primaryIdentity: AnonymousParty, val secondaryIdentity: AnonymousParty)

    /**
     * Abstracted bilateral deal flow participant that initiates communication/handshake.
     */
    abstract class Primary(override val progressTracker: ProgressTracker = Primary.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_ID : ProgressTracker.Step("Generating anonymous identities")
            object SENDING_PROPOSAL : ProgressTracker.Step("Handshaking and awaiting transaction proposal.")

            fun tracker() = ProgressTracker(GENERATING_ID, SENDING_PROPOSAL)
        }

        abstract val payload: Any
        abstract val notaryParty: Party
        abstract val otherSideSession: FlowSession

        // DOCSTART 2
        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_ID
            val txIdentities = subFlow(SwapIdentitiesFlow(otherSideSession.counterparty))
            val anonymousMe = txIdentities[ourIdentity] ?: ourIdentity.anonymise()
            val anonymousCounterparty = txIdentities[otherSideSession.counterparty] ?: otherSideSession.counterparty.anonymise()
            // DOCEND 2
            progressTracker.currentStep = SENDING_PROPOSAL
            // Make the first message we'll send to kick off the flow.
            val hello = Handshake(payload, anonymousMe, anonymousCounterparty)
            // Wait for the FinalityFlow to finish on the other side and return the tx when it's available.
            otherSideSession.send(hello)

            val signTransactionFlow = object : SignTransactionFlow(otherSideSession) {
                override fun checkTransaction(stx: SignedTransaction) = checkProposal(stx)
            }

            val txId = subFlow(signTransactionFlow).id

            return waitForLedgerCommit(txId)
        }

        @Suspendable
        abstract fun checkProposal(stx: SignedTransaction)
    }

    /**
     * Abstracted bilateral deal flow participant that is recipient of initial communication.
     */
    abstract class Secondary<U>(override val progressTracker: ProgressTracker = Secondary.tracker(),
                                val regulators: Set<Party> = emptySet()) : FlowLogic<SignedTransaction>() {

        companion object {
            object RECEIVING : ProgressTracker.Step("Waiting for deal info.")
            object VERIFYING : ProgressTracker.Step("Verifying deal info.")
            object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal.")
            object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties.")
            object RECORDING : ProgressTracker.Step("Recording completed transaction.")

            fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING, COLLECTING_SIGNATURES, RECORDING)
        }

        abstract val otherSideSession: FlowSession

        @Suspendable
        override fun call(): SignedTransaction {
            val handshake = receiveAndValidateHandshake()

            progressTracker.currentStep = SIGNING
            val (utx, additionalSigningPubKeys, additionalSignatures) = assembleSharedTX(handshake)
            val ptx = if (additionalSignatures.any()) {
                serviceHub.signInitialTransaction(utx, additionalSigningPubKeys).withAdditionalSignatures(additionalSignatures)
            } else {
                serviceHub.signInitialTransaction(utx, additionalSigningPubKeys)
            }

            logger.trace("Signed proposed transaction.")

            progressTracker.currentStep = COLLECTING_SIGNATURES

            // Get signature of initiating side
            val ptxSignedByOtherSide = ptx + subFlow(CollectSignatureFlow(ptx, otherSideSession, otherSideSession.counterparty.owningKey))

            // DOCSTART 1
            // Get signatures of other signers
            val sessionsForOtherSigners = excludeNotary(groupPublicKeysByWellKnownParty(serviceHub, ptxSignedByOtherSide.getMissingSigners()), ptxSignedByOtherSide).map { initiateFlow(it.key) }
            val stx = subFlow(CollectSignaturesFlow(ptxSignedByOtherSide, sessionsForOtherSigners, additionalSigningPubKeys))
            // DOCEND 1

            logger.trace("Got signatures from other party, verifying ... ")

            progressTracker.currentStep = RECORDING
            val ftx = subFlow(FinalityFlow(stx, regulators + otherSideSession.counterparty))
            logger.trace("Recorded transaction.")

            return ftx
        }

        @Suspendable
        private fun receiveAndValidateHandshake(): Handshake<U> {
            progressTracker.currentStep = RECEIVING
            // Wait for a trade request to come in on our pre-provided session ID.
            val handshake = otherSideSession.receive<Handshake<U>>()

            progressTracker.currentStep = VERIFYING
            return handshake.unwrap {
                // Verify the transaction identities represent the correct parties
                val wellKnownOtherParty = serviceHub.identityService.wellKnownPartyFromAnonymous(it.primaryIdentity)
                val wellKnownMe = serviceHub.identityService.wellKnownPartyFromAnonymous(it.secondaryIdentity)
                require(wellKnownOtherParty == otherSideSession.counterparty)
                require(wellKnownMe == ourIdentity)
                validateHandshake(it)
            }
        }

        @Suspendable
        protected abstract fun validateHandshake(handshake: Handshake<U>): Handshake<U>

        @Suspendable
        protected abstract fun assembleSharedTX(handshake: Handshake<U>): Triple<TransactionBuilder, List<PublicKey>, List<TransactionSignature>>
    }

    @CordaSerializable
    data class AutoOffer(val notary: Party, val dealBeingOffered: DealState)

    /**
     * One side of the flow for inserting a pre-agreed deal.
     */
    open class Instigator(override val otherSideSession: FlowSession,
                          override val payload: AutoOffer,
                          override val progressTracker: ProgressTracker = Primary.tracker()) : Primary() {
        override val notaryParty: Party get() = payload.notary

        @Suspendable override fun checkProposal(stx: SignedTransaction) = requireThat {
            // Add some constraints here.
        }
    }

    /**
     * One side of the flow for inserting a pre-agreed deal.
     */
    open class Acceptor(override val otherSideSession: FlowSession,
                        override val progressTracker: ProgressTracker = Secondary.tracker()) : Secondary<AutoOffer>() {

        override fun validateHandshake(handshake: Handshake<AutoOffer>): Handshake<AutoOffer> {
            // What is the seller trying to sell us?
            val autoOffer = handshake.payload
            val deal = autoOffer.dealBeingOffered
            logger.trace { "Got deal request for: ${deal.linearId.externalId}" }
            return handshake.copy(payload = autoOffer.copy(dealBeingOffered = deal))
        }

        override fun assembleSharedTX(handshake: Handshake<AutoOffer>): Triple<TransactionBuilder, List<PublicKey>, List<TransactionSignature>> {
            val deal = handshake.payload.dealBeingOffered
            val ptx = deal.generateAgreement(handshake.payload.notary)

            // We set the transaction's time-window: it may be that none of the contracts need this!
            // But it can't hurt to have one.
            ptx.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
            return Triple(ptx, arrayListOf(deal.participants.single { it is Party && serviceHub.myInfo.isLegalIdentity(it) }.owningKey), emptyList())
        }
    }
}
