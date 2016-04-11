package protocols

import co.paralleluniverse.fibers.Suspendable
import contracts.DealState
import contracts.FixableDealState
import core.*
import core.crypto.DigitalSignature
import core.crypto.signWithECDSA
import core.messaging.SingleMessageRecipient
import core.node.NodeInfo
import core.protocols.ProtocolLogic
import core.utilities.ProgressTracker
import core.utilities.UntrustworthyData
import core.utilities.trace
import java.math.BigDecimal
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException

/**
 * Classes for manipulating a two party deal or agreement.
 *
 * TODO: The subclasses should probably be broken out into individual protocols rather than making this an ever expanding collection of subclasses.
 *
 * TODO: Also, the term Deal is used here where we might prefer Agreement.
 *
 */
object TwoPartyDealProtocol {
    val DEAL_TOPIC = "platform.deal"

    class DealMismatchException(val expectedDeal: ContractState, val actualDeal: ContractState) : Exception() {
        override fun toString() = "The submitted deal didn't match the expected: $expectedDeal vs $actualDeal"
    }

    class DealRefMismatchException(val expectedDeal: StateRef, val actualDeal: StateRef) : Exception() {
        override fun toString() = "The submitted deal didn't match the expected: $expectedDeal vs $actualDeal"
    }

    // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
    data class Handshake<T>(
            val payload: T,
            val publicKey: PublicKey,
            val sessionID: Long
    )

    class SignaturesFromPrimary(val timestampAuthoritySig: DigitalSignature.WithKey, val sellerSig: DigitalSignature.WithKey)

