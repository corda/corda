package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.contracts.TransactionBuilder
import com.r3corda.core.contracts.WireTransaction
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.ProgressTracker
import java.util.*


/**
 * Finalise a transaction by notarising it, then recording it locally, and then sending it to all involved parties.
 *
 * @param transaction to commit.
 * @param events information on the event(s) which triggered the transaction.
 * @param participants a list of participants involved in the transaction.
 * @return a list of participants who were successfully notified of the transaction.
 */
// TODO: Event needs to be replaced with something that's meaningful, but won't ever contain sensitive
//       information (such as internal details of an account to take payment from). Suggest
//       splitting ClientToServiceCommand into public and private parts, with only the public parts
//       relayed here.
class FinalityProtocol(val transaction: SignedTransaction,
                       val events: Set<ClientToServiceCommand>,
                       val participants: Set<Party>,
                       override val progressTracker: ProgressTracker = tracker()): ProtocolLogic<Unit>() {
    companion object {
        object NOTARISING : ProgressTracker.Step("Requesting signature by notary service")
        object BROADCASTING : ProgressTracker.Step("Broadcasting transaction to participants")

        fun tracker() = ProgressTracker(NOTARISING, BROADCASTING)
    }

    override val topic: String
        get() = throw UnsupportedOperationException()

    @Suspendable
    override fun call() {
        progressTracker.currentStep = NOTARISING
        // Notarise the transaction if needed
        val notarisedTransaction = if (needsNotarySignature(transaction)) {
            val notarySig = subProtocol(NotaryProtocol.Client(transaction))
            transaction.withAdditionalSignature(notarySig)
        } else {
            transaction
        }

        // Let everyone else know about the transaction
        progressTracker.currentStep = BROADCASTING
        subProtocol(BroadcastTransactionProtocol(notarisedTransaction, events, participants))
    }

    private fun needsNotarySignature(transaction: SignedTransaction) = expectsNotarySignature(transaction.tx) && hasNoNotarySignature(transaction)
    private fun expectsNotarySignature(transaction: WireTransaction) = transaction.notary != null && transaction.notary.owningKey in transaction.signers
    private fun hasNoNotarySignature(transaction: SignedTransaction) = transaction.tx.notary?.owningKey !in transaction.sigs.map { it.by }
}
