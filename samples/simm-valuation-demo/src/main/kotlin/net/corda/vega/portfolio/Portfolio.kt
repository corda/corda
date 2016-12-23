package net.corda.vega.portfolio

import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.ServiceHub
import net.corda.core.sum
import net.corda.vega.contracts.IRSState
import net.corda.vega.contracts.SwapData
import java.time.LocalDate

/**
 * This class is just a list of trades that exist with a certain other party.
 * In reality, portfolios are constructed under the tree of corporation, legal entity, division, desk, book etc etc
 * but for this example we just create our portfolio of "all trades against another counterparty" (ie node)
 *
 * @param valuationDate can be null for transient portfolio objects
 */
data class Portfolio(private val tradeStateAndRefs: List<StateAndRef<IRSState>>, val valuationDate: LocalDate? = null) {
    val trades: List<IRSState> by lazy { tradeStateAndRefs.map { it.state.data } }
    val swaps: List<SwapData> by lazy { trades.map { it.swap } }
    val refs: List<StateRef> by lazy { tradeStateAndRefs.map { it.ref } }

    fun getNotionalForParty(party: Party) = trades.map { it.swap.getLegForParty(party).notional }.sum()

    fun update(curTrades: List<StateAndRef<IRSState>>): Portfolio {
        return copy(tradeStateAndRefs = curTrades)
    }
}

fun List<StateAndRef<IRSState>>.toPortfolio(): Portfolio {
    return Portfolio(this)
}

inline fun <reified T : ContractState> List<StateRef>.toStateAndRef(rpc: CordaRPCOps): List<StateAndRef<T>> {
    val stateRefs = rpc.vaultAndUpdates().first.associateBy { it.ref }
    return mapNotNull { stateRefs[it] }.filterStatesOfType<T>()
}

// TODO: This should probably have its generics fixed and moved into the core platform API.
@Suppress("UNCHECKED_CAST")
fun <T : ContractState> List<StateRef>.toStateAndRef(services: ServiceHub): List<StateAndRef<T>> {
    return services.vaultService.statesForRefs(this).map {
        StateAndRef(it.value as TransactionState<T>, it.key)
    }
}
