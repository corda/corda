package com.r3corda.node.services

import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.services.*
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolLogicRefFactory
import com.r3corda.core.testing.InMemoryVaultService
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.api.MessagingServiceInternal
import com.r3corda.node.services.api.MonitoringService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.api.SchemaService
import com.r3corda.node.services.persistence.DataVending
import com.r3corda.node.services.schema.NodeSchemaService
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.testing.MOCK_IDENTITY_SERVICE
import com.r3corda.testing.node.MockNetworkMapCache
import com.r3corda.testing.node.MockStorageService
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
    override val vaultService: VaultService = customVault ?: InMemoryVaultService(this)
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

    override fun <T> startProtocol(loggerName: String, logic: ProtocolLogic<T>): ListenableFuture<T> {
        return smm.add(loggerName, logic).resultFuture
    }

    override fun registerProtocolInitiator(markerClass: KClass<*>, protocolFactory: (Party) -> ProtocolLogic<*>) {
        protocolFactories[markerClass.java] = protocolFactory
    }

    override fun getProtocolFactory(markerClass: Class<*>): ((Party) -> ProtocolLogic<*>)? {
        return protocolFactories[markerClass]
    }
}
