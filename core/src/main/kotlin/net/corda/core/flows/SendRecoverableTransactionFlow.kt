package net.corda.core.flows

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

/**
 * The [SendTransactionFlow] should be used to send a transaction to another peer that wishes to verify that transaction's
 * integrity by resolving and checking the dependencies as well. The other side should invoke [ReceiveTransactionFlow] at
 * the right point in the conversation to receive the sent transaction and perform the resolution back-and-forth required
 * to check the dependencies and download any missing attachments.
 *
 * @param stx the [SignedTransaction] being sent to the [otherSessions].
 * @param participantSessions the target parties which are participants to the transaction.
 * @param observerSessions the target parties which are observers to the transaction.
 * @param senderStatesToRecord the [StatesToRecord] relevancy information of the sender.
 */
class SendRecoverableTransactionFlow(val stx: SignedTransaction,
                               val participantSessions: Set<FlowSession>,
                               val observerSessions: Set<FlowSession>,
                               val senderStatesToRecord: StatesToRecord) : DataVendingFlow(participantSessions + observerSessions, stx,
                                   TransactionMetadata(DUMMY_PARTICIPANT_NAME,
                                       DistributionList(senderStatesToRecord, (participantSessions.map { it.counterparty.name to StatesToRecord.ONLY_RELEVANT}).toMap() +
                                               (observerSessions.map { it.counterparty.name to StatesToRecord.ALL_VISIBLE}).toMap()
                                   ))) {
    constructor(otherSide: FlowSession, stx: SignedTransaction) : this(stx, setOf(otherSide), emptySet(), StatesToRecord.NONE)
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