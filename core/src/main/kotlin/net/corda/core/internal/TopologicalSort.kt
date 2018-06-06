package net.corda.core.internal

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.SignedTransaction
import rx.Observable

/**
 * Provides a way to topologically sort SignedTransactions. This means that given any two transactions T1 and T2 in the
 * list returned by [complete] if T1 is a dependency of T2 then T1 will occur earlier than T2.
 */
class TopologicalSort {
    private val forwardGraph = HashMap<SecureHash, LinkedHashSet<SignedTransaction>>()
    private val transactions = ArrayList<SignedTransaction>()

    /**
     * Add a transaction to the to-be-sorted set of transactions.
     */
    fun add(stx: SignedTransaction) {
        for (input in stx.inputs) {
            // Note that we use a LinkedHashSet here to make the traversal deterministic (as long as the input list is)
            forwardGraph.getOrPut(input.txhash) { LinkedHashSet() }.add(stx)
        }
        transactions.add(stx)
    }

    /**
     * Return the sorted list of signed transactions.
     */
    fun complete(): List<SignedTransaction> {
        val visited = HashSet<SecureHash>(transactions.size)
        val result = ArrayList<SignedTransaction>(transactions.size)

        fun visit(transaction: SignedTransaction) {
            if (transaction.id !in visited) {
                visited.add(transaction.id)
                forwardGraph[transaction.id]?.forEach(::visit)
                result.add(transaction)
            }
        }

        transactions.forEach(::visit)
        return result.reversed()
    }
}

private fun getOutputStateRefs(stx: SignedTransaction): List<StateRef> {
    return stx.coreTransaction.outputs.mapIndexed { i, _ -> StateRef(stx.id, i) }
}

/**
 * Topologically sort a SignedTransaction Observable on the fly by buffering transactions until all dependencies are met.
 * @param initialUnspentRefs the list of unspent references that may be spent by transactions in the observable. This is
 *     the initial set of references the sort uses to decide whether to buffer transactions or not. For example if this
 *     is empty then the Observable should start with issue transactions that don't have inputs.
 */
fun Observable<SignedTransaction>.topologicalSort(initialUnspentRefs: Collection<StateRef>): Observable<SignedTransaction> {
    data class State(
            val unspentRefs: HashSet<StateRef>,
            val bufferedTopologicalSort: TopologicalSort,
            val bufferedInputs: HashSet<StateRef>,
            val bufferedOutputs: HashSet<StateRef>
    )

    var state = State(
            unspentRefs = HashSet(initialUnspentRefs),
            bufferedTopologicalSort = TopologicalSort(),
            bufferedInputs = HashSet(),
            bufferedOutputs = HashSet()
    )

    return concatMapIterable { stx ->
        val results = ArrayList<SignedTransaction>()
        if (state.unspentRefs.containsAll(stx.inputs)) {
            // Dependencies are satisfied
            state.unspentRefs.removeAll(stx.inputs)
            state.unspentRefs.addAll(getOutputStateRefs(stx))
            results.add(stx)
        } else {
            // Dependencies are not satisfied, buffer
            state.bufferedTopologicalSort.add(stx)
            state.bufferedInputs.addAll(stx.inputs)
            for (outputRef in getOutputStateRefs(stx)) {
                if (!state.bufferedInputs.remove(outputRef)) {
                    state.bufferedOutputs.add(outputRef)
                }
            }
            for (inputRef in stx.inputs) {
                if (!state.bufferedOutputs.remove(inputRef)) {
                    state.bufferedInputs.add(inputRef)
                }
            }
        }
        if (state.unspentRefs.containsAll(state.bufferedInputs)) {
            // Buffer satisfied
            results.addAll(state.bufferedTopologicalSort.complete())
            state.unspentRefs.removeAll(state.bufferedInputs)
            state.unspentRefs.addAll(state.bufferedOutputs)
            state = State(
                    unspentRefs = state.unspentRefs,
                    bufferedTopologicalSort = TopologicalSort(),
                    bufferedInputs = HashSet(),
                    bufferedOutputs = HashSet()
            )
            results
        } else {
            // Buffer not satisfied
            state = State(
                    unspentRefs = state.unspentRefs,
                    bufferedTopologicalSort = state.bufferedTopologicalSort,
                    bufferedInputs = state.bufferedInputs,
                    bufferedOutputs = state.bufferedOutputs
            )
            results
        }
    }
}
