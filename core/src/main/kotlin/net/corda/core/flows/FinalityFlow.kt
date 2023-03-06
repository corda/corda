package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaException
import net.corda.core.CordaInternal
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.NotarySigCheck.needsNotarySignature
import net.corda.core.identity.Party
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.pushToLoggingContext
import net.corda.core.internal.telemetry.telemetryServiceInternal
import net.corda.core.internal.warnOnce
import net.corda.core.node.StatesToRecord
import net.corda.core.node.StatesToRecord.ONLY_RELEVANT
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.debug
import net.corda.core.utilities.unwrap
import java.sql.SQLException
import java.time.Duration

/**
 * Verifies the given transaction, then sends it to the named notary. If the notary agrees that the transaction
 * is acceptable then it is from that point onwards committed to the ledger, and will be written through to the
 * vault. Additionally it will be distributed to the parties reflected in the participants list of the states.
 *
 * By default, the initiating flow will commit states that are relevant to the initiating party as indicated by
 * [StatesToRecord.ONLY_RELEVANT]. Relevance is determined by the union of all participants to states which have been
 * included in the transaction. This default behaviour may be modified by passing in an alternate value for [StatesToRecord].
 *
 * The transaction is expected to have already been resolved: if its dependencies are not available in local
 * storage, verification will fail. It must have signatures from all necessary parties other than the notary.
 *
 * A list of [FlowSession]s is required for each non-local participant of the transaction. These participants will receive
 * the final notarised transaction by calling [ReceiveFinalityFlow] in their counterpart flows. Sessions with non-participants
 * can also be included, but they must specify [StatesToRecord.ALL_VISIBLE] for statesToRecord if they wish to record the
 * contract states into their vaults.
 *
 * The flow returns the same transaction but with the additional signatures from the notary.
 *
 * NOTE: This is an inlined flow but for backwards compatibility is annotated with [InitiatingFlow].
 */
