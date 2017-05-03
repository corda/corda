package net.corda.node.services

import com.codahale.metrics.MetricRegistry
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.FlowStateMachine
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.*
import net.corda.core.transactions.SignedTransaction
import net.corda.node.internal.ServiceFlowInfo
import net.corda.node.serialization.NodeClock
import net.corda.node.services.api.MessagingServiceInternal
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.testing.MOCK_IDENTITY_SERVICE
import net.corda.testing.node.MockNetworkMapCache
import net.corda.testing.node.MockStorageService
import java.time.Clock

open class MockServiceHubInternal(
        val customVault: VaultService? = null,
        val keyManagement: KeyManagementService? = null,
        val net: MessagingServiceInternal? = null,
        val identity: IdentityService? = MOCK_IDENTITY_SERVICE,
        val storage: TxWritableStorageService? = MockStorageService(),
        val mapCache: NetworkMapCache? = MockNetworkMapCache(),
        val scheduler: SchedulerService? = null,
        val overrideClock: Clock? = NodeClock(),
        val flowFactory: FlowLogicRefFactory? = FlowLogicRefFactory(),
        val schemas: SchemaService? = NodeSchemaService(),
        val customTransactionVerifierService: TransactionVerifierService? = InMemoryTransactionVerifierService(2)
) : ServiceHubInternal() {
    override val transactionVerifierService: TransactionVerifierService
        get() = customTransactionVerifierService ?: throw UnsupportedOperationException()
    override val vaultService: VaultService
        get() = customVault ?: throw UnsupportedOperationException()
    override val keyManagementService: KeyManagementService
        get() = keyManagement ?: throw UnsupportedOperationException()
    override val identityService: IdentityService
        get() = identity ?: throw UnsupportedOperationException()
    override val networkService: MessagingServiceInternal
        get() = net ?: throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCache
        get() = mapCache ?: throw UnsupportedOperationException()
    override val storageService: StorageService
        get() = storage ?: throw UnsupportedOperationException()
    override val schedulerService: SchedulerService
        get() = scheduler ?: throw UnsupportedOperationException()
    override val clock: Clock
        get() = overrideClock ?: throw UnsupportedOperationException()
    override val myInfo: NodeInfo
        get() = throw UnsupportedOperationException()

    override val monitoringService: MonitoringService = MonitoringService(MetricRegistry())
    override val flowLogicRefFactory: FlowLogicRefFactory
        get() = flowFactory ?: throw UnsupportedOperationException()
    override val schemaService: SchemaService
        get() = schemas ?: throw UnsupportedOperationException()

    // We isolate the storage service with writable TXes so that it can't be accessed except via recordTransactions()
    private val txStorageService: TxWritableStorageService
        get() = storage ?: throw UnsupportedOperationException()

    lateinit var smm: StateMachineManager

    override fun recordTransactions(txs: Iterable<SignedTransaction>) = recordTransactionsInternal(txStorageService, txs)

    override fun <T> startFlow(logic: FlowLogic<T>, flowInitiator: FlowInitiator): FlowStateMachine<T> {
        return smm.executor.fetchFrom { smm.add(logic, flowInitiator) }
    }

    override fun registerServiceFlow(clientFlowClass: Class<out FlowLogic<*>>, serviceFlowFactory: (Party) -> FlowLogic<*>) = Unit

    override fun getServiceFlowFactory(clientFlowClass: Class<out FlowLogic<*>>): ServiceFlowInfo? = null
}
