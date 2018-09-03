package net.corda.rpcWorker

import com.codahale.metrics.MetricRegistry
import com.jcabi.manifests.Manifests
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.sign
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ContractUpgradeService
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.contextLogger
import net.corda.node.CordaClock
import net.corda.node.SimpleClock
import net.corda.node.VersionInfo
import net.corda.node.internal.*
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.serialization.kryo.KryoServerSerializationScheme
import net.corda.node.services.api.AuditService
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.*
import net.corda.node.services.persistence.DBTransactionMappingStorage
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.persistence.NodePropertiesPersistentStore
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.vault.NodeVaultService
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.isH2Database
import net.corda.serialization.internal.*
import org.slf4j.Logger
import rx.schedulers.Schedulers
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.util.*

class RpcWorkerServiceHub(override val configuration: NodeConfiguration, override val myInfo: NodeInfo, private val signedNetworkParameters: NetworkParametersReader.NetworkParametersAndSigned, private val ourKeyPair: KeyPair, private val trustRoot: X509Certificate, private val nodeCa: X509Certificate) : ServiceHubInternal, SingletonSerializeAsToken() {

    override val clock: CordaClock = SimpleClock(Clock.systemUTC())
    private val versionInfo = getVersionInfo()
    private val cordappLoader = JarScanningCordappLoader.fromDirectories(configuration.cordappDirectories, versionInfo)

    private val log: Logger get() = staticLog

    companion object {
        private val staticLog = contextLogger()
    }

    private val runOnStop = ArrayList<() -> Any?>()

    override val schemaService = NodeSchemaService(cordappLoader.cordappSchemas, false)
    override val identityService = PersistentIdentityService()
    override val database: CordaPersistence = createCordaPersistence(
            configuration.database,
            identityService::wellKnownPartyFromX500Name,
            identityService::wellKnownPartyFromAnonymous,
            schemaService
    )

    init {
        // TODO Break cyclic dependency
        identityService.database = database
    }

    private val persistentNetworkMapCache = PersistentNetworkMapCache(database, myInfo.legalIdentities[0].name)
    override val networkMapCache = NetworkMapCacheImpl(persistentNetworkMapCache, identityService, database)
    @Suppress("LeakingThis")
    override val validatedTransactions: WritableTransactionStorage = DBTransactionStorage(configuration.transactionCacheSizeBytes, database)
    private val networkMapClient: NetworkMapClient? = configuration.networkServices?.let { NetworkMapClient(it.networkMapURL, versionInfo) }
    private val metricRegistry = MetricRegistry()
    override val attachments = NodeAttachmentService(metricRegistry, database, configuration.attachmentContentCacheSizeBytes, configuration.attachmentCacheBound)

    override val cordappProvider = CordappProviderImpl(cordappLoader, CordappConfigFileProvider(), attachments)

    @Suppress("LeakingThis")
    override val keyManagementService = PersistentKeyManagementService(identityService, database)
    private val servicesForResolution = ServicesForResolutionImpl(identityService, attachments, cordappProvider, validatedTransactions)
    @Suppress("LeakingThis")
    override val vaultService = NodeVaultService(clock, keyManagementService, servicesForResolution, database, schemaService)
    override val nodeProperties = NodePropertiesPersistentStore(StubbedNodeUniqueIdProvider::value, database)
    override val monitoringService = MonitoringService(metricRegistry)
    override val networkMapUpdater = NetworkMapUpdater(
            networkMapCache,
            NodeInfoWatcher(
                    configuration.baseDirectory,
                    @Suppress("LeakingThis")
                    Schedulers.io(),
                    Duration.ofMillis(configuration.additionalNodeInfoPollingFrequencyMsec)
            ),
            networkMapClient,
            configuration.baseDirectory,
            configuration.extraNetworkMapKeys
    ).closeOnStop()

    override val networkParameters = signedNetworkParameters.networkParameters

