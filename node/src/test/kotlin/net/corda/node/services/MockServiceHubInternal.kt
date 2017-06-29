package net.corda.node.services

import com.codahale.metrics.MetricRegistry
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.*
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.serialization.NodeClock
import net.corda.node.services.api.*
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.testing.MOCK_IDENTITY_SERVICE
import net.corda.testing.node.MockNetworkMapCache
import net.corda.testing.node.MockStorageService
import org.jetbrains.exposed.sql.Database
import java.time.Clock

open class MockServiceHubInternal(
        val customVault: VaultService? = null,
        val customVaultQuery: VaultQueryService? = null,
        val keyManagement: KeyManagementService? = null,
        val network: MessagingService? = null,
        val identity: IdentityService? = MOCK_IDENTITY_SERVICE,
        val storage: TxWritableStorageService? = MockStorageService(),
        val mapCache: NetworkMapCacheInternal? = MockNetworkMapCache(),
        val scheduler: SchedulerService? = null,
        val overrideClock: Clock? = NodeClock(),
        val schemas: SchemaService? = NodeSchemaService(),
        val customTransactionVerifierService: TransactionVerifierService? = InMemoryTransactionVerifierService(2)
) : ServiceHubInternal() {
    override val vaultQueryService: VaultQueryService
        get() = customVaultQuery ?: throw UnsupportedOperationException()
    override val transactionVerifierService: TransactionVerifierService
        get() = customTransactionVerifierService ?: throw UnsupportedOperationException()
    override val vaultService: VaultService
        get() = customVault ?: throw UnsupportedOperationException()
    override val keyManagementService: KeyManagementService
        get() = keyManagement ?: throw UnsupportedOperationException()
    override val identityService: IdentityService
        get() = identity ?: throw UnsupportedOperationException()
    override val networkService: MessagingService
        get() = network ?: throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCacheInternal
        get() = mapCache ?: throw UnsupportedOperationException()
    override val storageService: StorageService
        get() = storage ?: throw UnsupportedOperationException()
    override val schedulerService: SchedulerService
        get() = scheduler ?: throw UnsupportedOperationException()
    override val clock: Clock
        get() = overrideClock ?: throw UnsupportedOperationException()
    override val myInfo: NodeInfo
        get() = throw UnsupportedOperationException()
    override val database: Database
        get() = throw UnsupportedOperationException()
    override val configuration: NodeConfiguration
        get() = throw UnsupportedOperationException()
    override val monitoringService: MonitoringService = MonitoringService(MetricRegistry())
    override val rpcFlows: List<Class<out FlowLogic<*>>>
        get() = throw UnsupportedOperationException()
    override val schemaService: SchemaService
        get() = schemas ?: throw UnsupportedOperationException()
    override val auditService: AuditService = DummyAuditService()
    // We isolate the storage service with writable TXes so that it can't be accessed except via recordTransactions()
    private val txStorageService: TxWritableStorageService
        get() = storage ?: throw UnsupportedOperationException()

    lateinit var smm: StateMachineManager

    override fun recordTransactions(txs: Iterable<SignedTransaction>) = recordTransactionsInternal(txStorageService, txs)

    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T = throw UnsupportedOperationException()

    override fun <T> startFlow(logic: FlowLogic<T>, flowInitiator: FlowInitiator): FlowStateMachineImpl<T> {
        return smm.executor.fetchFrom { smm.add(logic, flowInitiator) }
    }

    override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? = null
}