// To maintain backwards compatibility with the old API, FinalityFlow can act both as an initiating flow and as an inlined flow.
// This is only possible because a flow is only truly initiating when the first call to initiateFlow is made (where the
// presence of @InitiatingFlow is checked). So the new API is inlined simply because that code path doesn't call initiateFlow.
@InitiatingFlow
class FinalityFlow private constructor(val transaction: SignedTransaction,
                                       private val oldParticipants: Collection<Party>,
                                       override val progressTracker: ProgressTracker,
                                       private val sessions: Collection<FlowSession>,
                                       private val newApi: Boolean,
                                       private val statesToRecord: StatesToRecord = ONLY_RELEVANT) : FlowLogic<SignedTransaction>() {

    @CordaInternal
    data class ExtraConstructorArgs(val oldParticipants: Collection<Party>, val sessions: Collection<FlowSession>, val newApi: Boolean, val statesToRecord: StatesToRecord)

    @CordaInternal
    fun getExtraConstructorArgs() = ExtraConstructorArgs(oldParticipants, sessions, newApi, statesToRecord)

    @Deprecated(DEPRECATION_MSG)
    constructor(transaction: SignedTransaction, extraRecipients: Set<Party>, progressTracker: ProgressTracker) : this(
            transaction, extraRecipients, progressTracker, emptyList(), false
    )
    @Deprecated(DEPRECATION_MSG)
    constructor(transaction: SignedTransaction, extraRecipients: Set<Party>) : this(transaction, extraRecipients, tracker(), emptyList(), false)
    @Deprecated(DEPRECATION_MSG)
    constructor(transaction: SignedTransaction) : this(transaction, emptySet(), tracker(), emptyList(), false)
    @Deprecated(DEPRECATION_MSG)
    constructor(transaction: SignedTransaction, progressTracker: ProgressTracker) : this(transaction, emptySet(), progressTracker, emptyList(), false)

    /**
     * Notarise the given transaction and broadcast it to the given [FlowSession]s. This list **must** at least include
     * all the non-local participants of the transaction. Sessions to non-participants can also be provided.
     *
     * @param transaction What to commit.
     */
    constructor(transaction: SignedTransaction, firstSession: FlowSession, vararg restSessions: FlowSession) : this(
            transaction, listOf(firstSession) + restSessions.asList()
    )

    /**
     * Notarise the given transaction and broadcast it to all the participants.
     *
     * @param transaction What to commit.
     * @param sessions A collection of [FlowSession]s for each non-local participant of the transaction. Sessions to non-participants can
     * also be provided.
     */
    @JvmOverloads
    constructor(
            transaction: SignedTransaction,
            sessions: Collection<FlowSession>,
            progressTracker: ProgressTracker = tracker()
    ) : this(transaction, emptyList(), progressTracker, sessions, true)

    /**
     * Notarise the given transaction and broadcast it to all the participants.
     *
     * @param transaction What to commit.
     * @param sessions A collection of [FlowSession]s for each non-local participant of the transaction. Sessions to non-participants can
     * also be provided.
     * @param statesToRecord Which states to commit to the vault.
     */
    @JvmOverloads
    constructor(
            transaction: SignedTransaction,
            sessions: Collection<FlowSession>,
            statesToRecord: StatesToRecord,
            progressTracker: ProgressTracker = tracker()
    ) : this(transaction, emptyList(), progressTracker, sessions, true, statesToRecord)

    /**
     * Notarise the given transaction and broadcast it to all the participants.
     *
     * @param transaction What to commit.
     * @param sessions A collection of [FlowSession]s for each non-local participant.
     * @param oldParticipants An **optional** collection of parties for participants who are still using the old API.
     *
     * You will only need to use this parameter if you have upgraded your CorDapp from the V3 FinalityFlow API but are required to provide
     * backwards compatibility with participants running V3 nodes. If you're writing a new CorDapp then this does not apply and this
     * parameter should be ignored.
     */
    @Deprecated(DEPRECATION_MSG)
    constructor(
            transaction: SignedTransaction,
            sessions: Collection<FlowSession>,
            oldParticipants: Collection<Party>,
            progressTracker: ProgressTracker
    ) : this(transaction, oldParticipants, progressTracker, sessions, true)

    companion object {
        private const val DEPRECATION_MSG = "It is unsafe to use this constructor as it requires nodes to automatically " +
                "accept notarised transactions without first checking their relevancy. Instead, use one of the constructors " +
                "that requires only FlowSessions."

        object NOTARISING : ProgressTracker.Step("Requesting signature by notary service") {
            override fun childProgressTracker() = NotaryFlow.Client.tracker()
        }

        @Suppress("ClassNaming")
        object RECORD_UNNOTARISED : ProgressTracker.Step("Recording un-notarised transaction locally")
        @Suppress("ClassNaming")
        object BROADCASTING_PRE_NOTARISATION : ProgressTracker.Step("Broadcasting un-notarised transaction to participants")
        @Suppress("ClassNaming")
        object BROADCASTING_POST_NOTARISATION : ProgressTracker.Step("Broadcasting notary signatures to participants")
        @Suppress("ClassNaming")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Recording finalised transaction locally with notary signatures")
        object BROADCASTING : ProgressTracker.Step("Broadcasting transaction to other participants")

        @JvmStatic
        fun tracker() = ProgressTracker(RECORD_UNNOTARISED, BROADCASTING_PRE_NOTARISATION, NOTARISING, BROADCASTING_POST_NOTARISATION, FINALISING_TRANSACTION, BROADCASTING)
    }

    @Suspendable
    @Throws(NotaryException::class)
    override fun call(): SignedTransaction {
        if (!newApi) {
            logger.warnOnce("The current usage of FinalityFlow is unsafe. Please consider upgrading your CorDapp to use " +
                    "FinalityFlow with FlowSessions. (${serviceHub.getAppContext().cordapp.info})")
        } else {
            require(sessions.none { serviceHub.myInfo.isLegalIdentity(it.counterparty) }) {
                "Do not provide flow sessions for the local node. FinalityFlow will record the notarised transaction locally."
            }
        }

        // Note: this method is carefully broken up to minimize the amount of data reachable from the stack at
        // the point where subFlow is invoked, as that minimizes the checkpointing work to be done.
        //
        // Lookup the resolved transactions and use them to map each signed transaction to the list of participants.

        transaction.pushToLoggingContext()
        logCommandData()
        val ledgerTransaction = verifyTx()
        val externalTxParticipants = extractExternalParticipants(ledgerTransaction)

        if (newApi) {
            val sessionParties = sessions.map { it.counterparty }
            val missingRecipients = externalTxParticipants - sessionParties - oldParticipants
            require(missingRecipients.isEmpty()) {
                "Flow sessions were not provided for the following transaction participants: $missingRecipients"
            }
            sessionParties.intersect(oldParticipants).let {
                require(it.isEmpty()) { "The following parties are specified both in flow sessions and in the oldParticipants list: $it" }
            }
        }

        // Recoverability
        // As of platform version 13 we introduce a 2-phase finality protocol whereby
        // - record un-notarised transaction locally and broadcast to external participants to record
        // - notarise transaction + finalise locally
        // - broadcast notary signatures to external participants (finalise remotely)

        val (oldPlatformSessions, newPlatformSessions) = sessions.partition {
            serviceHub.networkMapCache.getNodeByLegalName(it.counterparty.name)?.platformVersion!! < PlatformVersionSwitches.TWO_PHASE_FINALITY
        }

        val useTwoPhaseFinality = serviceHub.myInfo.platformVersion >= PlatformVersionSwitches.TWO_PHASE_FINALITY
        if (useTwoPhaseFinality && needsNotarySignature(transaction)) {
            recordLocallyAndBroadcast(newPlatformSessions, transaction)
        }

        val notarised = notariseOrRecord()
        val notarySignatures = notarised.sigs - transaction.sigs.toSet()
        if (notarySignatures.isNotEmpty()) {
            if (useTwoPhaseFinality && newPlatformSessions.isNotEmpty()) {
                broadcastSignaturesAndFinalise(newPlatformSessions, notarySignatures)
            }
            else {
                progressTracker.currentStep = FINALISING_TRANSACTION
                serviceHub.finalizeTransactionWithExtraSignatures(transaction + notarySignatures, notarySignatures, statesToRecord)
                logger.info("Finalised transaction locally.")
            }
        }

        if (externalTxParticipants.isNotEmpty() || oldPlatformSessions.isNotEmpty() || newPlatformSessions.isNotEmpty()) {
            progressTracker.currentStep = BROADCASTING
            if (!useTwoPhaseFinality || !needsNotarySignature(transaction)) {
                broadcastToOtherParticipants(externalTxParticipants, newPlatformSessions + oldPlatformSessions, notarised)
            } else {
                broadcastToOtherParticipants(externalTxParticipants, oldPlatformSessions, notarised)
            }
            logger.info("All parties received the transaction successfully.")
        }

        return notarised
    }

    @Suspendable
    private fun recordLocallyAndBroadcast(sessions: Collection<FlowSession>, tx: SignedTransaction) {
        recordUnnotarisedTransaction(tx)
        logger.info("Broadcasting un-notarised transaction.")
        if (sessions.isEmpty()) sleep(Duration.ZERO) else {
            progressTracker.currentStep = BROADCASTING_PRE_NOTARISATION
            sessions.forEach { session ->
                try {
                    logger.debug { "Sending transaction to party $session." }
                    subFlow(SendTransactionFlow(session, tx))
                } catch (e: UnexpectedFlowEndException) {
                    throw UnexpectedFlowEndException(
                            "${session.counterparty} has finished prematurely and we're trying to send them a transaction without notary signature(s). " +
                                    "Did they forget to call ReceiveFinalityFlow? (${e.message})",
                            e.cause,
                            e.originalErrorId
                    )
                }
            }
        }
    }

    @Suspendable
    private fun broadcastSignaturesAndFinalise(sessions: Collection<FlowSession>, notarySignatures: List<TransactionSignature>) {
        progressTracker.currentStep = BROADCASTING_POST_NOTARISATION
        logger.info("Sending notarised signatures.")
        sessions.forEach { session ->
            try {
                logger.debug { "Sending transaction to party $session." }
                session.send(notarySignatures)
                // remote will finalise txn with notary signatures
            } catch (e: UnexpectedFlowEndException) {
                throw UnexpectedFlowEndException(
                        "${session.counterparty} has finished prematurely and we're trying to send them the notary signature(s). " +
                                "Did they forget to call ReceiveFinalityFlow? (${e.message})",
                        e.cause,
                        e.originalErrorId
                )
            }
        }
        progressTracker.currentStep = FINALISING_TRANSACTION
        serviceHub.finalizeTransactionWithExtraSignatures(transaction + notarySignatures, notarySignatures, statesToRecord)
        logger.info("Finalised transaction locally.")
    }

    @Suspendable
    private fun broadcastToOtherParticipants(externalTxParticipants: Set<Party>, sessions: Collection<FlowSession>, tx: SignedTransaction) {
        if (newApi) {
            oldV3Broadcast(tx, oldParticipants.toSet())
            for (session in sessions) {
                try {
                    logger.debug { "Sending transaction to party $session." }
                    subFlow(SendTransactionFlow(session, tx))
                } catch (e: UnexpectedFlowEndException) {
                    throw UnexpectedFlowEndException(
                            "${session.counterparty} has finished prematurely and we're trying to send them the finalised transaction. " +
                                    "Did they forget to call ReceiveFinalityFlow? (${e.message})",
                            e.cause,
                            e.originalErrorId
                    )
                }
            }
        } else {
            oldV3Broadcast(tx, (externalTxParticipants + oldParticipants).toSet())
        }
    }

    @Suspendable
    private fun oldV3Broadcast(notarised: SignedTransaction, recipients: Set<Party>) {
        for (recipient in recipients) {
            if (!serviceHub.myInfo.isLegalIdentity(recipient)) {
                logger.debug { "Sending transaction to party $recipient." }
                val session = initiateFlow(recipient)
                subFlow(SendTransactionFlow(session, notarised))
                logger.info("Party $recipient received the transaction.")
            }
        }
    }

    private fun logCommandData() {
        if (logger.isDebugEnabled) {
            val commandDataTypes = transaction.tx.commands.asSequence().mapNotNull { it.value::class.qualifiedName }.distinct()
            logger.debug("Started finalization, commands are ${commandDataTypes.joinToString(", ", "[", "]")}.")
        }
    }

    @Suspendable
    private fun recordTransactionLocally(tx: SignedTransaction): SignedTransaction {
        serviceHub.telemetryServiceInternal.span("${this::class.java.name}#recordTransactionLocally", flowLogic = this) {
            serviceHub.recordTransactions(statesToRecord, listOf(tx))
            logger.info("Recorded transaction locally.")
            return tx
        }
    }

    @Suspendable
    private fun recordUnnotarisedTransaction(tx: SignedTransaction): SignedTransaction {
        progressTracker.currentStep = RECORD_UNNOTARISED
        serviceHub.recordUnnotarisedTransaction(tx,
                FlowTransactionMetadata(
                        serviceHub.myInfo.legalIdentities.first().name,
                        statesToRecord,
                        sessions.map { it.counterparty.name }.toSet()))
        logger.info("Recorded transaction without notary signature(s) locally.")
        return tx
    }

    @Suspendable
    private fun notariseOrRecord(): SignedTransaction {
        serviceHub.telemetryServiceInternal.span("${this::class.java.name}#notariseAndRecord", flowLogic = this) {
            return if (needsNotarySignature(transaction)) {
                progressTracker.currentStep = NOTARISING
                val notarySignatures = subFlow(NotaryFlow.Client(transaction, skipVerification = true))
                logger.info("Transaction notarised.")
                transaction + notarySignatures
            } else {
                logger.info("No need to notarise this transaction. Recording locally.")
                recordTransactionLocally(transaction)
                transaction
            }
        }
    }

    private fun needsNotarySignature(stx: SignedTransaction): Boolean {
        val wtx = stx.tx
        val needsNotarisation = wtx.inputs.isNotEmpty() || wtx.references.isNotEmpty() || wtx.timeWindow != null
        return needsNotarisation && hasNoNotarySignature(stx)
    }

    private fun hasNoNotarySignature(stx: SignedTransaction): Boolean {
        val notaryKey = stx.tx.notary?.owningKey
        val signers = stx.sigs.asSequence().map { it.by }.toSet()
        return notaryKey?.isFulfilledBy(signers) != true
    }

    private fun extractExternalParticipants(ltx: LedgerTransaction): Set<Party> {
        val participants = ltx.outputStates.flatMap { it.participants } + ltx.inputStates.flatMap { it.participants }
        return groupAbstractPartyByWellKnownParty(serviceHub, participants).keys - serviceHub.myInfo.legalIdentities
    }

    private fun verifyTx(): LedgerTransaction {
        val notary = transaction.tx.notary
        // The notary signature(s) are allowed to be missing but no others.
        if (notary != null) transaction.verifySignaturesExcept(notary.owningKey) else transaction.verifyRequiredSignatures()
        // TODO= [CORDA-3267] Remove duplicate signature verification
        val ltx = transaction.toLedgerTransaction(serviceHub, false)
        ltx.verify()
        return ltx
    }
}

