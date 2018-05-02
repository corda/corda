package net.corda.client.jfx.model

import javafx.beans.property.SimpleObjectProperty
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
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
import net.corda.core.utilities.seconds
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
class NodeMonitorModel {

    private val retryableStateMachineUpdatesSubject = PublishSubject.create<StateMachineUpdate>()
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

    val proxyObservable = SimpleObjectProperty<CordaRPCOpsWrapper?>()
    lateinit var notaryIdentities: List<Party>

    companion object {
        val logger = contextLogger()
    }

    /**
     * This is needed as JavaFX listener framework attempts to call `equals()` before dispatching notification change.
     * And calling `CordaRPCOps.equals()` results in (unhandled) remote call.
     */
    class CordaRPCOpsWrapper(val cordaRPCOps: CordaRPCOps) {
        override fun equals(other: Any?): Boolean {
            return this === other
        }

        override fun hashCode(): Int {
            throw IllegalArgumentException()
        }
    }

    /**
     * Register for updates to/from a given vault.
     * TODO provide an unsubscribe mechanism
     */
    fun register(nodeHostAndPort: NetworkHostAndPort, username: String, password: String) {

        // `retryableStateMachineUpdatesSubject` will change it's upstream subscriber in case of RPC connection failure, this `Observable` should
        // never produce an error.
        // `stateMachineUpdatesSubject` will stay firmly subscribed to `retryableStateMachineUpdatesSubject`
        retryableStateMachineUpdatesSubject.subscribe(stateMachineUpdatesSubject)

        // Proxy may change during re-connect, ensure that subject wiring accurately reacts to this activity.
        proxyObservable.addListener { _, _, wrapper ->
            if(wrapper != null) {
                val proxy = wrapper.cordaRPCOps
                // Vault snapshot (force single page load with MAX_PAGE_SIZE) + updates
                val (statesSnapshot, vaultUpdates) = proxy.vaultTrackBy<ContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL),
                        PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE))
                val unconsumedStates = statesSnapshot.states.filterIndexed { index, _ ->
                    statesSnapshot.statesMetadata[index].status == Vault.StateStatus.UNCONSUMED
                }.toSet()
                val consumedStates = statesSnapshot.states.toSet() - unconsumedStates
                val initialVaultUpdate = Vault.Update(consumedStates, unconsumedStates)
                vaultUpdates.startWith(initialVaultUpdate).onErrorResumeNext { Observable.empty() }.subscribe(vaultUpdatesSubject)

                // Transactions
                val (transactions, newTransactions) = proxy.internalVerifiedTransactionsFeed()
                newTransactions.startWith(transactions).onErrorResumeNext { Observable.empty() }.subscribe(transactionsSubject)

                // SM -> TX mapping
                val (smTxMappings, futureSmTxMappings) = proxy.stateMachineRecordedTransactionMappingFeed()
                futureSmTxMappings.startWith(smTxMappings).onErrorResumeNext { Observable.empty() }.subscribe(stateMachineTransactionMappingSubject)

                // Parties on network
                val (parties, futurePartyUpdate) = proxy.networkMapFeed()
                futurePartyUpdate.startWith(parties.map { MapChange.Added(it) }).onErrorResumeNext { Observable.empty() }.subscribe(networkMapSubject)
            }
        }

        val stateMachines = performRpcReconnect(nodeHostAndPort, username, password)

        // Extract the flow tracking stream
        // TODO is there a nicer way of doing this? Stream of streams in general results in code like this...
        // TODO `progressTrackingSubject` doesn't seem to be used anymore - should it be removed?
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

        // We need to retry, because when flow errors, we unsubscribe from progressTrackingSubject. So we end up with stream of state machine updates and no progress trackers.
        futureProgressTrackerUpdates.startWith(currentProgressTrackerUpdates).flatMap { it }.retry().subscribe(progressTrackingSubject)
    }

    private fun performRpcReconnect(nodeHostAndPort: NetworkHostAndPort, username: String, password: String): List<StateMachineInfo> {

        logger.info("Connecting to: $nodeHostAndPort")

        val retryInterval = 10.seconds
        val client = CordaRPCClient(
                nodeHostAndPort,
                object : CordaRPCClientConfiguration {
                    override val connectionMaxRetryInterval = retryInterval
                }
        )
        val connection = client.start(username, password)
        val proxy = connection.proxy

        val (stateMachineInfos, stateMachineUpdatesRaw) = proxy.stateMachinesFeed()

        stateMachineUpdatesRaw
                .startWith(stateMachineInfos.map { StateMachineUpdate.Added(it) })
                .onErrorResumeNext {
                    // Failure has occurred for a reason, perhaps because the server backend is being re-started.
                    // Give it some time to come back online before trying to re-connect.
                    Thread.sleep(retryInterval.toMillis())
                    performRpcReconnect(nodeHostAndPort, username, password)
                    // Returning empty observable such that error will not be propagated downstream.
                    Observable.empty()
                }
                .subscribe(retryableStateMachineUpdatesSubject)

        proxyObservable.set(CordaRPCOpsWrapper(proxy))
        notaryIdentities = proxy.notaryIdentities()

        return stateMachineInfos
    }
}
