package net.corda.client.jfx.model

import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.contracts.ContractState
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.staticField
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
import rx.Subscription
import rx.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    private val retryableStateMachineUpdatesSubject = PublishSubject.create<StateMachineUpdate>()
    private val stateMachineUpdatesSubject = PublishSubject.create<StateMachineUpdate>()
    private val vaultUpdatesSubject = PublishSubject.create<Vault.Update<ContractState>>()
    private val transactionsSubject = PublishSubject.create<SignedTransaction>()
    private val stateMachineTransactionMappingSubject = PublishSubject.create<StateMachineTransactionMapping>()
    private val progressTrackingSubject = PublishSubject.create<ProgressTrackingEvent>()
    private val networkMapSubject = PublishSubject.create<MapChange>()
    private var rpcConnection: CordaRPCConnection? = null

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

        private fun runLaterIfInitialized(op: () -> Unit) {

            val initialized = PlatformImpl::class.java.staticField<AtomicBoolean>("initialized")

            // Only execute using "runLater()" if JavaFX been initialized.
            // It may not be initialized in the unit test.
            // Also if we are already in the JavaFX thread - perform direct invocation without postponing it.
            if (initialized.value.get() && !Platform.isFxApplicationThread()) {
                Platform.runLater(op)
            } else {
                op()
            }
        }
    }

    /**
     * This is needed as JavaFX listener framework attempts to call `equals()` before dispatching notification change.
     * And calling `CordaRPCOps.equals()` results in (unhandled) remote call.
     */
    class CordaRPCOpsWrapper(val cordaRPCOps: CordaRPCOps)

    /**
     * Disconnects from the Corda node for a clean client shutdown.
     */
    override fun close() {
        try {
            rpcConnection?.notifyServerAndClose()
        } catch (e: Exception) {
            logger.error("Error closing RPC connection to node", e)
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
            if (wrapper != null) {
                val proxy = wrapper.cordaRPCOps
                // Vault snapshot (force single page load with MAX_PAGE_SIZE) + updates
                val (statesSnapshot, vaultUpdates) = proxy.vaultTrackBy<ContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL),
                        PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE))
                val unconsumedStates = statesSnapshot.states.filterIndexed { index, _ ->
                    statesSnapshot.statesMetadata[index].status == Vault.StateStatus.UNCONSUMED
                }.toSet()
                val consumedStates = statesSnapshot.states.toSet() - unconsumedStates
                val initialVaultUpdate = Vault.Update(consumedStates, unconsumedStates)
                vaultUpdates.startWith(initialVaultUpdate).subscribe({ vaultUpdatesSubject.onNext(it) }, {})

                // Transactions
                val (transactions, newTransactions) = proxy.internalVerifiedTransactionsFeed()
                newTransactions.startWith(transactions).subscribe({ transactionsSubject.onNext(it) }, {})

                // SM -> TX mapping
                val (smTxMappings, futureSmTxMappings) = proxy.stateMachineRecordedTransactionMappingFeed()
                futureSmTxMappings.startWith(smTxMappings).subscribe({ stateMachineTransactionMappingSubject.onNext(it) }, {})

                // Parties on network
                val (parties, futurePartyUpdate) = proxy.networkMapFeed()
                futurePartyUpdate.startWith(parties.map { MapChange.Added(it) }).subscribe({ networkMapSubject.onNext(it) }, {})
            }
        }

        val stateMachines = performRpcReconnect(nodeHostAndPort, username, password, shouldRetry = false)

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

    private fun performRpcReconnect(nodeHostAndPort: NetworkHostAndPort, username: String, password: String, shouldRetry: Boolean): List<StateMachineInfo> {
        val proxy = establishConnectionWithRetry(nodeHostAndPort, username, password, shouldRetry).let { connection ->
            rpcConnection = connection
            connection.proxy
        }

        val (stateMachineInfos, stateMachineUpdatesRaw) = proxy.stateMachinesFeed()

        val retryableStateMachineUpdatesSubscription: AtomicReference<Subscription?> = AtomicReference(null)
        val subscription: Subscription = stateMachineUpdatesRaw
                .startWith(stateMachineInfos.map { StateMachineUpdate.Added(it) })
                .subscribe({ retryableStateMachineUpdatesSubject.onNext(it) }, {
                    // Terminate subscription such that nothing gets past this point to downstream Observables.
                    retryableStateMachineUpdatesSubscription.get()?.unsubscribe()
                    // Flag to everyone that proxy is no longer available.
                    runLaterIfInitialized { proxyObservable.set(null) }
                    // It is good idea to close connection to properly mark the end of it. During re-connect we will create a new
                    // client and a new connection, so no going back to this one. Also the server might be down, so we are
                    // force closing the connection to avoid propagation of notification to the server side.
                    rpcConnection?.forceClose()
                    // Perform re-connect.
                    performRpcReconnect(nodeHostAndPort, username, password, shouldRetry = true)
                })

        retryableStateMachineUpdatesSubscription.set(subscription)
        runLaterIfInitialized { proxyObservable.set(CordaRPCOpsWrapper(proxy)) }
        notaryIdentities = proxy.notaryIdentities()

        return stateMachineInfos
    }

    private fun establishConnectionWithRetry(nodeHostAndPort: NetworkHostAndPort, username: String, password: String, shouldRetry: Boolean): CordaRPCConnection {
        val retryInterval = 5.seconds

        val client = CordaRPCClient(
            nodeHostAndPort,
            CordaRPCClientConfiguration.DEFAULT.copy(
                connectionMaxRetryInterval = retryInterval
            )
        )
        do {
            val connection = try {
                logger.info("Connecting to: $nodeHostAndPort")
                val _connection = client.start(username, password)
                // Check connection is truly operational before returning it.
                val nodeInfo = _connection.proxy.nodeInfo()
                require(nodeInfo.legalIdentitiesAndCerts.isNotEmpty())
                _connection
            } catch (throwable: Throwable) {
                if (shouldRetry) {
                    // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                    logger.info("Exception upon establishing connection: {}", throwable.message)
                    null
                } else {
                    throw throwable
                }
            }

            if (connection != null) {
                logger.info("Connection successfully established with: $nodeHostAndPort")
                return connection
            }
            // Could not connect this time round - pause before giving another try.
            Thread.sleep(retryInterval.toMillis())
        } while (connection == null)

        throw IllegalArgumentException("Never reaches here")
    }
}