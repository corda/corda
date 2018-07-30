package net.corda.flowworker

import com.codahale.metrics.MetricRegistry
import com.google.common.collect.MutableClassToInstanceMap
import com.jcabi.manifests.Manifests
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.newSecureRandom
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.CordaClock
import net.corda.node.SimpleClock
import net.corda.node.VersionInfo
import net.corda.node.cordapp.CordappLoader
import net.corda.node.internal.*
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.serialization.kryo.KryoServerSerializationScheme
import net.corda.node.services.api.DummyAuditService
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.config.shouldInitCrashShell
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.P2PMessagingClient
import net.corda.node.services.network.*
import net.corda.node.services.persistence.*
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.MultiThreadedStateMachineExecutor
import net.corda.node.services.statemachine.MultiThreadedStateMachineManager
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AffinityExecutor
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.serialization.internal.*
import org.apache.activemq.artemis.utils.ReusableLatch
import rx.schedulers.Schedulers
import java.security.KeyPair
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class FlowWorkerServiceHub(override val configuration: NodeConfiguration, override val myInfo: NodeInfo, override val networkParameters: NetworkParameters, private val ourKeyPair: KeyPair) : ServiceHubInternal, SingletonSerializeAsToken() {

    companion object {
        @JvmStatic
        private fun makeCordappLoader(configuration: NodeConfiguration, versionInfo: VersionInfo): CordappLoader {
            return JarScanningCordappLoader.fromDirectories(configuration.cordappDirectories, versionInfo)
        }
    }

    private val versionInfo = getVersionInfo()
    override val clock: CordaClock = SimpleClock(Clock.systemUTC())

    private val runOnStop = ArrayList<() -> Any?>()

    val cordappLoader = makeCordappLoader(configuration, versionInfo)

    @Suppress("LeakingThis")
    private var tokenizableServices: MutableList<Any>? = mutableListOf(clock, this)

    override val schemaService = NodeSchemaService(cordappLoader.cordappSchemas, configuration.notary != null).tokenize()
    override val identityService = PersistentIdentityService().tokenize()
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

    private val persistentNetworkMapCache = PersistentNetworkMapCache(database)
    override val networkMapCache = NetworkMapCacheImpl(persistentNetworkMapCache, identityService, database).tokenize()
    private val checkpointStorage = DBCheckpointStorage()
    @Suppress("LeakingThis")
    override val validatedTransactions: WritableTransactionStorage = DBTransactionStorage(configuration.transactionCacheSizeBytes, database).tokenize()
    private val networkMapClient: NetworkMapClient? = configuration.networkServices?.let { NetworkMapClient(it.networkMapURL) }
    private val metricRegistry = MetricRegistry()
    override val attachments = NodeAttachmentService(metricRegistry, database, configuration.attachmentContentCacheSizeBytes, configuration.attachmentCacheBound).tokenize()
    override val cordappProvider = CordappProviderImpl(cordappLoader, CordappConfigFileProvider(), attachments).tokenize()
    @Suppress("LeakingThis")
    override val keyManagementService = PersistentKeyManagementService(identityService, database).tokenize()
    private val servicesForResolution = ServicesForResolutionImpl(identityService, attachments, cordappProvider, validatedTransactions)
    @Suppress("LeakingThis")
    override val vaultService = NodeVaultService(clock, keyManagementService, servicesForResolution, database).tokenize()
    override val nodeProperties = NodePropertiesPersistentStore(StubbedNodeUniqueIdProvider::value, database)
    override val monitoringService = MonitoringService(metricRegistry).tokenize()
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
    private val transactionVerifierWorkerCount = 4
    @Suppress("LeakingThis")
    override val transactionVerifierService = InMemoryTransactionVerifierService(transactionVerifierWorkerCount).tokenize()
    override val contractUpgradeService = ContractUpgradeServiceImpl().tokenize()
    override val auditService = DummyAuditService().tokenize()

    @Suppress("LeakingThis")
    val smm = MultiThreadedStateMachineManager(this, checkpointStorage, MultiThreadedStateMachineExecutor(configuration.enterpriseConfiguration.tuning.flowThreadPoolSize), database, newSecureRandom(), ReusableLatch(), cordappLoader.appClassLoader)
    // TODO Making this non-lateinit requires MockNode being able to create a blank InMemoryMessaging instance
    private lateinit var network: MessagingService

    private val cordappServices = MutableClassToInstanceMap.create<SerializeAsToken>()
    val flowFactories = ConcurrentHashMap<Class<out FlowLogic<*>>, InitiatedFlowFactory<*>>()

    override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage(database)

    override val rpcFlows = ArrayList<Class<out FlowLogic<*>>>()

    override val networkService: MessagingService get() = network

    override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
        return flowFactories[initiatingFlowClass]
    }

    override fun loadState(stateRef: StateRef): TransactionState<*> {
        return servicesForResolution.loadState(stateRef)
    }

    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return servicesForResolution.loadStates(stateRefs)
    }

    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
        require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
        return cordappServices.getInstance(type)
                ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
    }

    override fun jdbcSession(): Connection = database.createSession()

    override fun registerUnloadHandler(runOnStop: () -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun <T : Any> T.tokenize(): T {
        tokenizableServices?.add(this)
                ?: throw IllegalStateException("The tokenisable services list has already been finialised")
        return this
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

    private fun makeMessagingService(): MessagingService {
        return P2PMessagingClient(
                config = configuration,
                versionInfo = versionInfo,
                serverAddress = configuration.messagingServerAddress
                        ?: NetworkHostAndPort("localhost", configuration.p2pAddress.port),
                nodeExecutor = AffinityExecutor.ServiceAffinityExecutor("Flow Worker", 1),
                database = database,
                networkMap = networkMapCache,
                metricRegistry = metricRegistry,
                isDrainingModeOn = nodeProperties.flowsDrainingMode::isEnabled,
                drainingModeWasChangedEvents = nodeProperties.flowsDrainingMode.values
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
                    rpcClientContext = if (configuration.shouldInitCrashShell()) AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classloader) else null) //even Shell embeded in the node connects via RPC to the node
        }
    }

    fun start() {
        initialiseSerialization()

        // TODO First thing we do is create the MessagingService. This should have been done by the c'tor but it's not
        // possible (yet) to due restriction from MockNode
        network = makeMessagingService().tokenize()

        // TODO
        configuration.configureWithDevSSLCertificate()
        val trustRoot = DEV_ROOT_CA.certificate
        val nodeCa = configuration.loadNodeKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_CA)

        networkMapClient?.start(trustRoot)

        servicesForResolution.start(networkParameters)
        persistentNetworkMapCache.start(networkParameters.notaries)

        database.hikariStart(configuration.dataSourceProperties, configuration.database, schemaService)
        identityService.start(trustRoot, listOf(myInfo.legalIdentitiesAndCerts.first().certificate, nodeCa))

        database.transaction {
            networkMapCache.start()
        }

        // TODO
        //networkMapUpdater.start(trustRoot, signedNetParams.raw.hash, signedNodeInfo.raw.hash)

        startMessaging()

        database.transaction {
            identityService.loadIdentities(myInfo.legalIdentitiesAndCerts)
            attachments.start()
            cordappProvider.start(networkParameters.whitelistedContractImplementations)
            nodeProperties.start()
            keyManagementService.start(setOf(ourKeyPair))

            contractUpgradeService.start()
            vaultService.start()
            HibernateObserver.install(vaultService.rawUpdates, database.hibernateConfig, schemaService)

            val frozenTokenizableServices = tokenizableServices!!
            tokenizableServices = null

            smm.start(frozenTokenizableServices)
            runOnStop += { smm.stop(0) }
        }
    }

    fun stop() {
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
    }

    private fun startMessaging() {
        val client = network as P2PMessagingClient

        val messageBroker = if (!configuration.messagingServerExternal) {
            val brokerBindAddress = configuration.messagingServerAddress
                    ?: NetworkHostAndPort("0.0.0.0", configuration.p2pAddress.port)
            ArtemisMessagingServer(configuration, brokerBindAddress, networkParameters.maxMessageSize)
        } else {
            null
        }

        // Start up the embedded MQ server
        messageBroker?.apply {
            closeOnStop()
            start()
        }
        client.closeOnStop()
        client.start(
                myIdentity = myInfo.legalIdentities[0].owningKey,
                serviceIdentity = if (myInfo.legalIdentities.size == 1) null else myInfo.legalIdentities[1].owningKey,
                advertisedAddress = myInfo.addresses.single(),
                maxMessageSize = networkParameters.maxMessageSize,
                legalName = myInfo.legalIdentities[0].name.toString()
        )
    }


    private fun <T : AutoCloseable> T.closeOnStop(): T {
        runOnStop += this::close
        return this
    }

}