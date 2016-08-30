package com.r3corda.client.model

import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.node.services.monitor.ServiceToClientEvent
import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.removeIf
import org.reactfx.EventStream

class StatesDiff<out T : ContractState>(val added: Collection<StateAndRef<T>>, val removed: Collection<StateRef>)

class ContractStateModel {
    private val serviceToClient: EventStream<ServiceToClientEvent> by stream(WalletMonitorModel::serviceToClient)
    private val outputStates = serviceToClient.filter(ServiceToClientEvent.OutputState::class.java)

    val contractStatesDiff = outputStates.map { StatesDiff(it.produced, it.consumed) }
    val cashStatesDiff = contractStatesDiff.map {
        StatesDiff(it.added.filterIsInstance<StateAndRef<Cash.State>>(), it.removed)
    }
    val cashStates: ObservableList<StateAndRef<Cash.State>> =
            cashStatesDiff.foldToObservableList(Unit) { statesDiff, _accumulator, observableList ->
                observableList.removeIf { it.ref in statesDiff.removed }
                observableList.addAll(statesDiff.added)
            }

}