    override val transactionVerifierService: TransactionVerifierService
        get() {
            throw NotImplementedError()
        }
    override val contractUpgradeService: ContractUpgradeService
        get() {
            throw NotImplementedError()
        }
    override val auditService: AuditService
        get() {
            throw NotImplementedError()
        }

    // TODO schedulerService

    override val rpcFlows = ArrayList<Class<out FlowLogic<*>>>()
    override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage(database)
    override val networkService: MessagingService
        get() {
            throw NotImplementedError()
        }

    private fun <T : AutoCloseable> T.closeOnStop(): T {
        runOnStop += this::close
        return this
    }

    override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
        throw NotImplementedError()
    }

    override fun loadState(stateRef: StateRef): TransactionState<*> {
        return servicesForResolution.loadState(stateRef)
    }

    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return servicesForResolution.loadStates(stateRefs)
    }

    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
        throw NotImplementedError()
    }

    override fun jdbcSession(): Connection {
        throw NotImplementedError()
    }

    override fun registerUnloadHandler(runOnStop: () -> Unit) {
        this.runOnStop += runOnStop
    }

    private fun getVersionInfo(): VersionInfo {
        // Manifest properties are only available if running from the corda jar
        fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null

        return VersionInfo(
                manifestValue("Corda-Platform-Version")?.toInt() ?: 1,
                manifestValue("Corda-Release-Version") ?: "Unknown",
                manifestValue("Corda-Revision") ?: "Unknown",
                manifestValue("Corda-Vendor") ?: "Unknown"
        )
    }

    private fun initialiseSerialization() {
        val serializationExists = try {
            effectiveSerializationEnv
            true
        } catch (e: IllegalStateException) {
            false
        }
        if (!serializationExists) {
            val classloader = cordappLoader.appClassLoader
            nodeSerializationEnv = SerializationEnvironmentImpl(
                    SerializationFactoryImpl().apply {
                        registerScheme(AMQPServerSerializationScheme(cordappLoader.cordapps))
                        registerScheme(AMQPClientSerializationScheme(cordappLoader.cordapps))
                        registerScheme(KryoServerSerializationScheme())
                    },
                    p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                    rpcServerContext = AMQP_RPC_SERVER_CONTEXT.withClassLoader(classloader),
                    storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),
                    checkpointContext = KRYO_CHECKPOINT_CONTEXT.withClassLoader(classloader),
                    rpcClientContext = AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classloader))
        }
    }

    val rpcOps = CordaRpcWorkerOps(this, {})

    fun start() {
        log.info("Rpc Worker starting up ...")

        initialiseSerialization()

        networkMapClient?.start(trustRoot)

        servicesForResolution.start(networkParameters)

        val isH2Database = isH2Database(configuration.dataSourceProperties.getProperty("dataSource.url", ""))
        val schemas = if (isH2Database) schemaService.internalSchemas() else schemaService.schemaOptions.keys

        database.startHikariPool(configuration.dataSourceProperties, configuration.database, schemas)
        identityService.start(trustRoot, listOf(myInfo.legalIdentitiesAndCerts.first().certificate, nodeCa))
        persistentNetworkMapCache.start(networkParameters.notaries)

        runOnStop += { rpcOps.shutdown() }
        rpcOps.start()

        database.transaction {
            networkMapCache.start()
            networkMapCache.addNode(myInfo)
        }

        val nodeInfoAndSigned = NodeInfoAndSigned(myInfo) { _, serialised ->
            ourKeyPair.private.sign(serialised.bytes)
        }
        identityService.ourNames = myInfo.legalIdentities.map { it.name }.toSet()

        networkMapUpdater.start(trustRoot, signedNetworkParameters.signed.raw.hash, nodeInfoAndSigned.signed.raw.hash)

        database.transaction {
            identityService.loadIdentities(myInfo.legalIdentitiesAndCerts)
            attachments.start()
            nodeProperties.start()
            keyManagementService.start(setOf(ourKeyPair))
            vaultService.start()
        }
    }

    fun stop() {
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
    }
}