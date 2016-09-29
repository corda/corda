package com.r3corda.client.model

import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.client.fxutils.recordInSequence
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.node.services.Vault
import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.removeIf
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

    val contractStatesDiff: Observable<Diff<ContractState>> = vaultUpdates.map {
        Diff(it.produced, it.consumed)
    }
    val cashStatesDiff: Observable<Diff<Cash.State>> = contractStatesDiff.map {
        // We can't filter removed hashes here as we don't have type info
        Diff(it.added.filterCashStateAndRefs(), it.removed)
    }
    val cashStates: ObservableList<StateAndRef<Cash.State>> =
            cashStatesDiff.foldToObservableList(Unit) { statesDiff, _accumulator, observableList ->
                observableList.removeIf { it.ref in statesDiff.removed }
                observableList.addAll(statesDiff.added)
            }


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
