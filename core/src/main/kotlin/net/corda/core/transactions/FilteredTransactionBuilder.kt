package net.corda.core.transactions

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import java.lang.IllegalArgumentException
import java.util.function.Predicate

/**
 * SGX: Helper class for generating filtered transactions
 */
data class FilteredTransactionBuilder(
        val wtx: WireTransaction,
        private var revealOutputState: (TransactionState<ContractState>, Int) -> Boolean = { x, y -> false },
        private var revealNetworkParametersHash: Boolean = false) {

    fun withOutputStates(selector: (TransactionState<ContractState>, Int) -> Boolean): FilteredTransactionBuilder {
        return copy(revealOutputState = selector)
    }

    fun includeNetworkParameters(flag: Boolean = true): FilteredTransactionBuilder {
        return copy(revealNetworkParametersHash = flag)
    }

    fun build(): FilteredTransaction {
        val filter = object: Predicate<Any> {
            override fun test(element: Any): Boolean {
                val (component, id) = (element as? Pair<Any, Int>)
                        ?: throw IllegalArgumentException("Illegal argument type")
                return when (component) {
                    is TransactionState<ContractState> -> revealOutputState(component, id)
                    is NetworkParametersHash -> revealNetworkParametersHash
                    else -> false
                }
            }
        }
        return FilteredTransaction.buildFilteredTransactionWithIndexedGroup(wtx, filter)
    }
}