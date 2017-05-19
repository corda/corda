package net.corda.client.jfx.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import net.corda.client.jfx.utils.LeftOuterJoinedMap
import net.corda.client.jfx.utils.fold
import net.corda.client.jfx.utils.getObservableValues
import net.corda.client.jfx.utils.lift
import net.corda.client.jfx.utils.recordAsAssociation
import net.corda.core.ErrorOr
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.StateMachineInfo
import net.corda.core.messaging.StateMachineUpdate
import org.fxmisc.easybind.EasyBind
import rx.Observable

data class ProgressTrackingEvent(val stateMachineId: StateMachineRunId, val message: String) {
    companion object {
        fun createStreamFromStateMachineInfo(stateMachine: StateMachineInfo): Observable<ProgressTrackingEvent>? {
            return stateMachine.progressTrackerStepAndUpdates?.let { (current, future) ->
                future.map { ProgressTrackingEvent(stateMachine.id, it) }.startWith(ProgressTrackingEvent(stateMachine.id, current))
            }
        }
    }
}

data class ProgressStatus(val status: String?)

sealed class StateMachineStatus {
    data class Added(val stateMachineName: String, val flowInitiator: FlowInitiator) : StateMachineStatus()
    data class Removed(val result: ErrorOr<*>) : StateMachineStatus()
}

data class StateMachineData(
        val id: StateMachineRunId,
        val stateMachineName: String,
        val flowInitiator: FlowInitiator,
        val smmStatus: Pair<ObservableValue<StateMachineStatus>, ObservableValue<ProgressStatus>>
)

data class Counter(
        var errored: SimpleIntegerProperty = SimpleIntegerProperty(0),
        var success: SimpleIntegerProperty = SimpleIntegerProperty(0),
        var progress: SimpleIntegerProperty = SimpleIntegerProperty(0)
) {
    fun addSmm() { progress.value += 1 }
    fun removeSmm(result: ErrorOr<*>) {
        progress.value -= 1
        when (result.error) {
            null -> success.value += 1
            else -> errored.value += 1
        }
    }
}

class StateMachineDataModel {
    private val stateMachineUpdates by observable(NodeMonitorModel::stateMachineUpdates)
    private val progressTracking by observable(NodeMonitorModel::progressTracking)
    private val progressEvents = progressTracking.recordAsAssociation(ProgressTrackingEvent::stateMachineId)

    val counter = Counter()

    private val stateMachineStatus = stateMachineUpdates.fold(FXCollections.observableHashMap<StateMachineRunId, SimpleObjectProperty<StateMachineStatus>>()) { map, update ->
        when (update) {
            is StateMachineUpdate.Added -> {
                counter.addSmm()
                val flowInitiator= update.stateMachineInfo.initiator
                val added: SimpleObjectProperty<StateMachineStatus> =
                        SimpleObjectProperty(StateMachineStatus.Added(update.stateMachineInfo.flowLogicClassName, flowInitiator))
                map[update.id] = added
            }
            is StateMachineUpdate.Removed -> {
                val added = map[update.id]
                added ?: throw Exception("State machine removed with unknown id ${update.id}")
                counter.removeSmm(update.result)
                added.set(StateMachineStatus.Removed(update.result))
            }
        }
    }

    private val stateMachineDataList = LeftOuterJoinedMap(stateMachineStatus, progressEvents) { id, status, progress ->
        val smStatus = status.value as StateMachineStatus.Added
        StateMachineData(id, smStatus.stateMachineName, smStatus.flowInitiator,
                Pair(status, EasyBind.map(progress) { ProgressStatus(it?.message) }))
    }.getObservableValues()

    val stateMachinesAll = stateMachineDataList
    val error = counter.errored
    val success = counter.success
    val progress = counter.progress
}
