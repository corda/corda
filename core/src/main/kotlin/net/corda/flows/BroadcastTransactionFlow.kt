package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction


/**
 * Notify the specified parties about a transaction. The remote peers will download this transaction and its
 * dependency graph, verifying them all. The flow returns when all peers have acknowledged the transactions
 * as valid. Normally you wouldn't use this directly, it would be called via [FinalityFlow].
 *
 * @param notarisedTransaction transaction which has been notarised (if needed) and is ready to notify nodes about.
 * @param participants a list of participants involved in the transaction.
 * @return a list of participants who were successfully notified of the transaction.
 */
class BroadcastTransactionFlow(val notarisedTransaction: SignedTransaction,
                               val participants: Set<Party>) : FlowLogic<Unit>() {
    data class NotifyTxRequest(val tx: SignedTransaction)

    @Suspendable
    override fun call() {
        // TODO: Messaging layer should handle this broadcast for us
        val msg = NotifyTxRequest(notarisedTransaction)
        participants.filter { it != serviceHub.myInfo.legalIdentity }.forEach { participant ->
            // This pops out the other side in DataVending.NotifyTransactionHandler.
            send(participant, msg)
        }
    }
}
