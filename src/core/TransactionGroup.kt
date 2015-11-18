package core

import java.util.*

class TransactionResolutionException(val hash: SecureHash) : Exception()
class TransactionConflictException(val conflictRef: ContractStateRef, val tx1: LedgerTransaction, val tx2: LedgerTransaction) : Exception()

/**
 * A TransactionGroup defines a directed acyclic graph of transactions that can be resolved with each other and then
 * verified. Successful verification does not imply the non-existence of other conflicting transactions: simply that
 * this subgraph does not contain conflicts and is accepted by the involved contracts.
 *
 * The inputs of the provided transactions must be resolvable either within the [transactions] set, or from the
 * [nonVerifiedRoots] set. Transactions in the non-verified set are ignored other than for looking up input states.
 */
class TransactionGroup(val transactions: Set<LedgerTransaction>, val nonVerifiedRoots: Set<LedgerTransaction>) {

    /**
     * Verifies the group and returns the set of resolved transactions.
     */
    fun verify(programMap: Map<SecureHash, Contract>): Set<TransactionForVerification> {
        // Check that every input can be resolved to an output.
        // Check that no output is referenced by more than one input.
        // Cycles should be impossible due to the use of hashes as pointers.
        check(transactions.intersect(nonVerifiedRoots).isEmpty())

        val hashToTXMap: Map<SecureHash, List<LedgerTransaction>> = (transactions + nonVerifiedRoots).groupBy { it.hash }
        val refToConsumingTXMap = hashMapOf<ContractStateRef, LedgerTransaction>()

        val resolved = HashSet<TransactionForVerification>(transactions.size)
        for (tx in transactions) {
            val inputs = ArrayList<ContractState>(tx.inStateRefs.size)
            for (ref in tx.inStateRefs) {
                val conflict = refToConsumingTXMap[ref]
                if (conflict != null)
                    throw TransactionConflictException(ref, tx, conflict)
                refToConsumingTXMap[ref] = tx

                // Look up the connecting transaction.
                val ltx = hashToTXMap[ref.txhash]?.single() ?: throw TransactionResolutionException(ref.txhash)
                // Look up the output in that transaction by index.
                inputs.add(ltx.outStates[ref.index])
            }
            resolved.add(TransactionForVerification(inputs, tx.outStates, tx.commands, tx.time, tx.hash))
        }

        for (tx in resolved) {
            try {
                tx.verify(programMap)
            } catch(e: Exception) {
                println(tx)
                throw e
            }
        }
        return resolved
    }

}