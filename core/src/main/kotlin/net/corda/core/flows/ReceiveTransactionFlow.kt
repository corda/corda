package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.TransactionSignature
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.ServiceHubCoreInternal
import net.corda.core.internal.checkParameterHash
import net.corda.core.internal.pushToLoggingContext
import net.corda.core.internal.telemetry.telemetryServiceInternal
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Try
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.core.utilities.unwrap
import java.security.SignatureException
import java.time.Duration

/**
 * The [ReceiveTransactionFlow] should be called in response to the [SendTransactionFlow].
 *
 * This flow is a combination of [FlowSession.receive], resolve and [SignedTransaction.verify]. This flow will receive the
 * [SignedTransaction] and perform the resolution back-and-forth required to check the dependencies and download any missing
 * attachments. The flow will return the [SignedTransaction] after it is resolved and then verified using [SignedTransaction.verify].
 *
 * Please note that it will *not* store the transaction to the vault unless that is explicitly requested and checkSufficientSignatures is true.
 * Setting statesToRecord to anything else when checkSufficientSignatures is false will *not* update the vault.
 *
 * Attention: At the moment, this flow receives a [SignedTransaction] first thing and then proceeds by invoking a [ResolveTransactionsFlow] subflow.
 *            This is used as a criterion to identify cases, where a counterparty has failed notarising a transact
 *
 * @property otherSideSession session to the other side which is calling [SendTransactionFlow].
 * @property checkSufficientSignatures if true checks all required signatures are present. See [SignedTransaction.verify].
 * @property statesToRecord which transaction states should be recorded in the vault, if any.
 * @property deferredAck if set then the caller of this flow is responsible for explicitly sending a FetchDataFlow.Request.End
 *           acknowledgement to indicate transaction resolution is complete. See usage within [FinalityFlow].
 *           Not recommended for 3rd party use.
 */
