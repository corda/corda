package com.r3corda.client.model

import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.client.fxutils.getObservableValue
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.client.fxutils.recordInSequence
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.monitor.TransactionBuildResult
import com.r3corda.node.utilities.AddOrRemove
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import org.fxmisc.easybind.EasyBind
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import rx.Observable
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KProperty1

interface GatheredTransactionData {
    val stateMachineRunId: ObservableValue<StateMachineRunId?>
    val uuid: ObservableValue<UUID?>
    val protocolStatus: ObservableValue<ProtocolStatus?>
    val stateMachineStatus: ObservableValue<StateMachineStatus?>
    val transaction: ObservableValue<PartiallyResolvedTransaction?>
    val status: ObservableValue<TransactionCreateStatus?>
    val lastUpdate: ObservableValue<Instant>
    val allEvents: ObservableList<out ServiceToClientEvent>
}

/**
 * [PartiallyResolvedTransaction] holds a [SignedTransaction] that has zero or more inputs resolved. The intent is
 * to prepare clients for cases where an input can only be resolved in the future/cannot be resolved at all (for example
 * because of permissioning)
 */
data class PartiallyResolvedTransaction(
        val transaction: SignedTransaction,
        val inputs: List<ObservableValue<InputResolution>>
) {
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
        override val transaction: SimpleObjectProperty<PartiallyResolvedTransaction?> = SimpleObjectProperty(null),
        override val status: SimpleObjectProperty<TransactionCreateStatus?> = SimpleObjectProperty(null),
        override val lastUpdate: SimpleObjectProperty<Instant>,
        override val allEvents: ObservableList<ServiceToClientEvent> = FXCollections.observableArrayList()
) : GatheredTransactionData

private val log = LoggerFactory.getLogger(GatheredTransactionDataModel::class.java)

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
            serviceToClient.foldToObservableList<ServiceToClientEvent, GatheredTransactionDataWritable, ObservableMap<SecureHash, SignedTransaction>>(
                    initialAccumulator = FXCollections.observableHashMap<SecureHash, SignedTransaction>(),
                    folderFun = { serviceToClientEvent, transactions, transactionStates ->
                        val _unit = when (serviceToClientEvent) {
                            is ServiceToClientEvent.Transaction -> {
                                transactions.set(serviceToClientEvent.transaction.id, serviceToClientEvent.transaction)
                                val somewhatResolvedTransaction = PartiallyResolvedTransaction.fromSignedTransaction(
                                        serviceToClientEvent.transaction,
                                        transactions
                                )
                                newTransactionIdTransactionStateOrModify(transactionStates, serviceToClientEvent,
                                        transaction = somewhatResolvedTransaction,
                                        tweak = {}
                                )
                            }
                            is ServiceToClientEvent.OutputState -> {
                            }
                            is ServiceToClientEvent.StateMachine -> {
                                newFiberIdTransactionStateOrModify(transactionStates, serviceToClientEvent,
                                        stateMachineRunId = serviceToClientEvent.id,
                                        tweak = {
                                            stateMachineStatus.set(when (serviceToClientEvent.addOrRemove) {
                                                AddOrRemove.ADD -> StateMachineStatus.Added(serviceToClientEvent.label)
                                                AddOrRemove.REMOVE -> {
                                                    val currentStatus = stateMachineStatus.value
                                                    if (currentStatus is StateMachineStatus.Added) {
                                                        StateMachineStatus.Removed(currentStatus.stateMachineName)
                                                    } else {
                                                        StateMachineStatus.Removed(serviceToClientEvent.label)
                                                    }
                                                }
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

                                when (state) {
                                    is TransactionBuildResult.ProtocolStarted -> {
                                        state.transaction?.let {
                                            transactions.set(it.id, it)
                                        }
                                    }
                                }

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
                                                    state.transaction?.let {
                                                        transaction.set(PartiallyResolvedTransaction.fromSignedTransaction(it, transactions))
                                                    }
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
                        transactions
                    }
            )

    companion object {

        private fun newTransactionIdTransactionStateOrModify(
                transactionStates: ObservableList<GatheredTransactionDataWritable>,
                event: ServiceToClientEvent,
                transaction: PartiallyResolvedTransaction,
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
            val matchingStates = transactionStates.filtered {
                it.uuid.value == uuid ||
                        (stateMachineRunId != null && it.stateMachineRunId.value == stateMachineRunId) ||
                        (transactionId != null && it.transaction.value?.transaction?.id == transactionId)
            }
            val mergedState = mergeGatheredData(matchingStates)
            for (i in 0 .. matchingStates.size - 1) {
                transactionStates.removeAt(matchingStates.getSourceIndex(i))
            }
            val state = if (mergedState == null) {
                val newState = GatheredTransactionDataWritable(
                        uuid = SimpleObjectProperty(uuid),
                        stateMachineRunId = SimpleObjectProperty(stateMachineRunId),
                        lastUpdate = SimpleObjectProperty(event.time)
                )
                transactionStates.add(newState)
                newState
            } else {
                mergedState.lastUpdate.set(event.time)
                mergedState
            }
            tweak(state)
            state.allEvents.add(event)
        }

        private fun mergeGatheredData(
                gatheredDataList: List<GatheredTransactionDataWritable>
        ): GatheredTransactionDataWritable? {
            var gathered: GatheredTransactionDataWritable? = null
            // Modify the last one if we can
            gatheredDataList.asReversed().forEach {
                val localGathered = gathered
                if (localGathered == null) {
                    gathered = it
                } else {
                    mergeField(it, localGathered, GatheredTransactionDataWritable::stateMachineRunId)
                    mergeField(it, localGathered, GatheredTransactionDataWritable::uuid)
                    mergeField(it, localGathered, GatheredTransactionDataWritable::stateMachineStatus)
                    mergeField(it, localGathered, GatheredTransactionDataWritable::protocolStatus)
                    mergeField(it, localGathered, GatheredTransactionDataWritable::transaction)
                    mergeField(it, localGathered, GatheredTransactionDataWritable::status)
                    localGathered.allEvents.addAll(it.allEvents)
                }
            }
            return gathered
        }

        private fun <A> mergeField(
                from: GatheredTransactionDataWritable,
                to: GatheredTransactionDataWritable,
                field: KProperty1<GatheredTransactionDataWritable, SimpleObjectProperty<A?>>) {
            val fromValue = field(from).value
            if (fromValue != null) {
                val toField = field(to)
                val toValue = toField.value
                if (toValue != null && fromValue != toValue) {
                    log.warn("Conflicting data for field ${field.name}: $fromValue vs $toValue")
                }
                toField.set(fromValue)
            }
        }
    }

}
