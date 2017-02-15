package net.corda.client.jfx.model

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import net.corda.client.jfx.utils.*
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.transactions.SignedTransaction
import org.fxmisc.easybind.EasyBind

data class GatheredTransactionData(
        val transaction: PartiallyResolvedTransaction,
        val stateMachines: ObservableList<out StateMachineData>
)

/**
 * [PartiallyResolvedTransaction] holds a [SignedTransaction] that has zero or more inputs resolved. The intent is
 * to prepare clients for cases where an input can only be resolved in the future/cannot be resolved at all (for example
 * because of permissioning)
 */
data class PartiallyResolvedTransaction(
        val transaction: SignedTransaction,
        val inputs: List<ObservableValue<InputResolution>>) {
    val id = transaction.id

    sealed class InputResolution {
        abstract val stateRef: StateRef

        data class Unresolved(override val stateRef: StateRef) : InputResolution()
        data class Resolved(val stateAndRef: StateAndRef<ContractState>) : InputResolution() {
            override val stateRef: StateRef get() = stateAndRef.ref
        }
    }

    companion object {
        fun fromSignedTransaction(
                transaction: SignedTransaction,
                transactions: ObservableMap<SecureHash, SignedTransaction>
        ) = PartiallyResolvedTransaction(
                transaction = transaction,
                inputs = transaction.tx.inputs.map { stateRef ->
                    EasyBind.map(transactions.getObservableValue(stateRef.txhash)) {
                        if (it == null) {
                            InputResolution.Unresolved(stateRef)
                        } else {
                            InputResolution.Resolved(it.tx.outRef(stateRef.index))
                        }
                    }
                }
        )
    }
}

data class FlowStatus(val status: String)
//todo after rebase
//class FlowStatus(
//        val status: String,
//        pt: ProgressTrackingEvent?,
//        val progress: FlowLogic.ProgressTrackerDisplayProxy = convert(pt)
//) {
//
//    companion object {
//        fun convert(pt: ProgressTrackingEvent?) : FlowLogic.ProgressTrackerDisplayProxy {
//            return FlowLogic.ProgressTrackerDisplayProxy(null, false, false)
///*                    pt?. == ProgressTracker.DONE,
//                    pt == null)*/
//        }
//    }
//}

sealed class StateMachineStatus {
    abstract val stateMachineName: String

    data class Added(override val stateMachineName: String) : StateMachineStatus()
    data class Removed(override val stateMachineName: String) : StateMachineStatus()
}

/*data class StateMachineData(
        val id: StateMachineRunId,
        val flowStatus: ObservableValue<FlowStatus?>,
        val stateMachineStatus: ObservableValue<StateMachineStatus>
)
*/

data class StateMachineData(
        val id: StateMachineRunId,
        val flowStatus: FlowStatus?,
        val stateMachineStatus: StateMachineStatus
)


/**
 * This model provides an observable list of transactions and what state machines/flows recorded them
 */
class TransactionDataModel {
    private val transactions by observable(NodeMonitorModel::transactions)
    private val stateMachineUpdates by observable(NodeMonitorModel::stateMachineUpdates)
    private val progressTracking by observable(NodeMonitorModel::progressTracking)
    private val stateMachineTransactionMapping by observable(NodeMonitorModel::stateMachineTransactionMapping)

    private val collectedTransactions = transactions.recordInSequence()
    private val transactionMap = collectedTransactions.associateBy(SignedTransaction::id)
    private val progressEvents = progressTracking.recordAsAssociation(ProgressTrackingEvent::stateMachineId)
    private val stateMachineStatus = stateMachineUpdates.fold(FXCollections.observableHashMap<StateMachineRunId, SimpleObjectProperty<StateMachineStatus>>()) { map, update ->
        when (update) {
            is StateMachineUpdate.Added -> {
                val added: SimpleObjectProperty<StateMachineStatus> =
                        SimpleObjectProperty(StateMachineStatus.Added(update.stateMachineInfo.flowLogicClassName))
                map[update.id] = added
            }
            is StateMachineUpdate.Removed -> {
                val added = map[update.id]
                added ?: throw Exception("State machine removed with unknown id ${update.id}")
                added.set(StateMachineStatus.Removed(added.value.stateMachineName))
            }
        }
    }

    val stateMachineDataList = LeftOuterJoinedMap(stateMachineStatus, progressEvents) {
        id, status, progress ->
        Bindings.createObjectBinding(
                { StateMachineData(id,
//                        FlowStatus(progress.value?.message!!, null),
                        progress.value?.message?.let{
                            FlowStatus(progress.value!!.message!!.toString(),progress.value)
                        },
                        status.get()) }
                , arrayOf(progress, status)
        )
    }.getObservableValues().flatten()