open class ReceiveTransactionFlow constructor(private val otherSideSession: FlowSession,
                                              private val checkSufficientSignatures: Boolean = true,
                                              private val statesToRecord: StatesToRecord = StatesToRecord.NONE,
                                              private val handlePropagatedNotaryError: Boolean? = null) : FlowLogic<SignedTransaction>() {
    @JvmOverloads
    constructor(
            otherSideSession: FlowSession,
            checkSufficientSignatures: Boolean = true,
            statesToRecord: StatesToRecord = StatesToRecord.NONE
    ) : this(otherSideSession, checkSufficientSignatures, statesToRecord, null)

    @Suppress("KDocMissingDocumentation")
    @Suspendable
    @Throws(SignatureException::class,
            AttachmentResolutionException::class,
            TransactionResolutionException::class,
            TransactionVerificationException::class)
    override fun call(): SignedTransaction {
        if (checkSufficientSignatures) {
            logger.trace { "Receiving a transaction from ${otherSideSession.counterparty}" }
        } else {
            logger.trace { "Receiving a transaction (but without checking the signatures) from ${otherSideSession.counterparty}" }
        }

        val payload = otherSideSession.receive<Any>().unwrap { it }
        return if (isReallyReceiveFinality(payload)) {
            doReceiveFinality(payload)
        } else {
            val deferredAck = isDeferredAck(payload)
            val stx = resolvePayload(payload)
            stx.pushToLoggingContext()
            logger.info("Received transaction acknowledgement request from party ${otherSideSession.counterparty}.")
            subFlow(ResolveTransactionsFlow(stx, otherSideSession, statesToRecord, deferredAck))
            checkParameterHash(stx.networkParametersHash)
            logger.info("Transaction dependencies resolution completed.")
            verifyTx(stx, checkSufficientSignatures)
            if (checkSufficientSignatures) {
                // We should only send a transaction to the vault for processing if we did in fact fully verify it, and
                // there are no missing signatures. We don't want partly signed stuff in the vault.
                checkBeforeRecording(stx)
                logger.info("Successfully received fully signed tx. Sending it to the vault for processing.")
                serviceHub.recordTransactions(statesToRecord, setOf(stx))
                logger.info("Successfully recorded received transaction locally.")
                if (deferredAck) otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (deferredAck)
            }
            stx
        }
    }

    private fun verifyTx(stx: SignedTransaction, localCheckSufficientSignatures: Boolean) {
        try {
            stx.verify(serviceHub, localCheckSufficientSignatures)
        } catch (e: Exception) {
            logger.warn("Transaction verification failed.")
            throw e
        }
    }

    private fun isDeferredAck(payload: Any): Boolean {
        return payload is SignedTransactionWithDistributionList && checkSufficientSignatures && payload.isFinality
    }

    @Suspendable
    private fun doReceiveFinality(payload: Any): SignedTransaction {
        val stx = resolvePayload(payload)
        stx.pushToLoggingContext()
        logger.info("Received transaction acknowledgement request from party ${otherSideSession.counterparty}.")
        checkParameterHash(stx.networkParametersHash)
        subFlow(ResolveTransactionsFlow(stx, otherSideSession, statesToRecord, true))
        logger.info("Transaction dependencies resolution completed.")
        verifyTx(stx, false)
        serviceHub.telemetryServiceInternal.span("${this::class.java.name}#recordUnnotarisedTransaction", flowLogic = this) {
            logger.debug { "Peer recording transaction without notary signature." }
            (serviceHub as ServiceHubCoreInternal).recordUnnotarisedTransaction(stx)
        }
        otherSideSession.send(FetchDataFlow.Request.End) // Finish fetching data (deferredAck)
        logger.info("Peer recorded transaction without notary signature. Waiting to receive notary signature.")
        try {
            val notarySignatures = otherSideSession.receive<Try<List<TransactionSignature>>>().unwrap { it.getOrThrow() }
            serviceHub.telemetryServiceInternal.span("${this::class.java.name}#finalizeTransactionWithExtraSignatures", flowLogic = this) {
                logger.debug { "Peer received notarised signature." }
                (serviceHub as ServiceHubCoreInternal).finalizeTransactionWithExtraSignatures(stx, notarySignatures, statesToRecord)
                logger.info("Peer finalised transaction with notary signature.")
            }
            return stx + notarySignatures
        } catch (e: NotaryException) {
            logger.info("Peer received notary error.")
            val overrideHandlePropagatedNotaryError = handlePropagatedNotaryError
                    ?: (serviceHub.cordappProvider.getAppContext().cordapp.targetPlatformVersion >= PlatformVersionSwitches.TWO_PHASE_FINALITY)
            if (overrideHandlePropagatedNotaryError) {
                (serviceHub as ServiceHubCoreInternal).removeUnnotarisedTransaction(stx.id)
                sleep(Duration.ZERO) // force checkpoint to persist db update.
                throw e
            } else {
                otherSideSession.receive<Any>() // simulate unexpected flow end
            }
        }
        return stx
    }

    private fun isReallyReceiveFinality(payload: Any): Boolean {
        return payload is SignedTransactionWithDistributionList && checkSufficientSignatures && payload.isFinality && NotarySigCheck.needsNotarySignature(payload.stx)
    }

    open fun resolvePayload(payload: Any): SignedTransaction {
        return if (payload is SignedTransactionWithDistributionList) {
            if (checkSufficientSignatures) {
                (serviceHub as ServiceHubCoreInternal).recordReceiverTransactionRecoveryMetadata(payload.stx.id, otherSideSession.counterparty.name,
                        TransactionMetadata(otherSideSession.counterparty.name, DistributionList.ReceiverDistributionList(payload.distributionList, statesToRecord)))
                payload.stx
            } else payload.stx
        } else payload as SignedTransaction
    }

    /**
     * Hook to perform extra checks on the received transaction just before it's recorded. The transaction has already
     * been resolved and verified at this point.
     */
    @Suspendable
    @Throws(FlowException::class)
    protected open fun checkBeforeRecording(stx: SignedTransaction) = Unit
}

/**
 * The [ReceiveStateAndRefFlow] should be called in response to the [SendStateAndRefFlow].
 *
 * This flow is a combination of [FlowSession.receive] and resolve. This flow will receive a list of [StateAndRef]
 * and perform the resolution back-and-forth required to check the dependencies.
 * The flow will return the list of [StateAndRef] after it is resolved.
 */
// @JvmSuppressWildcards is used to suppress wildcards in return type when calling `subFlow(new ReceiveStateAndRef<T>(otherParty))` in java.
class ReceiveStateAndRefFlow<out T : ContractState>(private val otherSideSession: FlowSession) : FlowLogic<@JvmSuppressWildcards List<StateAndRef<T>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<T>> {
        return otherSideSession.receive<List<StateAndRef<T>>>().unwrap {
            val txHashes = it.asSequence().map { it.ref.txhash }.toSet()
            subFlow(ResolveTransactionsFlow(txHashes, otherSideSession))
            it
        }
    }
}
