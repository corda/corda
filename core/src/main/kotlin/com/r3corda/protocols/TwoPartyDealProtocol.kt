package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.TransientProperty
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.seconds
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
import com.r3corda.core.utilities.trace
import java.math.BigDecimal
import java.security.KeyPair
import java.security.PublicKey
import java.time.Duration

/**
 * Classes for manipulating a two party deal or agreement.
 *
 * TODO: The subclasses should probably be broken out into individual protocols rather than making this an ever expanding collection of subclasses.
 *
 * TODO: Also, the term Deal is used here where we might prefer Agreement.
 *
 * TODO: Consider whether we can merge this with [TwoPartyTradeProtocol]
 *
 */
object TwoPartyDealProtocol {

    val DEAL_TOPIC = "platform.deal"
    /** This topic exists purely for [FixingSessionInitiation] to be sent from [FixingRoleDecider] to [FixingSessionInitiationHandler] */
    val FIX_INITIATE_TOPIC = "platform.fix.initiate"

    class DealMismatchException(val expectedDeal: ContractState, val actualDeal: ContractState) : Exception() {
        override fun toString() = "The submitted deal didn't match the expected: $expectedDeal vs $actualDeal"
    }

    class DealRefMismatchException(val expectedDeal: StateRef, val actualDeal: StateRef) : Exception() {
        override fun toString() = "The submitted deal didn't match the expected: $expectedDeal vs $actualDeal"
    }

    // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
    data class Handshake<out T>(
            val payload: T,
            val publicKey: PublicKey,
            val sessionID: Long
    )

    class SignaturesFromPrimary(val sellerSig: DigitalSignature.WithKey, val notarySig: DigitalSignature.LegallyIdentifiable)

    /**
     * Abstracted bilateral deal protocol participant that initiates communication/handshake.
     *
     * There's a good chance we can push at least some of this logic down into core protocol logic
     * and helper methods etc.
     */
    abstract class Primary<out U>(override val progressTracker: ProgressTracker = Primary.tracker()) : ProtocolLogic<SignedTransaction>() {

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

        override val topic: String get() = DEAL_TOPIC

        abstract val payload: U
        abstract val notaryNode: NodeInfo
        abstract val otherSide: Party
        abstract val otherSessionID: Long
        abstract val myKeyPair: KeyPair

        @Suspendable
        fun getPartialTransaction(): UntrustworthyData<SignedTransaction> {
            progressTracker.currentStep = AWAITING_PROPOSAL

            val sessionID = random63BitValue()

            // Make the first message we'll send to kick off the protocol.
            val hello = Handshake(payload, myKeyPair.public, sessionID)
            val maybeSTX = sendAndReceive<SignedTransaction>(otherSide, otherSessionID, sessionID, hello)

            return maybeSTX
        }

        @Suspendable
        fun verifyPartialTransaction(untrustedPartialTX: UntrustworthyData<SignedTransaction>): SignedTransaction {
            progressTracker.currentStep = VERIFYING

            untrustedPartialTX.validate { stx ->
                progressTracker.nextStep()

                // Check that the tx proposed by the buyer is valid.
                val wtx: WireTransaction = stx.verifySignatures(myKeyPair.public, notaryNode.identity.owningKey)
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
                // express protocol state machines on top of the messaging layer.

                return stx
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
            val stx: SignedTransaction = verifyPartialTransaction(getPartialTransaction())

            // These two steps could be done in parallel, in theory. Our framework doesn't support that yet though.
            val ourSignature = signWithOurKey(stx)
            val notarySignature = getNotarySignature(stx)

            val fullySigned = sendSignatures(stx, ourSignature, notarySignature)

            progressTracker.currentStep = RECORDING

            serviceHub.recordTransactions(listOf(fullySigned))

            logger.trace { "Deal stored" }

            progressTracker.currentStep = COPYING_TO_REGULATOR
            val regulators = serviceHub.networkMapCache.regulators
            if (regulators.isNotEmpty()) {
                // Copy the transaction to every regulator in the network. This is obviously completely bogus, it's
                // just for demo purposes.
                regulators.forEach { send(it.identity, DEFAULT_SESSION_ID, fullySigned) }
            }

            return fullySigned
        }

        @Suspendable
        private fun getNotarySignature(stx: SignedTransaction): DigitalSignature.LegallyIdentifiable {
            progressTracker.currentStep = NOTARY
            return subProtocol(NotaryProtocol.Client(stx))
        }

        open fun signWithOurKey(partialTX: SignedTransaction): DigitalSignature.WithKey {
            progressTracker.currentStep = SIGNING
            return myKeyPair.signWithECDSA(partialTX.txBits)
        }

        @Suspendable
        private fun sendSignatures(partialTX: SignedTransaction, ourSignature: DigitalSignature.WithKey,
                                   notarySignature: DigitalSignature.LegallyIdentifiable): SignedTransaction {
            progressTracker.currentStep = SENDING_SIGS
            val fullySigned = partialTX + ourSignature + notarySignature

            logger.trace { "Built finished transaction, sending back to other party!" }

            send(otherSide, otherSessionID, SignaturesFromPrimary(ourSignature, notarySignature))
            return fullySigned
        }
    }


