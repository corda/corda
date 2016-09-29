package com.r3corda.client.model

import com.r3corda.client.CordaRPCClient
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.StateMachineTransactionMapping
import com.r3corda.core.node.services.Vault
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.node.services.messaging.ArtemisMessagingComponent
import com.r3corda.node.services.messaging.StateMachineInfo
import com.r3corda.node.services.messaging.StateMachineUpdate
import rx.Observable
import rx.subjects.PublishSubject
import java.nio.file.Path

data class ProgressTrackingEvent(val stateMachineId: StateMachineRunId, val message: String) {
    companion object {
        fun createStreamFromStateMachineInfo(stateMachine: StateMachineInfo): Observable<ProgressTrackingEvent>? {
            return stateMachine.progressTrackerStepAndUpdates?.let { pair ->
                val (current, future) = pair
                future.map { ProgressTrackingEvent(stateMachine.id, it) }.startWith(ProgressTrackingEvent(stateMachine.id, current))
            }
        }
    }
}

/**
 * This model exposes raw event streams to and from the node.
 */
class NodeMonitorModel {

    private val stateMachineUpdatesSubject = PublishSubject.create<StateMachineUpdate>()
    private val vaultUpdatesSubject = PublishSubject.create<Vault.Update>()
    private val transactionsSubject = PublishSubject.create<SignedTransaction>()
    private val stateMachineTransactionMappingSubject = PublishSubject.create<StateMachineTransactionMapping>()
    private val progressTrackingSubject = PublishSubject.create<ProgressTrackingEvent>()

    val stateMachineUpdates: Observable<StateMachineUpdate> = stateMachineUpdatesSubject
    val vaultUpdates: Observable<Vault.Update> = vaultUpdatesSubject
    val transactions: Observable<SignedTransaction> = transactionsSubject
    val stateMachineTransactionMapping: Observable<StateMachineTransactionMapping> = stateMachineTransactionMappingSubject
    val progressTracking: Observable<ProgressTrackingEvent> = progressTrackingSubject

    private val clientToServiceSource = PublishSubject.create<ClientToServiceCommand>()
    val clientToService: PublishSubject<ClientToServiceCommand> = clientToServiceSource

    /**
     * Register for updates to/from a given vault.
     * @param messagingService The messaging to use for communication.
     * @param monitorNodeInfo the [Node] to connect to.
     * TODO provide an unsubscribe mechanism
     */
    fun register(vaultMonitorNodeInfo: NodeInfo, certificatesPath: Path) {

        val client = CordaRPCClient(ArtemisMessagingComponent.toHostAndPort(vaultMonitorNodeInfo.address), certificatesPath)
        client.start()
        val proxy = client.proxy()

        val (stateMachines, stateMachineUpdates) = proxy.stateMachinesAndUpdates()
        // Extract the protocol tracking stream
        // TODO is there a nicer way of doing this? Stream of streams in general results in code like this...
        val currentProgressTrackerUpdates = stateMachines.mapNotNull { stateMachine ->
            ProgressTrackingEvent.createStreamFromStateMachineInfo(stateMachine)
        }
        val futureProgressTrackerUpdates = stateMachineUpdatesSubject.map { stateMachineUpdate ->
            if (stateMachineUpdate is StateMachineUpdate.Added) {
                ProgressTrackingEvent.createStreamFromStateMachineInfo(stateMachineUpdate.stateMachineInfo) ?: Observable.empty<ProgressTrackingEvent>()
            } else {
                Observable.empty<ProgressTrackingEvent>()
            }
        }
        futureProgressTrackerUpdates.startWith(currentProgressTrackerUpdates).flatMap { it }.subscribe(progressTrackingSubject)

        // Now the state machines
        val currentStateMachines = stateMachines.map { StateMachineUpdate.Added(it) }
        stateMachineUpdates.startWith(currentStateMachines).subscribe(stateMachineUpdatesSubject)

        // Vault updates
        val (vault, vaultUpdates) = proxy.vaultAndUpdates()
        val initialVaultUpdate = Vault.Update(setOf(), vault.toSet())
        vaultUpdates.startWith(initialVaultUpdate).subscribe(vaultUpdatesSubject)

        // Transactions
        val (transactions, newTransactions) = proxy.verifiedTransactions()
        newTransactions.startWith(transactions).subscribe(transactionsSubject)

        // SM -> TX mapping
        val (smTxMappings, futureSmTxMappings) = proxy.stateMachineRecordedTransactionMapping()
        futureSmTxMappings.startWith(smTxMappings).subscribe(stateMachineTransactionMappingSubject)

        // Client -> Service
        clientToServiceSource.subscribe {
            proxy.executeCommand(it)
        }
    }
}
