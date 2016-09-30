package com.r3corda.client.model

import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.client.fxutils.recordInSequence
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.monitor.StateSnapshotMessage
import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.removeIf
import rx.Observable

sealed class StatesModification<out T : ContractState>{
    class Diff<out T : ContractState>(
            val added: Collection<StateAndRef<T>>,
            val removed: Collection<StateRef>
    ) : StatesModification<T>()
    class Reset<out T : ContractState>(val states: Collection<StateAndRef<T>>) : StatesModification<T>()
}

/**
 * This model exposes the list of owned contract states.
 */
class ContractStateModel {
    private val serviceToClient: Observable<ServiceToClientEvent> by observable(NodeMonitorModel::serviceToClient)
    private val snapshot: Observable<StateSnapshotMessage> by observable(NodeMonitorModel::snapshot)
    private val outputStates = serviceToClient.ofType(ServiceToClientEvent.OutputState::class.java)

    val contractStatesDiff: Observable<StatesModification.Diff<ContractState>> =
            outputStates.map { StatesModification.Diff(it.produced, it.consumed) }
    // We filter the diff first rather than the complete contract state list.
    val cashStatesModification: Observable<StatesModification<Cash.State>> = Observable.merge(
            arrayOf(
                    contractStatesDiff.map {
                        StatesModification.Diff(it.added.filterCashStateAndRefs(), it.removed)
                    },
                    snapshot.map {
                        StatesModification.Reset(it.contractStates.filterCashStateAndRefs())
                    }
            )
    )
    val cashStates: ObservableList<StateAndRef<Cash.State>> =
            cashStatesModification.foldToObservableList(Unit) { statesDiff, _accumulator, observableList ->
                when (statesDiff) {
                    is StatesModification.Diff -> {
                        observableList.removeIf { it.ref in statesDiff.removed }
                        observableList.addAll(statesDiff.added)
                    }
                    is StatesModification.Reset -> {
                        observableList.setAll(statesDiff.states)
                    }
                }
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
