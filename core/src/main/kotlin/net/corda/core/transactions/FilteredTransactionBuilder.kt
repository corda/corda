package net.corda.core.transactions

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.identity.Party
import java.lang.IllegalArgumentException
import java.util.function.Predicate

/**
 * SGX: Helper class for generating filtered transactions
 */
data class FilteredTransactionBuilder(
        val wtx: WireTransaction,
        private var revealOutputState: (TransactionState<ContractState>, Int) -> Boolean = { x, y -> false },
        private var revealNetworkParametersHash: Boolean = false,
        private var revealNotary: Boolean = false) {

    fun withOutputStates(selector: (TransactionState<ContractState>, Int) -> Boolean): FilteredTransactionBuilder {
        return copy(revealOutputState = selector)
    }

    fun includeNetworkParameters(flag: Boolean = true): FilteredTransactionBuilder {
        return copy(revealNetworkParametersHash = flag)
    }

    fun includeNotary(flag: Boolean = true): FilteredTransactionBuilder {
        return copy(revealNotary = flag)
    }

    fun build(): FilteredTransaction {
        val filter = object: Predicate<Any> {
            override fun test(element: Any): Boolean {
                val (component, id) = (element as? Pair<Any, Int>)
                        ?: throw IllegalArgumentException("Illegal argument type")
                return when (component) {
                    is TransactionState<ContractState> -> revealOutputState(component, id)
                    is NetworkParametersHash -> revealNetworkParametersHash
                    is Party /* notary is the only Party (!) */ -> revealNotary
                    else -> false
                }
            }
        }
        return FilteredTransaction.buildFilteredTransactionWithIndexedGroup(wtx, filter)
    }
}