package com.r3corda.client.model

import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.monitor.TransactionBuildResult
import com.r3corda.node.utilities.AddOrRemove
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import rx.Observable
import java.time.Instant
import java.util.UUID

interface GatheredTransactionData {
    val fiberId: ObservableValue<Long?>
    val uuid: ObservableValue<UUID?>
    val protocolName: ObservableValue<String?>
    val protocolStatus: ObservableValue<ProtocolStatus?>
    val transaction: ObservableValue<SignedTransaction?>
    val status: ObservableValue<TransactionCreateStatus?>
    val lastUpdate: ObservableValue<Instant>
}

sealed class TransactionCreateStatus(val message: String?) {
    class Started(message: String?) : TransactionCreateStatus(message)
    class Failed(message: String?) : TransactionCreateStatus(message)
    override fun toString(): String = message ?: javaClass.simpleName
}

sealed class ProtocolStatus(val status: String?) {
    object Added: ProtocolStatus(null)
    object Removed: ProtocolStatus(null)
    class InProgress(status: String): ProtocolStatus(status)
    override fun toString(): String = status ?: javaClass.simpleName
}

data class GatheredTransactionDataWritable(
        override val fiberId: SimpleObjectProperty<Long?> = SimpleObjectProperty(null),
        override val uuid: SimpleObjectProperty<UUID?> = SimpleObjectProperty(null),
        override val protocolName: SimpleObjectProperty<String?> = SimpleObjectProperty(null),
        override val protocolStatus: SimpleObjectProperty<ProtocolStatus?> = SimpleObjectProperty(null),
        override val transaction: SimpleObjectProperty<SignedTransaction?> = SimpleObjectProperty(null),
        override val status: SimpleObjectProperty<TransactionCreateStatus?> = SimpleObjectProperty(null),
        override val lastUpdate: SimpleObjectProperty<Instant>
) : GatheredTransactionData

/**
 * This model provides an observable list of states relating to the creation of a transaction not yet on ledger.
 */
class GatheredTransactionDataModel {

    private val serviceToClient: Observable<ServiceToClientEvent> by observable(WalletMonitorModel::serviceToClient)

    /**
     * Aggregation of updates to transactions. We use the observable list as the only container and do linear search for
     * matching transactions because we have two keys(fiber ID and UUID) and this way it's easier to avoid syncing issues.
     *
     * The Fiber ID is used to identify events that relate to the same transaction server-side, whereas the UUID is
     * generated on the UI and is used to identify events with the UI action that triggered them. Currently a UUID is
     * generated for each outgoing [ClientToServiceCommand].
     *
     * TODO: Make this more efficient by maintaining and syncing two maps (for the two keys) in the accumulator
     * (Note that a transaction may be mapped by one or both)
     * TODO: Expose a writable stream to combine [serviceToClient] with to allow recording of transactions made locally(UUID)
     */
    val gatheredGatheredTransactionDataList: ObservableList<out GatheredTransactionData> =
            serviceToClient.foldToObservableList<ServiceToClientEvent, GatheredTransactionDataWritable, Unit>(
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
                transactionStates: ObservableList<GatheredTransactionDataWritable>,
                fiberId: Long,
                lastUpdate: Instant,
                tweak: GatheredTransactionDataWritable.() -> Unit
        ) {
            val index = transactionStates.indexOfFirst { it.fiberId.value == fiberId }
            if (index < 0) {
                val newState = GatheredTransactionDataWritable(
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
                transactionStates: ObservableList<GatheredTransactionDataWritable>,
                uuid: UUID,
                fiberId: Long?,
                lastUpdate: Instant,
                tweak: GatheredTransactionDataWritable.() -> Unit
        ) {
            val index = transactionStates.indexOfFirst {
                it.uuid.value == uuid || (fiberId != null && it.fiberId.value == fiberId)
            }
            if (index < 0) {
                val newState = GatheredTransactionDataWritable(
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