    /**
     * Abstracted bilateral deal protocol participant that initiates communication/handshake.
     *
     * There's a good chance we can push at least some of this logic down into core protocol logic
     * and helper methods etc.
     */
    abstract class Primary<U>(val payload: U,
                              val otherSide: SingleMessageRecipient,
                              val otherSessionID: Long,
                              val myKeyPair: KeyPair,
                              val timestampingAuthority: NodeInfo,
                              override val progressTracker: ProgressTracker = Primary.tracker()) : ProtocolLogic<SignedTransaction>() {

        companion object {
            object AWAITING_PROPOSAL : ProgressTracker.Step("Awaiting transaction proposal from other")
            object VERIFYING : ProgressTracker.Step("Verifying transaction proposal from other")
            object SIGNING : ProgressTracker.Step("Signing transaction")
            object TIMESTAMPING : ProgressTracker.Step("Timestamping transaction")
            object SENDING_SIGS : ProgressTracker.Step("Sending transaction signatures to other party")
            object RECORDING : ProgressTracker.Step("Recording completed transaction")
            object COPYING_TO_REGULATOR : ProgressTracker.Step("Copying regulator")

            fun tracker() = ProgressTracker(AWAITING_PROPOSAL, VERIFYING, SIGNING, TIMESTAMPING, SENDING_SIGS, RECORDING, COPYING_TO_REGULATOR).apply {
                childrenFor[TIMESTAMPING] = TimestampingProtocol.tracker()
            }
        }

        @Suspendable
        fun getPartialTransaction(): UntrustworthyData<SignedTransaction> {
            progressTracker.currentStep = AWAITING_PROPOSAL

            val sessionID = random63BitValue()

            // Make the first message we'll send to kick off the protocol.
            val hello = Handshake<U>(payload, myKeyPair.public, sessionID)

            val maybeSTX = sendAndReceive<SignedTransaction>(DEAL_TOPIC, otherSide, otherSessionID, sessionID, hello)

            return maybeSTX
        }

        @Suspendable
        fun verifyPartialTransaction(untrustedPartialTX: UntrustworthyData<SignedTransaction>): SignedTransaction {
            progressTracker.currentStep = VERIFYING

            untrustedPartialTX.validate {
                progressTracker.nextStep()

                // Check that the tx proposed by the buyer is valid.
                val missingSigs = it.verify(throwIfSignaturesAreMissing = false)
                if (missingSigs != setOf(myKeyPair.public, timestampingAuthority.identity.owningKey))
                    throw SignatureException("The set of missing signatures is not as expected: $missingSigs")

                val wtx: WireTransaction = it.tx
                logger.trace { "Received partially signed transaction: ${it.id}" }

                checkDependencies(it)

                // This verifies that the transaction is contract-valid, even though it is missing signatures.
                serviceHub.verifyTransaction(wtx.toLedgerTransaction(serviceHub.identityService, serviceHub.storageService.attachments))

                // There are all sorts of funny games a malicious secondary might play here, we should fix them:
                //
                // - This tx may attempt to send some assets we aren't intending to sell to the secondary, if
                //   we're reusing keys! So don't reuse keys!
                // - This tx may include output states that impose odd conditions on the movement of the cash,
                //   once we implement state pairing.
                //
                // but the goal of this code is not to be fully secure (yet), but rather, just to find good ways to
                // express protocol state machines on top of the messaging layer.

                return it
            }
        }

        @Suspendable
        private fun checkDependencies(stx: SignedTransaction) {
            // Download and check all the transactions that this transaction depends on, but do not check this
            // transaction itself.
            val dependencyTxIDs = stx.tx.inputs.map { it.txhash }.toSet()
            subProtocol(ResolveTransactionsProtocol(dependencyTxIDs, otherSide))
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val partialTX: SignedTransaction = verifyPartialTransaction(getPartialTransaction())

            // These two steps could be done in parallel, in theory. Our framework doesn't support that yet though.
            val ourSignature = signWithOurKey(partialTX)
            val tsaSig = timestamp(partialTX)

            val fullySigned = sendSignatures(partialTX, ourSignature, tsaSig)

            progressTracker.currentStep = RECORDING

            serviceHub.recordTransactions(listOf(fullySigned))

            logger.trace { "Deal stored" }

            val regulators = serviceHub.networkMapCache.regulators
            if (regulators.isNotEmpty()) {
                // Copy the transaction to every regulator in the network. This is obviously completely bogus, it's
                // just for demo purposes.
                for (regulator in regulators) {
                    send("regulator.all.seeing.eye", regulator.address, 0, fullySigned)
                }
            }

            return fullySigned
        }

        @Suspendable
        private fun timestamp(partialTX: SignedTransaction): DigitalSignature.LegallyIdentifiable {
            progressTracker.childrenFor[TIMESTAMPING] = TimestampingProtocol.tracker()
            progressTracker.currentStep = TIMESTAMPING
            return subProtocol(TimestampingProtocol(timestampingAuthority, partialTX.txBits, progressTracker.childrenFor[TIMESTAMPING]!!))
        }

        @Suspendable
        open fun signWithOurKey(partialTX: SignedTransaction): DigitalSignature.WithKey {
            progressTracker.currentStep = SIGNING
            return myKeyPair.signWithECDSA(partialTX.txBits)
        }

        @Suspendable
        private fun sendSignatures(partialTX: SignedTransaction, ourSignature: DigitalSignature.WithKey,
                                   tsaSig: DigitalSignature.LegallyIdentifiable): SignedTransaction {
            progressTracker.currentStep = SENDING_SIGS
            val fullySigned = partialTX + tsaSig + ourSignature

            logger.trace { "Built finished transaction, sending back to other party!" }

            send(DEAL_TOPIC, otherSide, otherSessionID, SignaturesFromPrimary(tsaSig, ourSignature))
            return fullySigned
        }
    }


    /**
     * Abstracted bilateral deal protocol participant that is recipient of initial communication.
     *
     * There's a good chance we can push at least some of this logic down into core protocol logic
     * and helper methods etc.
     */
    abstract class Secondary<U>(val otherSide: SingleMessageRecipient,
                                val timestampingAuthority: Party,
                                val sessionID: Long,
                                override val progressTracker: ProgressTracker = Secondary.tracker()) : ProtocolLogic<SignedTransaction>() {

        companion object {
            object RECEIVING : ProgressTracker.Step("Waiting for deal info")
            object VERIFYING : ProgressTracker.Step("Verifying deal info")
            object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")
            object SWAPPING_SIGNATURES : ProgressTracker.Step("Swapping signatures with the other party")
            object RECORDING : ProgressTracker.Step("Recording completed transaction")

            fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING, SWAPPING_SIGNATURES, RECORDING)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val handshake = receiveAndValidateHandshake()

            progressTracker.currentStep = SIGNING
            val (ptx, additionalSigningPubKeys) = assembleSharedTX(handshake)
            val stx = signWithOurKeys(additionalSigningPubKeys, ptx)

            val signatures = swapSignaturesWithPrimary(stx, handshake.sessionID)

            logger.trace { "Got signatures from other party, verifying ... " }
            val fullySigned = stx + signatures.timestampAuthoritySig + signatures.sellerSig
            fullySigned.verify()

            logger.trace { "Signatures received are valid. Deal transaction complete! :-)" }

            progressTracker.currentStep = RECORDING
            serviceHub.recordTransactions(listOf(fullySigned))

            logger.trace { "Deal transaction stored" }
            return fullySigned
        }