object NotarySigCheck {
    fun needsNotarySignature(stx: SignedTransaction): Boolean {
        val wtx = stx.tx
        val needsNotarisation = wtx.inputs.isNotEmpty() || wtx.references.isNotEmpty() || wtx.timeWindow != null
        return needsNotarisation && hasNoNotarySignature(stx)
    }

    private fun hasNoNotarySignature(stx: SignedTransaction): Boolean {
        val notaryKey = stx.tx.notary?.owningKey
        val signers = stx.sigs.asSequence().map { it.by }.toSet()
        return notaryKey?.isFulfilledBy(signers) != true
    }
}

/**
 * The receiving counterpart to [FinalityFlow].
 *
 * All parties who are receiving a finalised transaction from a sender flow must subcall this flow in their own flows.
 *
 * It's typical to have already signed the transaction proposal in the same workflow using [SignTransactionFlow]. If so
 * then the transaction ID can be passed in as an extra check to ensure the finalised transaction is the one that was signed
 * before it's committed to the vault.
 *
 * @param otherSideSession The session which is providing the transaction to record.
 * @param expectedTxId Expected ID of the transaction that's about to be received. This is typically retrieved from
 * [SignTransactionFlow]. Setting it to null disables the expected transaction ID check.
 * @param statesToRecord Which states to commit to the vault. Defaults to [StatesToRecord.ONLY_RELEVANT].
 */
