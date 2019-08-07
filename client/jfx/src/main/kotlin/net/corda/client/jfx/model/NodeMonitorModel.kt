package net.corda.client.jfx.model

import javafx.beans.property.SimpleObjectProperty
import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.contracts.ContractState
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.messaging.*
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import rx.Observable
import rx.subjects.PublishSubject

data class ProgressTrackingEvent(val stateMachineId: StateMachineRunId, val message: String) {
    companion object {
        fun createStreamFromStateMachineInfo(stateMachine: StateMachineInfo): Observable<ProgressTrackingEvent>? {
            return stateMachine.progressTrackerStepAndUpdates?.let { (current, future) ->
                future.map { ProgressTrackingEvent(stateMachine.id, it) }.startWith(ProgressTrackingEvent(stateMachine.id, current))
            }
        }
    }
}

/**
 * This model exposes raw event streams to and from the node.
 */
class NodeMonitorModel : AutoCloseable {

    private val stateMachineUpdatesSubject = PublishSubject.create<StateMachineUpdate>()
    private val vaultUpdatesSubject = PublishSubject.create<Vault.Update<ContractState>>()
    private val transactionsSubject = PublishSubject.create<SignedTransaction>()
    private val stateMachineTransactionMappingSubject = PublishSubject.create<StateMachineTransactionMapping>()
    private val progressTrackingSubject = PublishSubject.create<ProgressTrackingEvent>()
    private val networkMapSubject = PublishSubject.create<MapChange>()

    val stateMachineUpdates: Observable<StateMachineUpdate> = stateMachineUpdatesSubject
    val vaultUpdates: Observable<Vault.Update<ContractState>> = vaultUpdatesSubject
    val transactions: Observable<SignedTransaction> = transactionsSubject
    val stateMachineTransactionMapping: Observable<StateMachineTransactionMapping> = stateMachineTransactionMappingSubject
    val progressTracking: Observable<ProgressTrackingEvent> = progressTrackingSubject
    val networkMap: Observable<MapChange> = networkMapSubject

    private lateinit var rpc: CordaRPCOps
    val proxyObservable = SimpleObjectProperty<CordaRPCOps>()
    lateinit var notaryIdentities: List<Party>

    companion object {
        val logger = contextLogger()
    }

    /**
     * Disconnects from the Corda node for a clean client shutdown.
     */
    override fun close() {
        try {
            (rpc as ReconnectingCordaRPCOps).close()
        } catch (e: Exception) {
            logger.error("Error closing RPC connection to node", e)
        }
    }

    /**
     * Register for updates to/from a given vault.
     * TODO provide an unsubscribe mechanism
     */
    fun register(nodeHostAndPort: NetworkHostAndPort, username: String, password: String) {
        rpc = ReconnectingCordaRPCOps(nodeHostAndPort, username, password)

        proxyObservable.value = rpc

        // Vault snapshot (force single page load with MAX_PAGE_SIZE) + updates
        val (statesSnapshot, vaultUpdates) = rpc.vaultTrackBy<ContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL),
                PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE))
        val unconsumedStates = statesSnapshot.states.filterIndexed { index, _ ->
            statesSnapshot.statesMetadata[index].status == Vault.StateStatus.UNCONSUMED
        }.toSet()
        val consumedStates = statesSnapshot.states.toSet() - unconsumedStates
        val initialVaultUpdate = Vault.Update(consumedStates, unconsumedStates, references = emptySet())
        vaultUpdates.startWith(initialVaultUpdate).subscribe(vaultUpdatesSubject::onNext)

        // Transactions
        val (transactions, newTransactions) = rpc.internalVerifiedTransactionsFeed()
        newTransactions.startWith(transactions).subscribe(transactionsSubject::onNext)

        // SM -> TX mapping
        val (smTxMappings, futureSmTxMappings) = rpc.stateMachineRecordedTransactionMappingFeed()
        futureSmTxMappings.startWith(smTxMappings).subscribe(stateMachineTransactionMappingSubject::onNext)

        // Parties on network
        val (parties, futurePartyUpdate) = rpc.networkMapFeed()
        futurePartyUpdate.startWith(parties.map(MapChange::Added)).subscribe(networkMapSubject::onNext)

        val stateMachines = rpc.stateMachinesSnapshot()

        notaryIdentities = rpc.notaryIdentities()

        // Extract the flow tracking stream
        // TODO is there a nicer way of doing this? Stream of streams in general results in code like this...
        // TODO `progressTrackingSubject` doesn't seem to be used anymore - should it be removed?
        val currentProgressTrackerUpdates = stateMachines.mapNotNull { stateMachine ->
            ProgressTrackingEvent.createStreamFromStateMachineInfo(stateMachine)
        }
        val futureProgressTrackerUpdates = stateMachineUpdatesSubject.map { stateMachineUpdate ->
            if (stateMachineUpdate is StateMachineUpdate.Added) {
                ProgressTrackingEvent.createStreamFromStateMachineInfo(stateMachineUpdate.stateMachineInfo)
                        ?: Observable.empty<ProgressTrackingEvent>()
            } else {
                Observable.empty<ProgressTrackingEvent>()
            }
        }

        // We need to retry, because when flow errors, we unsubscribe from progressTrackingSubject. So we end up with stream of state machine updates and no progress trackers.
        futureProgressTrackerUpdates.startWith(currentProgressTrackerUpdates).flatMap { it }.retry().subscribe(progressTrackingSubject)
    }
}