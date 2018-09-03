package net.corda.client.jfx.model

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.distinctBy
import net.corda.client.jfx.utils.fold
import net.corda.client.jfx.utils.map
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.Vault
import net.corda.finance.contracts.asset.Cash
import rx.Observable

data class Diff<out T : ContractState>(
        val added: Collection<StateAndRef<T>>,
        val removed: Collection<StateAndRef<T>>
)

/**
 * This model exposes the list of owned contract states.
 */
class ContractStateModel {
    private val vaultUpdates: Observable<Vault.Update<ContractState>> by observable(NodeMonitorModel::vaultUpdates)

    private val contractStatesDiff: Observable<Diff<ContractState>> = vaultUpdates.map {
        Diff(it.produced, it.consumed)
    }
    private val cashStatesDiff: Observable<Diff<Cash.State>> = contractStatesDiff.map {
        Diff(it.added.filterCashStateAndRefs(), it.removed.filterCashStateAndRefs())
    }
    val cashStates: ObservableList<StateAndRef<Cash.State>> = cashStatesDiff.fold(FXCollections.observableArrayList()) { list: MutableList<StateAndRef<Cash.State>>, (added, removed) ->
        list.removeIf { it in removed }
        list.addAll(added)
    }.distinctBy { it.ref }

    val cash = cashStates.map { it.state.data.amount }

    companion object {
        private fun Collection<StateAndRef<ContractState>>.filterCashStateAndRefs(): List<StateAndRef<Cash.State>> {
            return this.mapNotNull { stateAndRef ->
                if (stateAndRef.state.data is Cash.State) {
                    // Kotlin doesn't unify here for some reason
                    uncheckedCast<StateAndRef<ContractState>, StateAndRef<Cash.State>>(stateAndRef)
                } else {
                    null
                }
            }
        }
    }
}
