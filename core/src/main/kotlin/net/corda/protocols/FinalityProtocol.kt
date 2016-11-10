package net.corda.protocols

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ClientToServiceCommand
import net.corda.core.crypto.Party
import net.corda.core.protocols.ProtocolLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker


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

    @Suspendable
    override fun call() {
        // TODO: Resolve the tx here: it's probably already been done, but re-resolution is a no-op and it'll make the API more forgiving.

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

    private fun needsNotarySignature(stx: SignedTransaction) = stx.tx.notary != null && hasNoNotarySignature(stx)
    private fun hasNoNotarySignature(stx: SignedTransaction) = stx.tx.notary?.owningKey !in stx.sigs.map { it.by }
}
