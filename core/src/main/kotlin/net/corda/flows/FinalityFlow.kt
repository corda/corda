package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

/**
 * Verifies the given transactions, then sends them to the named notaries. If the notary agrees that the transactions
 * are acceptable then they are from that point onwards committed to the ledger, and will be written through to the
 * vault. Additionally they will be distributed to the parties reflected in the participants list of the states.
 *
 * The transactions will be topologically sorted before commitment to ensure that dependencies are committed before
 * dependers, so you don't need to do this yourself.
 *
 * The transactions are expected to have already been resolved: if their dependencies are not available in local
 * storage or within the given set, verification will fail. They must have signatures from all necessary parties
 * other than the notary.
 *
 * If specified, the extra recipients are sent all the given transactions. The base set of parties to inform of each
 * transaction are calculated on a per transaction basis from the contract-given set of participants.
 *
 * The flow returns the same transactions, in the same order, with the additional signatures.
 *
 * @param transactions What to commit.
 * @param extraRecipients A list of additional participants to inform of the transaction.
 */
class FinalityFlow(val transactions: Iterable<SignedTransaction>,
                   val extraRecipients: Set<Party>,
                   override val progressTracker: ProgressTracker) : FlowLogic<List<SignedTransaction>>() {
    constructor(transaction: SignedTransaction, extraParticipants: Set<Party>) : this(listOf(transaction), extraParticipants, tracker())
    constructor(transaction: SignedTransaction) : this(listOf(transaction), emptySet(), tracker())

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
    override fun call(): List<SignedTransaction> {
        // Note: this method is carefully broken up to minimize the amount of data reachable from the stack at
        // the point where subFlow is invoked, as that minimizes the checkpointing work to be done.
        //
        // Lookup the resolved transactions and use them to map each signed transaction to the list of participants.
        // Then send to the notary if needed, record locally and distribute.
        progressTracker.currentStep = NOTARISING
        val notarisedTxns = notariseAndRecord(lookupParties(resolveDependenciesOf(transactions)))

        // Each transaction has its own set of recipients, but extra recipients get them all.
        progressTracker.currentStep = BROADCASTING
        val me = serviceHub.myInfo.legalIdentity
        for ((stx, parties) in notarisedTxns) {
            subFlow(BroadcastTransactionFlow(stx, parties + extraRecipients - me))
        }
        return notarisedTxns.map { it.first }
    }

    // TODO: API: Make some of these protected?

    @Suspendable
    private fun notariseAndRecord(stxnsAndParties: List<Pair<SignedTransaction, Set<Party>>>): List<Pair<SignedTransaction, Set<Party>>> {
        return stxnsAndParties.map { pair ->
            val stx = pair.first
            val notarised = if (needsNotarySignature(stx)) {
                val notarySig = subFlow(NotaryFlow.Client(stx))
                stx + notarySig
            } else {
                stx
            }
            serviceHub.recordTransactions(listOf(notarised))
            Pair(notarised, pair.second)
        }
    }

    private fun needsNotarySignature(stx: SignedTransaction) = stx.tx.notary != null && hasNoNotarySignature(stx)
    private fun hasNoNotarySignature(stx: SignedTransaction): Boolean {
        val notaryKey = stx.tx.notary?.owningKey
        val signers = stx.sigs.map { it.by }.toSet()
        return !(notaryKey?.isFulfilledBy(signers) ?: false)
    }

    private fun lookupParties(ltxns: List<Pair<SignedTransaction, LedgerTransaction>>): List<Pair<SignedTransaction, Set<Party>>> {
        return ltxns.map { pair ->
            val (stx, ltx) = pair
            // Calculate who is meant to see the results based on the participants involved.
            val keys = ltx.outputs.flatMap { it.data.participants } + ltx.inputs.flatMap { it.state.data.participants }
            // TODO: Is it safe to drop participants we don't know how to contact? Does not knowing how to contact them count as a reason to fail?
            val parties = keys.mapNotNull { serviceHub.identityService.partyFromKey(it) }.toSet()
            Pair(stx, parties)
        }
    }

    private fun resolveDependenciesOf(signedTransactions: Iterable<SignedTransaction>): List<Pair<SignedTransaction, LedgerTransaction>> {
        // Make sure the dependencies come before the dependers.
        val sorted = ResolveTransactionsFlow.topologicalSort(signedTransactions.toList())
        // Build a ServiceHub that consults the argument list as well as what's in local tx storage so uncommitted
        // transactions can depend on each other.
        val augmentedLookup = object : ServiceHub by serviceHub {
            val hashToTx = sorted.associateBy { it.id }
            override fun loadState(stateRef: StateRef): TransactionState<*> {
                val provided: TransactionState<ContractState>? = hashToTx[stateRef.txhash]?.let { it.tx.outputs[stateRef.index] }
                return provided ?: super.loadState(stateRef)
            }
        }
        // Load and verify each transaction.
        return sorted.map { stx ->
            val notary = stx.tx.notary
            // The notary signature is allowed to be missing but no others.
            val wtx = if (notary != null) stx.verifySignatures(notary.owningKey) else stx.verifySignatures()
            val ltx = wtx.toLedgerTransaction(augmentedLookup)
            ltx.verify()
            stx to ltx
        }
    }
}
