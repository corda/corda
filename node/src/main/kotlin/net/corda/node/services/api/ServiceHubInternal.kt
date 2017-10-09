package net.corda.node.services.api

import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.TransactionStorage
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.statemachine.FlowLogicRefFactoryImpl
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.CordaPersistence

interface NetworkMapCacheInternal : NetworkMapCache {
    /**
     * Deregister from updates from the given map service.
     * @param network the network messaging service.
     * @param mapParty the network map service party to fetch current state from.
     */
    fun deregisterForUpdates(network: MessagingService, mapParty: Party): CordaFuture<Unit>

    /**
     * Add a network map service; fetches a copy of the latest map from the service and subscribes to any further
     * updates.
     * @param network the network messaging service.
     * @param networkMapAddress the network map service to fetch current state from.
     * @param subscribe if the cache should subscribe to updates.
     * @param ifChangedSinceVer an optional version number to limit updating the map based on. If the latest map
     * version is less than or equal to the given version, no update is fetched.
     */
    fun addMapService(network: MessagingService, networkMapAddress: SingleMessageRecipient,
                      subscribe: Boolean, ifChangedSinceVer: Int? = null): CordaFuture<Unit>

    /** Adds a node to the local cache (generally only used for adding ourselves). */
    fun addNode(node: NodeInfo)

    /** Removes a node from the local cache. */
    fun removeNode(node: NodeInfo)

    /** For testing where the network map cache is manipulated marks the service as immediately ready. */
    @VisibleForTesting
    fun runWithoutMapService()

    /** Indicates if loading network map data from database was successful. */
    val loadDBSuccess: Boolean
}

@CordaSerializable
sealed class NetworkCacheException : CordaException("Network Cache Error") {
    /** Indicates a failure to deregister, because of a rejected request from the remote node */
    class DeregistrationFailed : NetworkCacheException()
}

interface ServiceHubInternal : ServiceHub {
    companion object {
        private val log = loggerFor<ServiceHubInternal>()
    }

    override val vaultService: VaultServiceInternal
    /**
     * A map of hash->tx where tx has been signature/contract validated and the states are known to be correct.
     * The signatures aren't technically needed after that point, but we keep them around so that we can relay
     * the transaction data to other nodes that need it.
     */
    override val validatedTransactions: WritableTransactionStorage
    val stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage
    val monitoringService: MonitoringService
    val schemaService: SchemaService
    override val networkMapCache: NetworkMapCacheInternal
    val schedulerService: SchedulerService
    val auditService: AuditService
    val rpcFlows: List<Class<out FlowLogic<*>>>
    val networkService: MessagingService
    val database: CordaPersistence
    val configuration: NodeConfiguration

    override fun recordTransactions(notifyVault: Boolean, txs: Iterable<SignedTransaction>) {
        require(txs.any()) { "No transactions passed in for recording" }
        val recordedTransactions = txs.filter { validatedTransactions.addTransaction(it) }
        val stateMachineRunId = FlowStateMachineImpl.currentStateMachine()?.id
        if (stateMachineRunId != null) {
            recordedTransactions.forEach {
                stateMachineRecordedTransactionMapping.addMapping(stateMachineRunId, it.id)
            }
        } else {
            log.warn("Transactions recorded from outside of a state machine")
        }

        if (notifyVault) {
            val toNotify = recordedTransactions.map { if (it.isNotaryChangeTransaction()) it.notaryChangeTx else it.tx }
            vaultService.notifyAll(toNotify)
        }
    }

    /**
     * Starts an already constructed flow. Note that you must be on the server thread to call this method. [FlowInitiator]
     * defaults to [FlowInitiator.RPC] with username "Only For Testing".
     */
    @VisibleForTesting
    fun <T> startFlow(logic: FlowLogic<T>): FlowStateMachine<T> = startFlow(logic, FlowInitiator.RPC("Only For Testing"))

    /**
     * Starts an already constructed flow. Note that you must be on the server thread to call this method.
     * @param flowInitiator indicates who started the flow, see: [FlowInitiator].
     */
    fun <T> startFlow(logic: FlowLogic<T>, flowInitiator: FlowInitiator, ourIdentity: Party? = null): FlowStateMachineImpl<T>

    /**
     * Will check [logicType] and [args] against a whitelist and if acceptable then construct and initiate the flow.
     * Note that you must be on the server thread to call this method. [flowInitiator] points how flow was started,
     * See: [FlowInitiator].
     *
     * @throws net.corda.core.flows.IllegalFlowLogicException or IllegalArgumentException if there are problems with the
     * [logicType] or [args].
     */
    fun <T> invokeFlowAsync(
            logicType: Class<out FlowLogic<T>>,
            flowInitiator: FlowInitiator,
            vararg args: Any?): FlowStateMachineImpl<T> {
        val logicRef = FlowLogicRefFactoryImpl.createForRPC(logicType, *args)
        val logic: FlowLogic<T> = uncheckedCast(FlowLogicRefFactoryImpl.toFlowLogic(logicRef))
        return startFlow(logic, flowInitiator, ourIdentity = null)
    }

    fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>?
}

/**
 * Thread-safe storage of transactions.
 */
interface WritableTransactionStorage : TransactionStorage {
    /**
     * Add a new transaction to the store. If the store already has a transaction with the same id it will be
     * overwritten.
     * @param transaction The transaction to be recorded.
     * @return true if the transaction was recorded successfully, false if it was already recorded.
     */
    // TODO: Throw an exception if trying to add a transaction with fewer signatures than an existing entry.
    fun addTransaction(transaction: SignedTransaction): Boolean
}

/**
 * This is the interface to storage storing state machine -> recorded tx mappings. Any time a transaction is recorded
 * during a flow run [addMapping] should be called.
 */
interface StateMachineRecordedTransactionMappingStorage {
    fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash)
    fun track(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping>
}