    /**
     * Abstracted bilateral deal protocol participant that is recipient of initial communication.
     *
     * There's a good chance we can push at least some of this logic down into core protocol logic
     * and helper methods etc.
     */
    abstract class Secondary<U>(override val progressTracker: ProgressTracker = Secondary.tracker()) : ProtocolLogic<SignedTransaction>() {

        companion object {
            object RECEIVING : ProgressTracker.Step("Waiting for deal info")
            object VERIFYING : ProgressTracker.Step("Verifying deal info")
            object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")
            object SWAPPING_SIGNATURES : ProgressTracker.Step("Swapping signatures with the other party")
            object RECORDING : ProgressTracker.Step("Recording completed transaction")

            fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING, SWAPPING_SIGNATURES, RECORDING)
        }

        override val topic: String get() = DEAL_TOPIC

        abstract val otherSide: Party
        abstract val sessionID: Long

        @Suspendable
        override fun call(): SignedTransaction {
            val handshake = receiveAndValidateHandshake()

            progressTracker.currentStep = SIGNING
            val (ptx, additionalSigningPubKeys) = assembleSharedTX(handshake)
            val stx = signWithOurKeys(additionalSigningPubKeys, ptx)

            val signatures = swapSignaturesWithPrimary(stx, handshake.sessionID)

            logger.trace { "Got signatures from other party, verifying ... " }

            val fullySigned = stx + signatures.sellerSig + signatures.notarySig
            fullySigned.verifySignatures()

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
            val handshake = receive<Handshake<U>>(sessionID)

            progressTracker.currentStep = VERIFYING
            handshake.validate {
                return validateHandshake(it)
            }
        }

