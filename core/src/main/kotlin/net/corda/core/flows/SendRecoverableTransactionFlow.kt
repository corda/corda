package net.corda.core.flows

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

/**
 * This flow extends [DataVendingFlow] to pass in transaction recovery metadata for the purpose of ledger recovery.
 */
class SendRecoverableTransactionFlow(val stx: SignedTransaction,
                               val participantSessions: Set<FlowSession>,
                               val observerSessions: Set<FlowSession>,
                               val senderStatesToRecord: StatesToRecord) : DataVendingFlow(participantSessions + observerSessions, stx,
                                   TransactionMetadata(DUMMY_PARTICIPANT_NAME,
                                       DistributionList(senderStatesToRecord, (participantSessions.map { it.counterparty.name to StatesToRecord.ONLY_RELEVANT}).toMap() +
                                               (observerSessions.map { it.counterparty.name to StatesToRecord.ALL_VISIBLE}).toMap()
                                   ))) {
    // Note: DUMMY_PARTICIPANT_NAME to be substituted with actual "ourIdentity.name" in flow call()
    companion object {
        val DUMMY_PARTICIPANT_NAME = CordaX500Name("Transaction Participant", "London", "GB")
    }
}

@CordaSerializable
data class SignedTransactionWithDistributionList(
        val stx: SignedTransaction,
        val distributionList: ByteArray
)