package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
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
 * The flow returns the same transaction but with the additional signature from the notary.
 *
 * @param transaction What to commit.
 * @param broadcast Set of established flow sessions to parties to which to send the notarised transaction to. Every
 * participant in the transaction must have a session. There can be sessions to extra parties as well. However there can
 * be no sessions which point back to the local node, even if the local party is participant to the transactin. Each of
 * flows running on these counterparties must sub-flow [ReceiveTransactionFlow].
 */
class FinalityFlow(private val transaction: SignedTransaction,
                   private val broadcast: Set<FlowSession>,
                   override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(transaction: SignedTransaction, broadcast: Set<FlowSession>) : this(transaction, broadcast, tracker())

    companion object {
        object NOTARISING : ProgressTracker.Step("Requesting signature by notary service") {
            override fun childProgressTracker() = NotaryFlow.Client.tracker()
        }

        object BROADCASTING : ProgressTracker.Step("Broadcasting transaction to participants")

        // TODO: Make all tracker() methods @JvmStatic
        fun tracker() = ProgressTracker(NOTARISING, BROADCASTING)
    }

    @Suspendable
    @Throws(NotaryException::class)
    override fun call(): SignedTransaction {
        require(broadcast.none { serviceHub.myInfo.isLegalIdentity(it.counterparty) }) {
            "Cannot broadcast to self"
        }

        // Note: this method is carefully broken up to minimize the amount of data reachable from the stack at
        // the point where subFlow is invoked, as that minimizes the checkpointing work to be done.
        //
        // Lookup the resolved transactions and use them to map each signed transaction to the list of participants.
        // Then send to the notary if needed, record locally and distribute.
        val parties = lookupParties(verifyTx())
        for (party in parties) {
            require(broadcast.any { it.counterparty == party }) {
                "$party is a participant of the transaction but no session for it was provided"
            }
        }

        progressTracker.currentStep = NOTARISING
        val notarised = notariseAndRecord()
        progressTracker.currentStep = BROADCASTING
        broadcast.forEach { subFlow(SendTransactionFlow(it, notarised)) }
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

    private fun lookupParties(ltx: LedgerTransaction): Set<Party> {
        // Calculate who is meant to see the results based on the participants involved.
        return extractParticipants(ltx).mapNotNull {
            val party = serviceHub.identityService.partyFromAnonymous(it)
                    ?: throw IllegalArgumentException("Could not resolve well known identity of participant $it")
            if (serviceHub.myInfo.isLegalIdentity(party)) null else party
        }.toSet()
    }

    private fun extractParticipants(ltx: LedgerTransaction): List<AbstractParty> {
        return ltx.outputStates.flatMap { it.participants } + ltx.inputStates.flatMap { it.participants }
    }

    private fun verifyTx(): LedgerTransaction {
        val notary = transaction.tx.notary
        // The notary signature(s) are allowed to be missing but no others.
        if (notary != null) transaction.verifySignaturesExcept(notary.owningKey) else transaction.verifyRequiredSignatures()
        val ltx = transaction.toLedgerTransaction(serviceHub, false)
        ltx.verify()
        return ltx
    }
}
