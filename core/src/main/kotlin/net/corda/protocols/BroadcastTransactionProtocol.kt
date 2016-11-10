package net.corda.protocols

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ClientToServiceCommand
import net.corda.core.crypto.Party
import net.corda.core.node.recordTransactions
import net.corda.core.protocols.ProtocolLogic
import net.corda.core.transactions.SignedTransaction


/**
 * Notify all involved parties about a transaction, including storing a copy. Normally this would be called via
 * [FinalityProtocol].
 *
 * @param notarisedTransaction transaction which has been notarised (if needed) and is ready to notify nodes about.
 * @param events information on the event(s) which triggered the transaction.
 * @param participants a list of participants involved in the transaction.
 * @return a list of participants who were successfully notified of the transaction.
 */
// TODO: Event needs to be replaced with something that's meaningful, but won't ever contain sensitive
//       information (such as internal details of an account to take payment from). Suggest
//       splitting ClientToServiceCommand into public and private parts, with only the public parts
//       relayed here.
class BroadcastTransactionProtocol(val notarisedTransaction: SignedTransaction,
                                   val events: Set<ClientToServiceCommand>,
                                   val participants: Set<Party>) : ProtocolLogic<Unit>() {

    data class NotifyTxRequest(val tx: SignedTransaction, val events: Set<ClientToServiceCommand>)

    @Suspendable
    override fun call() {
        // Record it locally
        serviceHub.recordTransactions(notarisedTransaction)

        // TODO: Messaging layer should handle this broadcast for us
        val msg = NotifyTxRequest(notarisedTransaction, events)
        participants.filter { it != serviceHub.myInfo.legalIdentity }.forEach { participant ->
            send(participant, msg)
        }
    }
}