class ReceiveFinalityFlow @JvmOverloads constructor(private val otherSideSession: FlowSession,
                                                    private val expectedTxId: SecureHash? = null,
                                                    private val statesToRecord: StatesToRecord = ONLY_RELEVANT) : FlowLogic<SignedTransaction>() {
    @Suppress("ComplexMethod")
    @Suspendable
    override fun call(): SignedTransaction {
        try {
            val stx = subFlow(object : ReceiveTransactionFlow(otherSideSession,
                    checkSufficientSignatures = false, statesToRecord = statesToRecord, deferredAck = true) {
                override fun checkBeforeRecording(stx: SignedTransaction) {
                    require(expectedTxId == null || expectedTxId == stx.id) {
                        "We expected to receive transaction with ID $expectedTxId but instead got ${stx.id}. Transaction was" +
                                "not recorded and nor its states sent to the vault."
                    }
                }
            })
            val fromTwoPhaseFinalityNode = serviceHub.networkMapCache.getNodeByLegalName(otherSideSession.counterparty.name)?.platformVersion!! >= PlatformVersionSwitches.TWO_PHASE_FINALITY
            if (fromTwoPhaseFinalityNode && needsNotarySignature(stx)) {
                logger.info("Peer recording transaction without notary signature.")
                serviceHub.recordUnnotarisedTransaction(stx,
                        FlowTransactionMetadata(otherSideSession.counterparty.name, statesToRecord))
                otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (overrideAutoAck)
                logger.info("Peer recorded transaction without notary signature.")

                val notarySignatures = otherSideSession.receive<List<TransactionSignature>>()
                        .unwrap { it }
                logger.info("Peer received notarised signature.")
                serviceHub.finalizeTransactionWithExtraSignatures(stx + notarySignatures, notarySignatures, statesToRecord)
                logger.info("Peer finalised transaction with notary signature.")
            } else {
                serviceHub.recordTransactions(statesToRecord, setOf(stx))
                otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (overrideAutoAck)
                logger.info("Peer successfully recorded received transaction.")
            }
            return stx
        } catch (e: SQLException) {
            logger.error("Peer failure upon recording or finalising transaction: $e")
            otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (overrideAutoAck)
            throw UnexpectedFlowEndException("Peer failure upon recording or finalising transaction.", e.cause)
        } catch (ce: CordaException) {
            logger.error("Peer failure within finality: $ce")
            // without this send() the flow blocks indefinitely:
            otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (overrideAutoAck)
            throw ce
        }
    }
}
