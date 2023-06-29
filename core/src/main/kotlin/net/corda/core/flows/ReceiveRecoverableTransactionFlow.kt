package net.corda.core.flows

import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.ServiceHubCoreInternal
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

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
open class ReceiveRecoverableTransactionFlow constructor(private val otherSideSession: FlowSession,
                                                         private val checkSufficientSignatures: Boolean = true,
                                                         private val statesToRecord: StatesToRecord = StatesToRecord.NONE,
                                                         private val deferredAck: Boolean = false) : ReceiveTransactionFlow(otherSideSession, checkSufficientSignatures, statesToRecord, deferredAck) {
    @JvmOverloads constructor(
            otherSideSession: FlowSession,
            checkSufficientSignatures: Boolean = true,
            statesToRecord: StatesToRecord = StatesToRecord.NONE
    ) : this(otherSideSession, checkSufficientSignatures, statesToRecord, false)

    override fun resolvePayload(payload: Any): SignedTransaction {
        return if (payload is SignedTransactionWithDistributionList) {
            (serviceHub as ServiceHubCoreInternal).recordReceiverTransactionRecoveryMetadata(
                    payload.stx.id,
                    otherSideSession.counterparty.name,
                    ourIdentity.name,
                    payload.senderStatesToRecord,
                    payload.distributionList)
            payload.stx
        } else payload as SignedTransaction
    }
}

