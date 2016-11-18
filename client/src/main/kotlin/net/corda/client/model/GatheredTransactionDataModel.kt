package net.corda.client.model

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import net.corda.client.fxutils.*
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
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

    sealed class InputResolution(val stateRef: StateRef) {
        class Unresolved(stateRef: StateRef) : InputResolution(stateRef)
        class Resolved(val stateAndRef: StateAndRef<ContractState>) : InputResolution(stateAndRef.ref)
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

sealed class TransactionCreateStatus(val message: String?) {
    class Started(message: String?) : TransactionCreateStatus(message)
    class Failed(message: String?) : TransactionCreateStatus(message)

    override fun toString(): String = message ?: javaClass.simpleName
}

data class FlowStatus(
        val status: String
)

sealed class StateMachineStatus(val stateMachineName: String) {
    class Added(stateMachineName: String) : StateMachineStatus(stateMachineName)
    class Removed(stateMachineName: String) : StateMachineStatus(stateMachineName)

    override fun toString(): String = "${javaClass.simpleName}($stateMachineName)"
}

data class StateMachineData(
        val id: StateMachineRunId,
        val flowStatus: ObservableValue<FlowStatus?>,
        val stateMachineStatus: ObservableValue<StateMachineStatus>
)

/**
 * This model provides an observable list of transactions and what state machines/flows recorded them
 */
class GatheredTransactionDataModel {
    private val transactions by observable(NodeMonitorModel::transactions)
    private val stateMachineUpdates by observable(NodeMonitorModel::stateMachineUpdates)
    private val progressTracking by observable(NodeMonitorModel::progressTracking)
    private val stateMachineTransactionMapping by observable(NodeMonitorModel::stateMachineTransactionMapping)

    private val collectedTransactions = transactions.recordInSequence()
    private val transactionMap = collectedTransactions.associateBy(SignedTransaction::id)
    private val progressEvents = progressTracking.recordAsAssociation(ProgressTrackingEvent::stateMachineId)
    private val stateMachineStatus = stateMachineUpdates.foldToObservableMap(Unit) { update, _unit, map: ObservableMap<StateMachineRunId, SimpleObjectProperty<StateMachineStatus>> ->
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
    private val stateMachineDataList = LeftOuterJoinedMap(stateMachineStatus, progressEvents) { id, status, progress ->
        StateMachineData(id, progress.map { it?.let { FlowStatus(it.message) } }, status)
    }.getObservableValues()
    // TODO : Create a new screen for state machines.
    private val stateMachineDataMap = stateMachineDataList.associateBy(StateMachineData::id)
    private val smTxMappingList = stateMachineTransactionMapping.recordInSequence()
    val partiallyResolvedTransactions = collectedTransactions.map {
        PartiallyResolvedTransaction.fromSignedTransaction(it, transactionMap)
    }
}
