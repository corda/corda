package net.corda.node.services

import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.crypto.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.*
import net.corda.core.protocols.ProtocolLogic
import net.corda.core.protocols.ProtocolLogicRefFactory
import net.corda.core.transactions.SignedTransaction
import net.corda.node.serialization.NodeClock
import net.corda.node.services.api.MessagingServiceInternal
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.persistence.DataVending
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.vault.NodeVaultService
import net.corda.testing.MOCK_IDENTITY_SERVICE
import net.corda.testing.node.MockNetworkMapCache
import net.corda.testing.node.MockStorageService
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

@Suppress("LeakingThis")
open class MockServiceHubInternal(
        customVault: VaultService? = null,
        val keyManagement: KeyManagementService? = null,
        val net: MessagingServiceInternal? = null,
        val identity: IdentityService? = MOCK_IDENTITY_SERVICE,
        val storage: TxWritableStorageService? = MockStorageService(),
        val mapCache: NetworkMapCache? = MockNetworkMapCache(),
        val scheduler: SchedulerService? = null,
        val overrideClock: Clock? = NodeClock(),
        val protocolFactory: ProtocolLogicRefFactory? = ProtocolLogicRefFactory(),
        val schemas: SchemaService? = NodeSchemaService()
) : ServiceHubInternal() {
    override val vaultService: VaultService = customVault ?: NodeVaultService(this)
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
    override val protocolLogicRefFactory: ProtocolLogicRefFactory
        get() = protocolFactory ?: throw UnsupportedOperationException()
    override val schemaService: SchemaService
        get() = schemas ?: throw UnsupportedOperationException()

    // We isolate the storage service with writable TXes so that it can't be accessed except via recordTransactions()
    private val txStorageService: TxWritableStorageService
        get() = storage ?: throw UnsupportedOperationException()

    private val protocolFactories = ConcurrentHashMap<Class<*>, (Party) -> ProtocolLogic<*>>()

    lateinit var smm: StateMachineManager

    init {
        if (net != null && storage != null) {
            // Creating this class is sufficient, we don't have to store it anywhere, because it registers a listener
            // on the networking service, so that will keep it from being collected.
            DataVending.Service(this)
        }
    }

    override fun recordTransactions(txs: Iterable<SignedTransaction>) = recordTransactionsInternal(txStorageService, txs)

    override fun <T> startProtocol(logic: ProtocolLogic<T>): ListenableFuture<T> = smm.add(logic).resultFuture

    override fun registerProtocolInitiator(markerClass: KClass<*>, protocolFactory: (Party) -> ProtocolLogic<*>) {
        protocolFactories[markerClass.java] = protocolFactory
    }

    override fun getProtocolFactory(markerClass: Class<*>): ((Party) -> ProtocolLogic<*>)? {
        return protocolFactories[markerClass]
    }
}
