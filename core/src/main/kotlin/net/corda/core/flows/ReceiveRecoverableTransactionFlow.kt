package net.corda.core.flows

import net.corda.core.internal.ServiceHubCoreInternal
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

/**
 * This flow extends [ReceiveTransactionFlow] to store transaction recovery metadata for the purpose of ledger recovery.
 */
open class ReceiveRecoverableTransactionFlow constructor(private val otherSideSession: FlowSession,
                                                         checkSufficientSignatures: Boolean = true,
                                                         private val statesToRecord: StatesToRecord = StatesToRecord.NONE,
                                                         deferredAck: Boolean = true) : ReceiveTransactionFlow(otherSideSession, checkSufficientSignatures, statesToRecord, deferredAck) {

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

