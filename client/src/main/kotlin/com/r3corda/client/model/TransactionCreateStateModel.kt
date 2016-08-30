package com.r3corda.client.model

import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.monitor.TransactionBuildResult
import com.r3corda.node.utilities.AddOrRemove
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import rx.Observable
import java.time.Instant
import java.util.UUID

sealed class TransactionCreateStatus() {
    class Started(val message: String?) : TransactionCreateStatus()
    class Failed(val message: String?) : TransactionCreateStatus()

    override fun toString(): String {
        return when (this) {
            is TransactionCreateStatus.Started -> message ?: "Started"
            is TransactionCreateStatus.Failed -> message ?: "Failed"
        }
    }
}

sealed class ProtocolStatus() {
    object Added: ProtocolStatus()
    object Removed: ProtocolStatus()
    class InProgress(val status: String): ProtocolStatus()

    override fun toString(): String {
        return when (this) {
            ProtocolStatus.Added -> "Added"
            ProtocolStatus.Removed -> "Removed"
            is ProtocolStatus.InProgress -> status
        }
    }
}

interface TransactionCreateState {
    val fiberId: ObservableValue<Long?>
    val uuid: ObservableValue<UUID?>
    val protocolName: ObservableValue<String?>
    val protocolStatus: ObservableValue<ProtocolStatus?>
    val transaction: ObservableValue<SignedTransaction?>
    val status: ObservableValue<TransactionCreateStatus?>
    val lastUpdate: ObservableValue<Instant>
}

data class TransactionCreateStateWritable(
        override val fiberId: SimpleObjectProperty<Long?> = SimpleObjectProperty(null),
        override val uuid: SimpleObjectProperty<UUID?> = SimpleObjectProperty(null),
        override val protocolName: SimpleObjectProperty<String?> = SimpleObjectProperty(null),
        override val protocolStatus: SimpleObjectProperty<ProtocolStatus?> = SimpleObjectProperty(null),
        override val transaction: SimpleObjectProperty<SignedTransaction?> = SimpleObjectProperty(null),
        override val status: SimpleObjectProperty<TransactionCreateStatus?> = SimpleObjectProperty(null),
        override val lastUpdate: SimpleObjectProperty<Instant>
) : TransactionCreateState

/**
 * This model provides an observable list of states relating to the creation of a transaction not yet on ledger.
 */
class TransactionCreateStateModel {

    private val serviceToClient: Observable<ServiceToClientEvent> by observable(WalletMonitorModel::serviceToClient)

    /**
     * Aggregation of updates to transactions. We use the observable list as the only container and do linear search for
     * matching transactions because we have two keys(fiber ID and UUID) and this way it's easier to avoid syncing issues
     * TODO: Make this more efficient by maintaining and syncing two maps (for the two keys) in the accumulator
     * (Note that a transaction may be mapped by one or both)
     * TODO: Expose a writable stream to combine [serviceToClient] with to allow recording of transactions made locally(UUID)
     */
    val transactionCreateStates: ObservableList<out TransactionCreateState> =
            serviceToClient.foldToObservableList<ServiceToClientEvent, TransactionCreateStateWritable, Unit>(
                    initialAccumulator = Unit,
                    folderFun = { serviceToClientEvent, _unit, transactionStates ->
                        return@foldToObservableList when (serviceToClientEvent) {
                            is ServiceToClientEvent.Transaction -> {
                                // TODO handle this once we have some id to associate the tx with
                            }
                            is ServiceToClientEvent.OutputState -> {}
                            is ServiceToClientEvent.StateMachine -> {
                                newFiberIdTransactionStateOrModify(transactionStates,
                                        fiberId = serviceToClientEvent.fiberId,
                                        lastUpdate = serviceToClientEvent.time,
                                        tweak = {
                                            protocolName.set(serviceToClientEvent.label)
                                            protocolStatus.set(when (serviceToClientEvent.addOrRemove) {
                                                AddOrRemove.ADD -> ProtocolStatus.Added
                                                AddOrRemove.REMOVE -> ProtocolStatus.Removed
                                            })
                                        }
                                )
                            }
                            is ServiceToClientEvent.Progress -> {
                                newFiberIdTransactionStateOrModify(transactionStates,
                                        fiberId = serviceToClientEvent.fiberId,
                                        lastUpdate = serviceToClientEvent.time,
                                        tweak = {
                                            protocolStatus.set(ProtocolStatus.InProgress(serviceToClientEvent.message))
                                        }
                                )
                            }
                            is ServiceToClientEvent.TransactionBuild -> {
                                val state = serviceToClientEvent.state
                                newUuidTransactionStateOrModify(transactionStates,
                                        uuid = serviceToClientEvent.id,
                                        fiberId = when (state) {
                                            is TransactionBuildResult.ProtocolStarted -> state.fiberId
                                            is TransactionBuildResult.Failed -> null
                                        },
                                        lastUpdate = serviceToClientEvent.time,
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
        private fun newFiberIdTransactionStateOrModify(
                transactionStates: ObservableList<TransactionCreateStateWritable>,
                fiberId: Long,
                lastUpdate: Instant,
                tweak: TransactionCreateStateWritable.() -> Unit
        ) {
            val index = transactionStates.indexOfFirst { it.fiberId.value == fiberId }
            if (index < 0) {
                val newState = TransactionCreateStateWritable(
                        fiberId = SimpleObjectProperty(fiberId),
                        lastUpdate = SimpleObjectProperty(lastUpdate)
                )
                tweak(newState)
                transactionStates.add(newState)
            } else {
                val existingState = transactionStates[index]
                existingState.lastUpdate.set(lastUpdate)
                tweak(existingState)
            }
        }

        private fun newUuidTransactionStateOrModify(
                transactionStates: ObservableList<TransactionCreateStateWritable>,
                uuid: UUID,
                fiberId: Long?,
                lastUpdate: Instant,
                tweak: TransactionCreateStateWritable.() -> Unit
        ) {
            val index = transactionStates.indexOfFirst {
                it.uuid.value == uuid || (fiberId != null && it.fiberId.value == fiberId)
            }
            if (index < 0) {
                val newState = TransactionCreateStateWritable(
                        uuid = SimpleObjectProperty(uuid),
                        fiberId = SimpleObjectProperty(fiberId),
                        lastUpdate = SimpleObjectProperty(lastUpdate)
                )
                tweak(newState)
                transactionStates.add(newState)
            } else {
                val existingState = transactionStates[index]
                existingState.lastUpdate.set(lastUpdate)
                tweak(existingState)
            }
        }
    }

}
