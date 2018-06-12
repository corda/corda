package net.corda.node.services.api

import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.FlowStateMachine
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NetworkMapCacheBase
import net.corda.core.node.services.TransactionStorage
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapUpdater
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.SubFlowVersion
import net.corda.nodeapi.internal.persistence.CordaPersistence

interface NetworkMapCacheInternal : NetworkMapCache, NetworkMapCacheBaseInternal
interface NetworkMapCacheBaseInternal : NetworkMapCacheBase {
    val allNodeHashes: List<SecureHash>

    fun getNodeByHash(nodeHash: SecureHash): NodeInfo?

    /** Find nodes from the [PublicKey] toShortString representation.
     * This is used for Artemis bridge lookup process. */
    fun getNodesByOwningKeyIndex(identityKeyIndex: String): List<NodeInfo>

    /** Adds a node to the local cache (generally only used for adding ourselves). */
    fun addNode(node: NodeInfo)

    /** Removes a node from the local cache. */
    fun removeNode(node: NodeInfo)

    /** Indicates if loading network map data from database was successful. */
    val loadDBSuccess: Boolean
}

interface ServiceHubInternal : ServiceHub {
    companion object {
        private val log = contextLogger()

        fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>,
                               validatedTransactions: WritableTransactionStorage,
                               stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage,
                               vaultService: VaultServiceInternal,
                               database: CordaPersistence) {

            database.transaction {
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

                if (statesToRecord != StatesToRecord.NONE) {
                    // When the user has requested StatesToRecord.ALL we may end up recording and relationally mapping states
                    // that do not involve us and that we cannot sign for. This will break coin selection and thus a warning
                    // is present in the documentation for this feature (see the "Observer nodes" tutorial on docs.corda.net).
                    //
                    // The reason for this is three-fold:
                    //
                    // 1) We are putting in place the observer mode feature relatively quickly to meet specific customer
                    //    launch target dates.
                    //
                    // 2) The right design for vaults which mix observations and relevant states isn't entirely clear yet.
                    //
                    // 3) If we get the design wrong it could create security problems and business confusions.
                    //
                    // Back in the bitcoinj days I did add support for "watching addresses" to the wallet code, which is the
                    // Bitcoin equivalent of observer nodes:
                    //
                    //   https://bitcoinj.github.io/working-with-the-wallet#watching-wallets
                    //
                    // The ability to have a wallet containing both irrelevant and relevant states complicated everything quite
                    // dramatically, even methods as basic as the getBalance() API which required additional modes to let you
                    // query "balance I can spend" vs "balance I am observing". In the end it might have been better to just
                    // require the user to create an entirely separate wallet for observing with.
                    //
                    // In Corda we don't support a single node having multiple vaults (at the time of writing), and it's not
                    // clear that's the right way to go: perhaps adding an "origin" column to the VAULT_STATES table is a better
                    // solution. Then you could select subsets of states depending on where the report came from.
                    //
                    // The risk of doing this is that apps/developers may use 'canned SQL queries' not written by us that forget
                    // to add a WHERE clause for the origin column. Those queries will seem to work most of the time until
                    // they're run on an observer node and mix in irrelevant data. In the worst case this may result in
                    // erroneous data being reported to the user, which could cause security problems.
                    //
                    // Because the primary use case for recording irrelevant states is observer/regulator nodes, who are unlikely
                    // to make writes to the ledger very often or at all, we choose to punt this issue for the time being.
                    vaultService.notifyAll(statesToRecord, recordedTransactions.map { it.coreTransaction })
                }
            }
        }
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
    val auditService: AuditService
    val rpcFlows: List<Class<out FlowLogic<*>>>
    val networkService: MessagingService
    val database: CordaPersistence
    val configuration: NodeConfiguration
    val nodeProperties: NodePropertiesStore
    val networkMapUpdater: NetworkMapUpdater
    override val cordappProvider: CordappProviderInternal
    override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
        recordTransactions(statesToRecord, txs, validatedTransactions, stateMachineRecordedTransactionMapping, vaultService, database)
    }

    fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>?
    fun createSubFlowVersion(flowLogic: FlowLogic<*>): SubFlowVersion {
        val platformVersion = myInfo.platformVersion
        // If no CorDapp found then it is a Core flow.
        return cordappProvider.getCordappForFlow(flowLogic)?.let { SubFlowVersion.CorDappFlow(platformVersion, it.name, it.jarHash) }
                ?: SubFlowVersion.CoreFlow(platformVersion)
    }
}

interface FlowStarter {

    /**
     * Starts an already constructed flow. Note that you must be on the server thread to call this method. This method
     * just synthesizes an [ExternalEvent.ExternalStartFlowEvent] and calls the method below.
     * @param context indicates who started the flow, see: [InvocationContext].
     */
    fun <T> startFlow(logic: FlowLogic<T>, context: InvocationContext): CordaFuture<FlowStateMachine<T>>

    /**
     * Starts a flow as described by an [ExternalEvent.ExternalStartFlowEvent].  If a transient error
     * occurs during invocation, it will re-attempt to start the flow.
     */
    fun <T> startFlow(event: ExternalEvent.ExternalStartFlowEvent<T>): CordaFuture<FlowStateMachine<T>>

    /**
     * Will check [logicType] and [args] against a whitelist and if acceptable then construct and initiate the flow.
     * Note that you must be on the server thread to call this method. [context] points how flow was started,
     * See: [InvocationContext].
     *
     * @throws net.corda.core.flows.IllegalFlowLogicException or IllegalArgumentException if there are problems with the
     * [logicType] or [args].
     */
    fun <T> invokeFlowAsync(
            logicType: Class<out FlowLogic<T>>,
            context: InvocationContext,
            vararg args: Any?): CordaFuture<FlowStateMachine<T>>
}

interface StartedNodeServices : ServiceHubInternal, FlowStarter
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