        @Suspendable
        private fun swapSignaturesWithPrimary(stx: SignedTransaction, theirSessionID: Long): SignaturesFromPrimary {
            progressTracker.currentStep = SWAPPING_SIGNATURES
            logger.trace { "Sending partially signed transaction to other party" }

            // TODO: Protect against the seller terminating here and leaving us in the lurch without the final tx.

            return sendAndReceive<SignaturesFromPrimary>(otherSide, theirSessionID, sessionID, stx).validate { it }
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
    open class Instigator<out T : DealState>(override val otherSide: Party,
                                             val notary: Party,
                                             override val payload: T,
                                             override val myKeyPair: KeyPair,
                                             override val otherSessionID: Long,
                                             override val progressTracker: ProgressTracker = Primary.tracker()) : Primary<T>() {

        override val notaryNode: NodeInfo get() =
            serviceHub.networkMapCache.notaryNodes.filter { it.identity == notary }.single()
    }

    /**
     * One side of the protocol for inserting a pre-agreed deal.
     */
    open class Acceptor<T : DealState>(override val otherSide: Party,
                                       val notary: Party,
                                       val dealToBuy: T,
                                       override val sessionID: Long,
                                       override val progressTracker: ProgressTracker = Secondary.tracker()) : Secondary<T>() {

        override fun validateHandshake(handshake: Handshake<T>): Handshake<T> {
            // What is the seller trying to sell us?
            val deal: T = handshake.payload
            val otherKey = handshake.publicKey
            logger.trace { "Got deal request for: ${handshake.payload.ref}" }

            // Check the start message for acceptability.
            check(handshake.sessionID > 0)
            check(dealToBuy == deal)

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

        override fun assembleSharedTX(handshake: Handshake<T>): Pair<TransactionBuilder, List<PublicKey>> {
            val ptx = handshake.payload.generateAgreement(notary)

            // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
            // to have one.
            ptx.setTime(serviceHub.clock.instant(), 30.seconds)
            return Pair(ptx, arrayListOf(handshake.payload.parties.single { it.name == serviceHub.storageService.myLegalIdentity.name }.owningKey))
        }

    }

    /**
     * One side of the fixing protocol for an interest rate swap, but could easily be generalised further.
     *
     * Do not infer too much from the name of the class.  This is just to indicate that it is the "side"
     * of the protocol that is run by the party with the fixed leg of swap deal, which is the basis for deciding
     * who does what in the protocol.
     */
    class Fixer(val initiation: FixingSessionInitiation, override val progressTracker: ProgressTracker = Secondary.tracker()) : Secondary<StateRef>() {

        override val sessionID: Long get() = initiation.sessionID

        override val otherSide: Party get() = initiation.sender

        private lateinit var txState: TransactionState<*>
        private lateinit var deal: FixableDealState

        override fun validateHandshake(handshake: Handshake<StateRef>): Handshake<StateRef> {
            logger.trace { "Got fixing request for: ${handshake.payload}" }

            // Check the handshake and initiation for acceptability.
            check(handshake.sessionID > 0)
            txState = serviceHub.loadState(handshake.payload)
            deal = txState.data as FixableDealState

            // validate the party that initiated is the one on the deal and that the recipient corresponds with it.
            // TODO: this is in no way secure and will be replaced by general session initiation logic in the future
            val myName = serviceHub.storageService.myLegalIdentity.name
            val otherParty = deal.parties.filter { it.name != myName }.single()
            check(otherParty == initiation.party)
            // Also check we are one of the parties
            deal.parties.filter { it.name == myName }.single()

            return handshake
        }

        @Suspendable
        override fun assembleSharedTX(handshake: Handshake<StateRef>): Pair<TransactionBuilder, List<PublicKey>> {
            @Suppress("UNCHECKED_CAST")
            val fixOf = deal.nextFixingOf()!!

            // TODO Do we need/want to substitute in new public keys for the Parties?
            val myName = serviceHub.storageService.myLegalIdentity.name
            val myOldParty = deal.parties.single { it.name == myName }

            val newDeal = deal

            val ptx = TransactionType.General.Builder(txState.notary)
            val addFixing = object : RatesFixProtocol(ptx, serviceHub.networkMapCache.ratesOracleNodes[0].identity, fixOf, BigDecimal.ZERO, BigDecimal.ONE) {
                @Suspendable
                override fun beforeSigning(fix: Fix) {
                    newDeal.generateFix(ptx, StateAndRef(txState, handshake.payload), fix)

                    // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
                    // to have one.
                    ptx.setTime(serviceHub.clock.instant(), 30.seconds)
                }
            }
            subProtocol(addFixing)
            return Pair(ptx, arrayListOf(myOldParty.owningKey))
        }
    }

    /**
     * One side of the fixing protocol for an interest rate swap, but could easily be generalised furher.
     *
     * As per the [Fixer], do not infer too much from this class name in terms of business roles.  This
     * is just the "side" of the protocol run by the party with the floating leg as a way of deciding who
     * does what in the protocol.
     */
    class Floater(override val payload: StateRef,
                  override val otherSessionID: Long,
                  override val progressTracker: ProgressTracker = Primary.tracker()) : Primary<StateRef>() {
        @Suppress("UNCHECKED_CAST")
        internal val dealToFix: StateAndRef<FixableDealState> by TransientProperty {
            val state = serviceHub.loadState(payload) as TransactionState<FixableDealState>
            StateAndRef(state, payload)
        }

        override val myKeyPair: KeyPair get() {
            val myName = serviceHub.storageService.myLegalIdentity.name
            val publicKey = dealToFix.state.data.parties.filter { it.name == myName }.single().owningKey
            return serviceHub.keyManagementService.toKeyPair(publicKey)
        }

        override val otherSide: Party get() {
            // TODO: what happens if there's no node?  Move to messaging taking Party and then handled in messaging layer
            val myName = serviceHub.storageService.myLegalIdentity.name
            return dealToFix.state.data.parties.filter { it.name != myName }.single()
        }

        override val notaryNode: NodeInfo get() =
            serviceHub.networkMapCache.notaryNodes.filter { it.identity == dealToFix.state.notary }.single()
    }


    /** Used to set up the session between [Floater] and [Fixer] */
    data class FixingSessionInitiation(val sessionID: Long, val party: Party, val sender: Party, val timeout: Duration)

    /**
     * This protocol looks at the deal and decides whether to be the Fixer or Floater role in agreeing a fixing.
     *
     * It is kicked off as an activity on both participant nodes by the scheduler when it's time for a fixing.  If the
     * Fixer role is chosen, then that will be initiated by the [FixingSessionInitiation] message sent from the other party and
     * handled by the [FixingSessionInitiationHandler].
     *
     * TODO: Replace [FixingSessionInitiation] and [FixingSessionInitiationHandler] with generic session initiation logic once it exists.
     */
    class FixingRoleDecider(val ref: StateRef,
                            val timeout: Duration,
                            override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<Unit>() {

        companion object {
            class LOADING() : ProgressTracker.Step("Loading state to decide fixing role")

            fun tracker() = ProgressTracker(LOADING())
        }

        override val topic: String get() = FIX_INITIATE_TOPIC

        @Suspendable
        override fun call(): Unit {
            progressTracker.nextStep()
            val dealToFix = serviceHub.loadState(ref)
            // TODO: this is not the eventual mechanism for identifying the parties
            val sortedParties = (dealToFix.data as FixableDealState).parties.sortedBy { it.name }
            if (sortedParties[0].name == serviceHub.storageService.myLegalIdentity.name) {
                // Generate sessionID
                val sessionID = random63BitValue()
                val initation = FixingSessionInitiation(sessionID, sortedParties[0], serviceHub.storageService.myLegalIdentity, timeout)

                // Send initiation to other side to launch one side of the fixing protocol (the Fixer).
                send(sortedParties[1], DEFAULT_SESSION_ID, initation)

                // Then start the other side of the fixing protocol.
                val protocol = Floater(ref, sessionID)
                subProtocol(protocol)
            }
        }
    }

}