package net.corda.client.model

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.removeIf
import net.corda.client.fxutils.fold
import net.corda.client.fxutils.map
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.node.services.Vault
import rx.Observable

data class Diff<out T : ContractState>(
        val added: Collection<StateAndRef<T>>,
        val removed: Collection<StateRef>
)

/**
 * This model exposes the list of owned contract states.
 */
class ContractStateModel {
    private val vaultUpdates: Observable<Vault.Update> by observable(NodeMonitorModel::vaultUpdates)

    private val contractStatesDiff: Observable<Diff<ContractState>> = vaultUpdates.map {
        Diff(it.produced, it.consumed)
    }
    private val cashStatesDiff: Observable<Diff<Cash.State>> = contractStatesDiff.map {
        // We can't filter removed hashes here as we don't have type info
        Diff(it.added.filterCashStateAndRefs(), it.removed)
    }
    val cashStates: ObservableList<StateAndRef<Cash.State>> = cashStatesDiff.fold(FXCollections.observableArrayList()) { list, statesDiff ->
        list.removeIf { it.ref in statesDiff.removed }
        list.addAll(statesDiff.added)
        list
    }

    val cash = cashStates.map { it.state.data.amount }

    companion object {
        private fun Collection<StateAndRef<ContractState>>.filterCashStateAndRefs(): List<StateAndRef<Cash.State>> {
            return this.map { stateAndRef ->
                @Suppress("UNCHECKED_CAST")
                if (stateAndRef.state.data is Cash.State) {
                    // Kotlin doesn't unify here for some reason
                    stateAndRef as StateAndRef<Cash.State>
                } else {
                    null
                }
            }.filterNotNull()
        }
    }

}
