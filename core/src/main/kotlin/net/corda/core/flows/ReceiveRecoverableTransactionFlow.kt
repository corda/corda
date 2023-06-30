package net.corda.core.flows

import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.ServiceHubCoreInternal
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

/**
 * This flow extends [ReceiveTransactionFlow] to store recovery metadata
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
                    statesToRecord,
                    payload.distributionList)
            payload.stx
        } else payload as SignedTransaction
    }
}