        @Suspendable
        private fun receiveAndValidateHandshake(): Handshake<U> {
            progressTracker.currentStep = RECEIVING
            // Wait for a trade request to come in on our pre-provided session ID.
            val handshake = receive<Handshake<U>>(DEAL_TOPIC, sessionID)

            progressTracker.currentStep = VERIFYING
            handshake.validate {
                return validateHandshake(it)
            }
        }

        @Suspendable
        private fun swapSignaturesWithPrimary(stx: SignedTransaction, theirSessionID: Long): SignaturesFromPrimary {
            progressTracker.currentStep = SWAPPING_SIGNATURES
            logger.trace { "Sending partially signed transaction to seller" }

            // TODO: Protect against the seller terminating here and leaving us in the lurch without the final tx.

            return sendAndReceive<SignaturesFromPrimary>(DEAL_TOPIC, otherSide, theirSessionID, sessionID, stx).validate { it }
        }

        private fun signWithOurKeys(signingPubKeys: List<PublicKey>, ptx: TransactionBuilder): SignedTransaction {
            // Now sign the transaction with whatever keys we need to move the cash.
            for (k in signingPubKeys) {
                val priv = serviceHub.keyManagementService.toPrivate(k)
                ptx.signWith(KeyPair(k, priv))
            }

            return ptx.toSignedTransaction(checkSufficientSignatures = false)
        }

