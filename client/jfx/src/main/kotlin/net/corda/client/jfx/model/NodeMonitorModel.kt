package net.corda.client.jfx.model

import javafx.beans.property.SimpleObjectProperty
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.RPCException
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

    val proxyObservable = SimpleObjectProperty<CordaRPCOps?>()
    lateinit var notaryIdentities: List<Party>

    /**
     * Register for updates to/from a given vault.
     * TODO provide an unsubscribe mechanism
     */
    fun register(nodeHostAndPort: NetworkHostAndPort, username: String, password: String) {
        val client = CordaRPCClient(
                nodeHostAndPort,
                CordaRPCClientConfiguration.DEFAULT.copy(
                        connectionMaxRetryInterval = 10.seconds
                )
        )
        val connection = client.start(username, password)
        val proxy = connection.proxy
        notaryIdentities = proxy.notaryIdentities()

        val (stateMachines, stateMachineUpdates) = proxy.stateMachinesFeed()
        // Extract the flow tracking stream
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

        // We need to retry, because when flow errors, we unsubscribe from progressTrackingSubject. So we end up with stream of state machine updates and no progress trackers.
        futureProgressTrackerUpdates.startWith(currentProgressTrackerUpdates).flatMap { it }.retry().subscribe(progressTrackingSubject)

        // Now the state machines
        val currentStateMachines = stateMachines.map { StateMachineUpdate.Added(it) }
        stateMachineUpdates.startWith(currentStateMachines).subscribe(stateMachineUpdatesSubject)

        // Vault snapshot (force single page load with MAX_PAGE_SIZE) + updates
        val (_, vaultUpdates) = proxy.vaultTrackBy<ContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL),
                PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE))

        val vaultSnapshot = proxy.vaultQueryBy<ContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED),
                PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE))
        // We have to fetch the snapshot separately since vault query API doesn't allow different criteria for snapshot and updates.
        // TODO : This will create a small window of opportunity for inconsistent updates, might need to change the vault API to handle this case.
        val initialVaultUpdate = Vault.Update(setOf(), vaultSnapshot.states.toSet())
        vaultUpdates.startWith(initialVaultUpdate).subscribe(vaultUpdatesSubject)

        // Transactions
        val (transactions, newTransactions) = proxy.internalVerifiedTransactionsFeed()
        newTransactions.startWith(transactions).subscribe(transactionsSubject)

        // SM -> TX mapping
        val (smTxMappings, futureSmTxMappings) = proxy.stateMachineRecordedTransactionMappingFeed()
        futureSmTxMappings.startWith(smTxMappings).subscribe(stateMachineTransactionMappingSubject)

        // Parties on network
        val (parties, futurePartyUpdate) = proxy.networkMapFeed()
        futurePartyUpdate.startWith(parties.map { MapChange.Added(it) }).subscribe(networkMapSubject)

        do {
            val connection = try {
                logger.info("Connecting to: $nodeHostAndPort")
                val client = CordaRPCClient(
                        nodeHostAndPort,
                        CordaRPCClientConfiguration.DEFAULT.copy(
                                connectionMaxRetryInterval = retryInterval
                        )
                )
                val _connection = client.start(username, password)
                // Check connection is truly operational before returning it.
                val nodeInfo = _connection.proxy.nodeInfo()
                require(nodeInfo.legalIdentitiesAndCerts.isNotEmpty())
                _connection
            } catch (throwable: Throwable) {
                when (throwable) {
                    is ActiveMQException, is RPCException -> {
                        // Happens when:
                        // * incorrect credentials provided;
                        // * incorrect endpoint specified;
                        // - no point to retry connecting.
                        throw throwable
                    }
                    else -> {
                        // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                        logger.info("Exception upon establishing connection: " + throwable.message)
                        null
                    }
                }
            }
            if (connection != null) {
                logger.info("Connection successfully established with: $nodeHostAndPort")
                return connection
            }
            // Could not connect this time round - pause before giving another try.
            Thread.sleep(retryInterval.toMillis())
        } while (connection == null)

        proxyObservable.set(proxy)
    }
}
