package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.DealState
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.utilities.seconds
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.trace
import net.corda.core.utilities.unwrap
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
     * @param primaryIdentity the (anonymised) identity of the primary/initiating party.
     * @param secondaryIdentity the (anonymised) identity of the secondary/receiving party.
     */
    @CordaSerializable
    data class Handshake<out T>(val payload: T, val primaryIdentity: AnonymousPartyAndPath, val secondaryIdentity: AnonymousPartyAndPath)

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
        abstract val notaryNode: NodeInfo
        abstract val otherParty: Party

        @Suspendable override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_ID
            val txIdentities = subFlow(TransactionKeyFlow(otherParty))
            val anonymousMe = txIdentities.get(serviceHub.myInfo.legalIdentity) ?: serviceHub.myInfo.legalIdentityAndCert.toAnonymous()
            val anonymousCounterparty = txIdentities.get(otherParty) ?: serviceHub.identityService.certificateFromParty(otherParty)!!.toAnonymous()
            progressTracker.currentStep = SENDING_PROPOSAL
            // Make the first message we'll send to kick off the flow.
            val hello = Handshake(payload, anonymousMe, anonymousCounterparty)
            // Wait for the FinalityFlow to finish on the other side and return the tx when it's available.
            send(otherParty, hello)

            val signTransactionFlow = object : SignTransactionFlow(otherParty) {
                override fun checkTransaction(stx: SignedTransaction) = checkProposal(stx)
            }

            subFlow(signTransactionFlow)

            val txHash = receive<SecureHash>(otherParty).unwrap { it }

            return waitForLedgerCommit(txHash)
        }

        @Suspendable abstract fun checkProposal(stx: SignedTransaction)
    }

    /**
     * Abstracted bilateral deal flow participant that is recipient of initial communication.
     */
    abstract class Secondary<U>(override val progressTracker: ProgressTracker = Secondary.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object RECEIVING : ProgressTracker.Step("Waiting for deal info.")
            object VERIFYING : ProgressTracker.Step("Verifying deal info.")
            object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal.")
            object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties.")
            object RECORDING : ProgressTracker.Step("Recording completed transaction.")
            object COPYING_TO_REGULATOR : ProgressTracker.Step("Copying regulator.")
            object COPYING_TO_COUNTERPARTY : ProgressTracker.Step("Copying counterparty.")

            fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING, COLLECTING_SIGNATURES, RECORDING, COPYING_TO_REGULATOR, COPYING_TO_COUNTERPARTY)
        }

        abstract val otherParty: Party

        @Suspendable
        override fun call(): SignedTransaction {
            val handshake = receiveAndValidateHandshake()

            progressTracker.currentStep = SIGNING
            val (utx, additionalSigningPubKeys) = assembleSharedTX(handshake)
            val ptx = signWithOurKeys(additionalSigningPubKeys, utx)

            logger.trace { "Signed proposed transaction." }

            progressTracker.currentStep = COLLECTING_SIGNATURES

            // DOCSTART 1
            // TODO: Use actual anonymised identities, not fake ones derived from the well known identity
            val myAnonymousPartyAndPath = handshake.secondaryIdentity
            val theirAnonymousPartyAndPath = handshake.primaryIdentity
            val identities: Map<Party, AnonymousPartyAndPath> = listOf(Pair(serviceHub.myInfo.legalIdentity, myAnonymousPartyAndPath), Pair(otherParty, theirAnonymousPartyAndPath)).toMap()
            val stx = subFlow(CollectSignaturesFlow(ptx, identities, additionalSigningPubKeys))
            // DOCEND 1

            logger.trace { "Got signatures from other party, verifying ... " }

            progressTracker.currentStep = RECORDING
            val ftx = subFlow(FinalityFlow(stx, setOf(otherParty, serviceHub.myInfo.legalIdentity))).single()

            logger.trace { "Recorded transaction." }

            progressTracker.currentStep = COPYING_TO_REGULATOR
            val regulators = serviceHub.networkMapCache.regulatorNodes
            if (regulators.isNotEmpty()) {
                // Copy the transaction to every regulator in the network. This is obviously completely bogus, it's
                // just for demo purposes.
                regulators.forEach { send(it.serviceIdentities(ServiceType.regulator).first(), ftx) }
            }

            progressTracker.currentStep = COPYING_TO_COUNTERPARTY
            // Send the final transaction hash back to the other party.
            // We need this so we don't break the IRS demo and the SIMM Demo.
            send(otherParty, ftx.id)

            return ftx
        }

        @Suspendable
        private fun receiveAndValidateHandshake(): Handshake<U> {
            progressTracker.currentStep = RECEIVING
            // Wait for a trade request to come in on our pre-provided session ID.
            val handshake = receive<Handshake<U>>(otherParty)

            progressTracker.currentStep = VERIFYING
            val otherPartyAndCert = serviceHub.identityService.certificateFromParty(otherParty) ?: throw IllegalArgumentException("Missing X.509 certificate for well known identity ${otherParty}")
            return handshake.unwrap {
                // Verify the contained identities by passing them to the identity service to store
                serviceHub.identityService.verifyAnonymousIdentity(it.primaryIdentity, otherPartyAndCert)
                serviceHub.identityService.verifyAnonymousIdentity(it.secondaryIdentity, serviceHub.myInfo.legalIdentityAndCert)
                validateHandshake(it)
            }
        }

        private fun signWithOurKeys(signingPubKeys: List<PublicKey>, ptx: TransactionBuilder): SignedTransaction {
            // Now sign the transaction with whatever keys we need to move the cash.
            return serviceHub.signInitialTransaction(ptx, signingPubKeys)
        }

        @Suspendable protected abstract fun validateHandshake(handshake: Handshake<U>): Handshake<U>
        @Suspendable protected abstract fun assembleSharedTX(handshake: Handshake<U>): Pair<TransactionBuilder, List<PublicKey>>
    }

    @CordaSerializable
    data class AutoOffer(val notary: Party, val dealBeingOffered: DealState)

    /**
     * One side of the flow for inserting a pre-agreed deal.
     */
    open class Instigator(override val otherParty: Party,
                          override val payload: AutoOffer,
                          override val progressTracker: ProgressTracker = Primary.tracker()) : Primary() {

        override val notaryNode: NodeInfo get() =
        serviceHub.networkMapCache.notaryNodes.filter { it.notaryIdentity == payload.notary }.single()

        @Suspendable override fun checkProposal(stx: SignedTransaction) = requireThat {
            // Add some constraints here.
        }
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

        override fun assembleSharedTX(handshake: Handshake<AutoOffer>): Pair<TransactionBuilder, List<PublicKey>> {
            val deal = handshake.payload.dealBeingOffered
            val ptx = deal.generateAgreement(handshake.payload.notary)

            // We set the transaction's time-window: it may be that none of the contracts need this!
            // But it can't hurt to have one.
            ptx.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
            return Pair(ptx, arrayListOf(deal.participants.single { it == serviceHub.myInfo.legalIdentity as AbstractParty }.owningKey))
        }
    }
}
