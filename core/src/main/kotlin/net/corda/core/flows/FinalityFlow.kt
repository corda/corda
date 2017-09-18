package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.toNonEmptySet

/**
 * Verifies the given transactions, then sends them to the named notary. If the notary agrees that the transactions
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
open class FinalityFlow(val transactions: Iterable<SignedTransaction>,
                        private val extraRecipients: Set<Party>,
                        override val progressTracker: ProgressTracker) : FlowLogic<List<SignedTransaction>>() {
    constructor(transaction: SignedTransaction, extraParticipants: Set<Party>) : this(listOf(transaction), extraParticipants, tracker())
    constructor(transaction: SignedTransaction) : this(listOf(transaction), emptySet(), tracker())
    constructor(transaction: SignedTransaction, progressTracker: ProgressTracker) : this(listOf(transaction), emptySet(), progressTracker)

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
        val notarisedTxns: List<Pair<SignedTransaction, Set<Party>>> = resolveDependenciesOf(transactions)
                .map { (stx, ltx) -> Pair(notariseAndRecord(stx), lookupParties(ltx)) }

        // Each transaction has its own set of recipients, but extra recipients get them all.
        progressTracker.currentStep = BROADCASTING
        for ((stx, parties) in notarisedTxns) {
            val participants = (parties + extraRecipients).filter { !serviceHub.myInfo.isLegalIdentity(it) }.toSet()
            if (participants.isNotEmpty()) {
                broadcastTransaction(stx, participants.toNonEmptySet())
            }
        }
        return notarisedTxns.map { it.first }
    }

    /**
     * Broadcast a transaction to the participants. By default calls [BroadcastTransactionFlow], however can be
     * overridden for more complex transaction delivery protocols (for example where not all parties know each other).
     *
     * @param participants the participants to send the transaction to. This is expected to include extra participants
     * and exclude the local node.
     */
    @Suspendable
    open protected fun broadcastTransaction(stx: SignedTransaction, participants: NonEmptySet<Party>) {
        subFlow(BroadcastTransactionFlow(stx, participants))
    }

    @Suspendable
    private fun notariseAndRecord(stx: SignedTransaction): SignedTransaction {
        val notarised = if (needsNotarySignature(stx)) {
            val notarySignatures = subFlow(NotaryFlow.Client(stx))
            stx + notarySignatures
        } else {
            stx
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

    /**
     * Resolve the parties involved in a transaction.
     *
     * The default implementation throws an exception if an unknown party is encountered.
     */
    open protected fun lookupParties(ltx: LedgerTransaction): Set<Party> {
        // Calculate who is meant to see the results based on the participants involved.
        return extractParticipants(ltx).map {
            serviceHub.identityService.partyFromAnonymous(it)
                    ?: throw IllegalArgumentException("Could not resolve well known identity of participant $it")
        }.toSet()
    }

    /**
     * Helper function to extract all participants from a ledger transaction. Intended to help implement [lookupParties]
     * overriding functions.
     */
    protected fun extractParticipants(ltx: LedgerTransaction): List<AbstractParty> {
        return ltx.outputStates.flatMap { it.participants } + ltx.inputStates.flatMap { it.participants }
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
            // The notary signature(s) are allowed to be missing but no others.
            if (notary != null) stx.verifySignaturesExcept(notary.owningKey) else stx.verifyRequiredSignatures()
            val ltx = stx.toLedgerTransaction(augmentedLookup, false)
            ltx.verify()
            stx to ltx
        }
    }
}
