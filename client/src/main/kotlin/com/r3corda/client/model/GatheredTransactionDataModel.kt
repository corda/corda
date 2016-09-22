package com.r3corda.client.model

import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.transactions.LedgerTransaction
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.monitor.TransactionBuildResult
import com.r3corda.node.utilities.AddOrRemove
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.jetbrains.exposed.sql.transactions.transaction
import rx.Observable
import java.time.Instant
import java.util.UUID

interface GatheredTransactionData {
    val stateMachineRunId: ObservableValue<StateMachineRunId?>
    val uuid: ObservableValue<UUID?>
    val protocolStatus: ObservableValue<ProtocolStatus?>
    val stateMachineStatus: ObservableValue<StateMachineStatus?>
    val transaction: ObservableValue<LedgerTransaction?>
    val status: ObservableValue<TransactionCreateStatus?>
    val lastUpdate: ObservableValue<Instant>
    val allEvents: ObservableList<out ServiceToClientEvent>
}

sealed class TransactionCreateStatus(val message: String?) {
    class Started(message: String?) : TransactionCreateStatus(message)
    class Failed(message: String?) : TransactionCreateStatus(message)
    override fun toString(): String = message ?: javaClass.simpleName
}

data class ProtocolStatus(
        val status: String
)
sealed class StateMachineStatus(val stateMachineName: String) {
    class Added(stateMachineName: String): StateMachineStatus(stateMachineName)
    class Removed(stateMachineName: String): StateMachineStatus(stateMachineName)
    override fun toString(): String = "${javaClass.simpleName}($stateMachineName)"
}

data class GatheredTransactionDataWritable(
        override val stateMachineRunId: SimpleObjectProperty<StateMachineRunId?> = SimpleObjectProperty(null),
        override val uuid: SimpleObjectProperty<UUID?> = SimpleObjectProperty(null),
        override val stateMachineStatus: SimpleObjectProperty<StateMachineStatus?> = SimpleObjectProperty(null),
        override val protocolStatus: SimpleObjectProperty<ProtocolStatus?> = SimpleObjectProperty(null),
        override val transaction: SimpleObjectProperty<LedgerTransaction?> = SimpleObjectProperty(null),
        override val status: SimpleObjectProperty<TransactionCreateStatus?> = SimpleObjectProperty(null),
        override val lastUpdate: SimpleObjectProperty<Instant>,
        override val allEvents: ObservableList<ServiceToClientEvent> = FXCollections.observableArrayList()
) : GatheredTransactionData

/**
 * This model provides an observable list of states relating to the creation of a transaction not yet on ledger.
 */
class GatheredTransactionDataModel {

    private val serviceToClient: Observable<ServiceToClientEvent> by observable(NodeMonitorModel::serviceToClient)

