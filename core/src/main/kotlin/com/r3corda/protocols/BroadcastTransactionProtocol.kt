package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.transactions.SignedTransaction


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
    companion object {
        /** Topic for messages notifying a node of a new transaction */
        val TOPIC = "platform.wallet.notify_tx"
    }

    override val topic: String = TOPIC

    data class NotifyTxRequestMessage(val tx: SignedTransaction,
                                      val events: Set<ClientToServiceCommand>,
                                      override val replyToParty: Party,
                                      override val sendSessionID: Long = random63BitValue(),
                                      override val receiveSessionID: Long = random63BitValue()) : HandshakeMessage

    @Suspendable
    override fun call() {
        // Record it locally
        serviceHub.recordTransactions(notarisedTransaction)

        // TODO: Messaging layer should handle this broadcast for us (although we need to not be sending
        // session ID, for that to work, as well).
        participants.filter { it != serviceHub.storageService.myLegalIdentity }.forEach { participant ->
            val msg = NotifyTxRequestMessage(
                    notarisedTransaction,
                    events,
                    serviceHub.storageService.myLegalIdentity)
            send(participant, msg)
        }
    }
}
