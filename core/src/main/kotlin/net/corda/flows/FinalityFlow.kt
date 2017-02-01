package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker


/**
 * Finalise a transaction by notarising it, then recording it locally, and then sending it to all involved parties.
 *
 * @param transaction to commit.
 * @param participants a list of participants involved in the transaction.
 * @return a list of participants who were successfully notified of the transaction.
 */
class FinalityFlow(val transaction: SignedTransaction,
                   val participants: Set<Party>,
                   override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {
    constructor(transaction: SignedTransaction, participants: Set<Party>) : this(transaction, participants, tracker())

    companion object {
        object NOTARISING : ProgressTracker.Step("Requesting signature by notary service")
        object BROADCASTING : ProgressTracker.Step("Broadcasting transaction to participants")

        fun tracker() = ProgressTracker(NOTARISING, BROADCASTING)
    }

    @Suspendable
    @Throws(NotaryException::class)
    override fun call() {
        // TODO: Resolve the tx here: it's probably already been done, but re-resolution is a no-op and it'll make the API more forgiving.

        progressTracker.currentStep = NOTARISING
        // Notarise the transaction if needed
        val notarisedTransaction = if (needsNotarySignature(transaction)) {
            val notarySig = subFlow(NotaryFlow.Client(transaction))
            transaction.withAdditionalSignature(notarySig)
        } else {
            transaction
        }

        // Let everyone else know about the transaction
        progressTracker.currentStep = BROADCASTING
        subFlow(BroadcastTransactionFlow(notarisedTransaction, participants))
    }

    private fun needsNotarySignature(stx: SignedTransaction) = stx.tx.notary != null && hasNoNotarySignature(stx)
    private fun hasNoNotarySignature(stx: SignedTransaction): Boolean {
        val notaryKey = stx.tx.notary?.owningKey
        val signers = stx.sigs.map { it.by }.toSet()
        return !(notaryKey?.isFulfilledBy(signers) ?: false)
    }
}