    /**
     * Aggregation of updates to transactions. We use the observable list as the only container and do linear search for
     * matching transactions because we have three keys(fiber ID, UUID, tx id) and this way it's easier to avoid syncing issues.
     *
     * The Fiber ID is used to identify events that relate to the same transaction server-side, whereas the UUID is
     * generated on the UI and is used to identify events with the UI action that triggered them. Currently a UUID is
     * generated for each outgoing [ClientToServiceCommand].
     *
     * TODO: Make this more efficient by maintaining and syncing two maps (for the two keys) in the accumulator
     * (Note that a transaction may be mapped by one or both)
     * TODO: Expose a writable stream to combine [serviceToClient] with to allow recording of transactions made locally(UUID)
     */
    val gatheredTransactionDataList: ObservableList<out GatheredTransactionData> =
            serviceToClient.foldToObservableList<ServiceToClientEvent, GatheredTransactionDataWritable, Unit>(
                    initialAccumulator = Unit,
                    folderFun = { serviceToClientEvent, _unit, transactionStates ->
                        return@foldToObservableList when (serviceToClientEvent) {
                            is ServiceToClientEvent.Transaction -> {
                                newTransactionIdTransactionStateOrModify(transactionStates, serviceToClientEvent,
                                        transaction = serviceToClientEvent.transaction,
                                        tweak = {}
                                )
                            }
                            is ServiceToClientEvent.OutputState -> {}
                            is ServiceToClientEvent.StateMachine -> {
                                newFiberIdTransactionStateOrModify(transactionStates, serviceToClientEvent,
                                        stateMachineRunId = serviceToClientEvent.id,
                                        tweak = {
                                            stateMachineStatus.set(when (serviceToClientEvent.addOrRemove) {
                                                AddOrRemove.ADD -> StateMachineStatus.Added(serviceToClientEvent.label)
                                                AddOrRemove.REMOVE -> StateMachineStatus.Removed(serviceToClientEvent.label)
                                            })
                                        }
                                )
                            }
                            is ServiceToClientEvent.Progress -> {
                                newFiberIdTransactionStateOrModify(transactionStates, serviceToClientEvent,
                                        stateMachineRunId = serviceToClientEvent.id,
                                        tweak = {
                                            protocolStatus.set(ProtocolStatus(serviceToClientEvent.message))
                                        }
                                )
                            }
                            is ServiceToClientEvent.TransactionBuild -> {
                                val state = serviceToClientEvent.state
                                newUuidTransactionStateOrModify(transactionStates, serviceToClientEvent,
                                        uuid = serviceToClientEvent.id,
                                        stateMachineRunId = when (state) {
                                            is TransactionBuildResult.ProtocolStarted -> state.id
                                            is TransactionBuildResult.Failed -> null
                                        },
                                        transactionId = when (state) {
                                            is TransactionBuildResult.ProtocolStarted -> state.transaction?.id
                                            is TransactionBuildResult.Failed -> null
                                        },
                                        tweak = {
                                            return@newUuidTransactionStateOrModify when (state) {
                                                is TransactionBuildResult.ProtocolStarted -> {
                                                    transaction.set(state.transaction)
                                                    status.set(TransactionCreateStatus.Started(state.message))
                                                }
                                                is TransactionBuildResult.Failed -> {
                                                    status.set(TransactionCreateStatus.Failed(state.message))
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
            )

    companion object {

        private fun newTransactionIdTransactionStateOrModify(
                transactionStates: ObservableList<GatheredTransactionDataWritable>,
                event: ServiceToClientEvent,
                transaction: LedgerTransaction,
                tweak: GatheredTransactionDataWritable.() -> Unit
        ) {
            val index = transactionStates.indexOfFirst { transaction.id == it.transaction.value?.id  }
            val state = if (index < 0) {
                val newState = GatheredTransactionDataWritable(
                        transaction = SimpleObjectProperty(transaction),
                        lastUpdate = SimpleObjectProperty(event.time)
                )
                tweak(newState)
                transactionStates.add(newState)
                newState
            } else {
                val existingState = transactionStates[index]
                existingState.lastUpdate.set(event.time)
                tweak(existingState)
                existingState
            }
            state.allEvents.add(event)
        }

        private fun newFiberIdTransactionStateOrModify(
                transactionStates: ObservableList<GatheredTransactionDataWritable>,
                event: ServiceToClientEvent,
                stateMachineRunId: StateMachineRunId,
                tweak: GatheredTransactionDataWritable.() -> Unit
        ) {
            val index = transactionStates.indexOfFirst { it.stateMachineRunId.value == stateMachineRunId }
            val state = if (index < 0) {
                val newState = GatheredTransactionDataWritable(
                        stateMachineRunId = SimpleObjectProperty(stateMachineRunId),
                        lastUpdate = SimpleObjectProperty(event.time)
                )
                tweak(newState)
                transactionStates.add(newState)
                newState
            } else {
                val existingState = transactionStates[index]
                existingState.lastUpdate.set(event.time)
                tweak(existingState)
                existingState
            }
            state.allEvents.add(event)
        }

        private fun newUuidTransactionStateOrModify(
                transactionStates: ObservableList<GatheredTransactionDataWritable>,
                event: ServiceToClientEvent,
                uuid: UUID,
                stateMachineRunId: StateMachineRunId?,
                transactionId: SecureHash?,
                tweak: GatheredTransactionDataWritable.() -> Unit
        ) {
            val index = transactionStates.indexOfFirst {
                it.uuid.value == uuid ||
                        (stateMachineRunId != null && it.stateMachineRunId.value == stateMachineRunId) ||
                        (transactionId != null && it.transaction.value?.id == transactionId)
            }
            val state = if (index < 0) {
                val newState = GatheredTransactionDataWritable(
                        uuid = SimpleObjectProperty(uuid),
                        stateMachineRunId = SimpleObjectProperty(stateMachineRunId),
                        lastUpdate = SimpleObjectProperty(event.time)
                )
                tweak(newState)
                transactionStates.add(newState)
                newState
            } else {
                val existingState = transactionStates[index]
                existingState.lastUpdate.set(event.time)
                tweak(existingState)
                existingState
            }
            state.allEvents.add(event)
        }
    }

}