    /*
    private val stateMachineDataList = LeftOuterJoinedMap(stateMachineStatus, progressEvents) { id, status, progress ->
        StateMachineData(id, progress.map { it?.let { FlowStatus(it.message) } }, status)
    }.getObservableValues()*/

    // TODO : Create a new screen for state machines.
    private val stateMachineDataMap = stateMachineDataList.associateBy(StateMachineData::id)
    private val smTxMappingList = stateMachineTransactionMapping.recordInSequence()
    val partiallyResolvedTransactions = collectedTransactions.map {
        PartiallyResolvedTransaction.fromSignedTransaction(it, transactionMap)
    }
}


class StateMachineDataModel {
    private val transactions by observable(NodeMonitorModel::transactions)

    private val stateMachineUpdates by observable(NodeMonitorModel::stateMachineUpdates)
    private val progressTracking by observable(NodeMonitorModel::progressTracking)
    private val progressEvents = progressTracking.recordAsAssociation(ProgressTrackingEvent::stateMachineId)

    private val progressList = progressEvents.getObservableValues()

    private val stateMachineTransactionMapping by observable(NodeMonitorModel::stateMachineTransactionMapping)
    private val collectedTransactions = transactions.recordInSequence()
    private val transactionMap = collectedTransactions.associateBy(SignedTransaction::id)

    private val stateMachineStatus = stateMachineUpdates.fold(FXCollections.observableHashMap<StateMachineRunId, SimpleObjectProperty<StateMachineStatus>>()) { map, update ->
        println("**** $update")
        when (update) {
            is StateMachineUpdate.Added -> {
                val added: SimpleObjectProperty<StateMachineStatus> =
                        SimpleObjectProperty(StateMachineStatus.Added(update.stateMachineInfo.flowLogicClassName))
                map[update.id] = added
            }
            is StateMachineUpdate.Removed -> {
                val added = map[update.id]
                added ?: throw Exception("State machine removed with unknown id ${update.id}")
                added.set(StateMachineStatus.Removed(added.value.stateMachineName))
            }
        }
    }


    val stateMachineDataList = LeftOuterJoinedMap(stateMachineStatus, progressEvents) { id, status, progress ->
        Bindings.createObjectBinding({
            StateMachineData(id,
                    progress.value?.message?.let{
                        FlowStatus(progress.value!!.message!!.toString(),null)
                    },
//                    progress.value?.message?.let(::FlowStatus),
                    status.get())
        }, arrayOf(progress, status))
    }.getObservableValues().flatten()

    /*
    val stateMachineDataList = LeftOuterJoinedMap(stateMachineStatus, progressEvents) { id, status, progress ->
        StateMachineData(id, progress.map { it?.let { FlowStatus(it.message) } }, status)
    }.getObservableValues()*/

    // TODO : Create a new screen for state machines.
    private val stateMachineDataMap = stateMachineDataList.associateBy(StateMachineData::id)
    private val smTxMappingList = stateMachineTransactionMapping.recordInSequence()




    val partiallyResolvedTransactions = collectedTransactions.map {
        PartiallyResolvedTransaction.fromSignedTransaction(it, transactionMap)
    }
    val flowsInProgress = partiallyResolvedTransactions

/*
    data class SomeStructure(
            val someInt: ObservableValue<StateMachineStatus>,
            val otherData: ObservableValue<String>
    )
*/
    val progressObservableList = progressList.map{ struct -> struct.message  }

    fun asd() {
        progressEvents // .flatMap { it -> it.key}
        progressEvents.keys.map{ it ->  { if  (true) {null }else { it}}}
     //   val a1 = progressEvents.map{ struct -> struct.key. { if ( true) {  null } else {struct }} }
  //      val a: ObservableList<SomeStructure> = null!!
//        val x: ObservableList<SomeStructure> = ObservableList<SomeStructure>();
  //      val a3: ObservableList<SomeStructure> = listOf(Pair(1,"hey"))
    //    val x = a.map{ struct -> struct. { if ( it.javaClass == StateMachineStatus::Removed) { null} else {struct  }} }
    //    val b = a.map { struct -> struct.someInt.map { if (it.javaClass == StateMachineStatus::Removed) { null } else { struct } } }
     //   val c = b.flatten().filterNotNull()
        //return c
    }

}