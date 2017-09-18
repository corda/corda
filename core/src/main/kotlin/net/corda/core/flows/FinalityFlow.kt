package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

/**
 * Verifies the given transaction, then sends it to the named notary. If the notary agrees that the transaction
 * is acceptable then it is from that point onwards committed to the ledger, and will be written through to the
 * vault. Additionally it will be distributed to the parties reflected in the participants list of the states.
 *
 * The transaction is expected to have already been resolved: if its dependencies are not available in local
 * storage, verification will fail. It must have signatures from all necessary parties other than the notary.
 *
 * If specified, the extra recipients are sent all the given transactions. The base set of parties to inform of each
 * transaction are calculated on a per transaction basis from the contract-given set of participants.
 *
 * The flow returns the same transaction but with the additional signatures from the notary.
 *
 * @param transaction What to commit.
 * @param sendTo Collection of established flow sessions to parties to which to send the notarised transaction to. There
 * can be no sessions which point back to the local node. [SendTransactionFlow] is called for each session therefore each
 * counterparty *must* sub-flow [ReceiveTransactionFlow].
 */
class FinalityFlow(private val transaction: SignedTransaction,
                   private val sendTo: Collection<FlowSession>,
                   override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(transaction: SignedTransaction, sendTo: Collection<FlowSession>) : this(transaction, sendTo, tracker())
    constructor(transaction: SignedTransaction, firstSendTo: FlowSession, vararg restSendTo: FlowSession) :
            this(transaction, listOf(firstSendTo, *restSendTo))

    companion object {
        object NOTARISING : ProgressTracker.Step("Requesting signature by notary service") {
            override fun childProgressTracker() = NotaryFlow.Client.tracker()
        }

        object BROADCASTING : ProgressTracker.Step("Broadcasting transaction to participants")

        @JvmStatic
        fun tracker() = ProgressTracker(NOTARISING, BROADCASTING)
    }

    @Suspendable
    @Throws(NotaryException::class)
    override fun call(): SignedTransaction {
        require(sendTo.none { serviceHub.myInfo.isLegalIdentity(it.counterparty) }) {
            "Transaction is recorded locally so no need to send to self"
        }

        // Note: this method is carefully broken up to minimize the amount of data reachable from the stack at
        // the point where subFlow is invoked, as that minimizes the checkpointing work to be done.
        //
        // Lookup the resolved transactions and use them to map each signed transaction to the list of participants.
        // Then send to the notary if needed, record locally and distribute.
        verifyTx()
        progressTracker.currentStep = NOTARISING
        val notarised = notariseAndRecord()
        progressTracker.currentStep = BROADCASTING
        sendTo.forEach { subFlow(SendTransactionFlow(it, notarised)) }
        return notarised
    }

    @Suspendable
    private fun notariseAndRecord(): SignedTransaction {
        val notarised = if (needsNotarySignature(transaction)) {
            val notarySignatures = subFlow(NotaryFlow.Client(transaction))
            transaction + notarySignatures
        } else {
            transaction
        }
        serviceHub.recordTransactions(notarised)
        return notarised
    }

    private fun needsNotarySignature(stx: SignedTransaction): Boolean {
        val wtx = stx.tx
        val needsNotarisation = wtx.inputs.isNotEmpty() || wtx.timeWindow != null
        return needsNotarisation && hasNoNotarySignature(stx)
    }

    private fun hasNoNotarySignature(stx: SignedTransaction): Boolean {
        val notaryKey = stx.tx.notary?.owningKey
        val signers = stx.sigs.map { it.by }.toSet()
        return !(notaryKey?.isFulfilledBy(signers) ?: false)
    }

    private fun verifyTx() {
        val notary = transaction.tx.notary
        // The notary signature(s) are allowed to be missing but no others.
        if (notary != null) transaction.verifySignaturesExcept(notary.owningKey) else transaction.verifyRequiredSignatures()
        transaction.toLedgerTransaction(serviceHub, false).verify()
    }
}