        @Suspendable protected abstract fun validateHandshake(handshake: Handshake<U>): Handshake<U>
        @Suspendable protected abstract fun assembleSharedTX(handshake: Handshake<U>): Pair<TransactionBuilder, List<PublicKey>>
    }

    /**
     * One side of the protocol for inserting a pre-agreed deal.
     */
    open class Instigator<T : DealState>(otherSide: SingleMessageRecipient,
                                         timestampingAuthority: NodeInfo,
                                         dealBeingOffered: T,
                                         myKeyPair: KeyPair,
                                         buyerSessionID: Long,
                                         override val progressTracker: ProgressTracker = Primary.tracker()) : Primary<T>(dealBeingOffered, otherSide, buyerSessionID, myKeyPair, timestampingAuthority)

    /**
     * One side of the protocol for inserting a pre-agreed deal.
     */
    open class Acceptor<T : DealState>(otherSide: SingleMessageRecipient,
                                       timestampingAuthority: Party,
                                       val dealToBuy: T,
                                       sessionID: Long,
                                       override val progressTracker: ProgressTracker = Secondary.tracker()) : Secondary<T>(otherSide, timestampingAuthority, sessionID) {

        @Suspendable
        override fun validateHandshake(handshake: Handshake<T>): Handshake<T> {
            with(handshake) {
                // What is the seller trying to sell us?
                val deal: T = handshake.payload
                val otherKey = handshake.publicKey
                logger.trace { "Got deal request for: ${handshake.payload}" }

                // Check the start message for acceptability.
                check(handshake.sessionID > 0)
                if (dealToBuy != deal)
                    throw DealMismatchException(dealToBuy, deal)

                // We need to substitute in the new public keys for the Parties
                val myName = serviceHub.storageService.myLegalIdentity.name
                val myOldParty = deal.parties.single { it.name == myName }
                val theirOldParty = deal.parties.single { it.name != myName }

                @Suppress("UNCHECKED_CAST")
                val newDeal = deal.
                        withPublicKey(myOldParty, serviceHub.keyManagementService.freshKey().public).
                        withPublicKey(theirOldParty, otherKey) as T

                return handshake.copy(payload = newDeal)
            }

        }

        @Suspendable
        override fun assembleSharedTX(handshake: Handshake<T>): Pair<TransactionBuilder, List<PublicKey>> {
            val ptx = handshake.payload.generateAgreement()

            // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
            // to have one.
            ptx.setTime(serviceHub.clock.instant(), timestampingAuthority, 30.seconds)
            return Pair(ptx, arrayListOf(handshake.payload.parties.single { it.name == serviceHub.storageService.myLegalIdentity.name }.owningKey))
        }

    }

    /**
     * One side of the fixing protocol for an interest rate swap, but could easily be generalised further.
     *
     * Do not infer too much from the name of the class.  This is just to indicate that it is the "side"
     * of the protocol that is run by the party with the fixed leg of swap deal, which is the basis for decided
     * who does what in the protocol.
     */
    open class Fixer<T : FixableDealState>(otherSide: SingleMessageRecipient,
                                           timestampingAuthority: Party,
                                           val dealToFix: StateAndRef<T>,
                                           sessionID: Long,
                                           override val progressTracker: ProgressTracker = Secondary.tracker()) : Secondary<StateRef>(otherSide, timestampingAuthority, sessionID) {

        @Suspendable
        override fun validateHandshake(handshake: Handshake<StateRef>): Handshake<StateRef> {
            with(handshake) {
                logger.trace { "Got fixing request for: ${dealToFix.state}" }

                // Check the start message for acceptability.
                if (dealToFix.ref != handshake.payload)
                    throw DealRefMismatchException(dealToFix.ref, handshake.payload)

                // Check the transaction that contains the state which is being resolved.
                // We only have a hash here, so if we don't know it already, we have to ask for it.
                //subProtocol(ResolveTransactionsProtocol(setOf(handshake.payload.txhash), otherSide))

                return handshake
            }
        }

        @Suspendable
        override fun assembleSharedTX(handshake: Handshake<StateRef>): Pair<TransactionBuilder, List<PublicKey>> {
            val fixOf = dealToFix.state.nextFixingOf()!!

            // TODO Do we need/want to substitute in new public keys for the Parties?
            val myName = serviceHub.storageService.myLegalIdentity.name
            val deal: T = dealToFix.state
            val myOldParty = deal.parties.single { it.name == myName }
            val theirOldParty = deal.parties.single { it.name != myName }
            val myNewKey = serviceHub.keyManagementService.freshKey().public

            @Suppress("UNCHECKED_CAST")
            val newDeal = deal.withPublicKey(myOldParty, myNewKey).withPublicKey(theirOldParty, handshake.publicKey) as T
            val oldRef = dealToFix.ref

            val ptx = TransactionBuilder()
            val addFixing = object : RatesFixProtocol(ptx, serviceHub.networkMapCache.ratesOracleNodes[0], fixOf, BigDecimal.ZERO, BigDecimal.ONE) {

                @Suspendable
                override fun beforeSigning(fix: Fix) {
                    newDeal.generateFix(ptx, oldRef, fix)

                    // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
                    // to have one.
                    ptx.setTime(serviceHub.clock.instant(), timestampingAuthority, 30.seconds)
                }

            }
            subProtocol(addFixing)

            return Pair(ptx, arrayListOf(myNewKey))
        }
    }

    /**
     * One side of the fixing protocol for an interest rate swap, but could easily be generalised furher
     *
     * As per the [Fixer], do not infer too much from this class name in terms of business roles.  This
     * is just the "side" of the protocol run by the party with the floating leg as a way of deciding who
     * does what in the protocol.
     */
    open class Floater<T : FixableDealState>(otherSide: SingleMessageRecipient,
                                             otherSessionID: Long,
                                             timestampingAuthority: NodeInfo,
                                             dealToFix: StateAndRef<T>,
                                             myKeyPair: KeyPair,
                                             val sessionID: Long,
                                             override val progressTracker: ProgressTracker = Primary.tracker()) : Primary<StateRef>(dealToFix.ref, otherSide, otherSessionID, myKeyPair, timestampingAuthority)
}