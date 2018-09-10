package net.corda.flowworker

import com.codahale.metrics.MetricRegistry
import com.google.common.collect.MutableClassToInstanceMap
import com.google.common.util.concurrent.MoreExecutors
import com.jcabi.manifests.Manifests
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.confidential.SwapIdentitiesHandler
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.newSecureRandom
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.CordaClock
import net.corda.node.SimpleClock
import net.corda.node.VersionInfo
import net.corda.node.internal.*
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.serialization.kryo.KryoServerSerializationScheme
import net.corda.node.services.ContractUpgradeHandler
import net.corda.node.services.FinalityHandler
import net.corda.node.services.NotaryChangeHandler
import net.corda.node.services.api.DummyAuditService
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.P2PMessagingClient
import net.corda.node.services.network.NetworkMapUpdater
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.persistence.*
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.*
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AffinityExecutor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.isH2Database
import net.corda.serialization.internal.*
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.Logger
import rx.Observable
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.sql.Connection
import java.time.Clock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class FlowWorkerServiceHub(override val configuration: NodeConfiguration, override val myInfo: NodeInfo, override val networkParameters: NetworkParameters, private val ourKeyPair: KeyPair, private val trustRoot: X509Certificate, private val nodeCa: X509Certificate) : ServiceHubInternal, SingletonSerializeAsToken() {

    override val clock: CordaClock = SimpleClock(Clock.systemUTC())
    private val versionInfo = getVersionInfo()
    private val cordappLoader = JarScanningCordappLoader.fromDirectories(configuration.cordappDirectories, versionInfo)
    private val sameVmNodeCounter = AtomicInteger()
    private val serverThread = AffinityExecutor.ServiceAffinityExecutor("FlowWorker thread-${sameVmNodeCounter.incrementAndGet()}", 1)
    private val busyNodeLatch = ReusableLatch()

    private val log: Logger get() = staticLog

    companion object {
        private val staticLog = contextLogger()
    }

    @Suppress("LeakingThis")
    private var tokenizableServices: MutableList<Any>? = mutableListOf(clock, this)
    private val runOnStop = ArrayList<() -> Any?>()

    init {
        (serverThread as? ExecutorService)?.let {
            runOnStop += {
                // We wait here, even though any in-flight messages should have been drained away because the
                // server thread can potentially have other non-messaging tasks scheduled onto it. The timeout value is
                // arbitrary and might be inappropriate.
                MoreExecutors.shutdownAndAwaitTermination(it, 50, TimeUnit.SECONDS)
            }
        }
    }

    override val schemaService = NodeSchemaService(cordappLoader.cordappSchemas, false).tokenize()
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

    override val networkMapCache = PersistentNetworkMapCache(database, identityService)
    private val checkpointStorage = DBCheckpointStorage()
    @Suppress("LeakingThis")
    override val validatedTransactions: WritableTransactionStorage = DBTransactionStorage(configuration.transactionCacheSizeBytes, database).tokenize()
    private val metricRegistry = MetricRegistry()
    override val attachments = NodeAttachmentService(metricRegistry, database, configuration.attachmentContentCacheSizeBytes, configuration.attachmentCacheBound).tokenize()
    override val cordappProvider = CordappProviderImpl(cordappLoader, CordappConfigFileProvider(), attachments).tokenize()
    @Suppress("LeakingThis")
    override val keyManagementService = PersistentKeyManagementService(identityService, database).tokenize()
    private val servicesForResolution = ServicesForResolutionImpl(identityService, attachments, cordappProvider, validatedTransactions)
    @Suppress("LeakingThis")
    override val vaultService = NodeVaultService(clock, keyManagementService, servicesForResolution, database, schemaService).tokenize()
    override val nodeProperties = NodePropertiesPersistentStore(StubbedNodeUniqueIdProvider::value, database)
    val flowLogicRefFactory = FlowLogicRefFactoryImpl(cordappLoader.appClassLoader)
    override val monitoringService = MonitoringService(metricRegistry).tokenize()

    override val networkMapUpdater: NetworkMapUpdater
        get() {
            throw NotImplementedError()
        }

    private val transactionVerifierWorkerCount = 4
    @Suppress("LeakingThis")
    override val transactionVerifierService = InMemoryTransactionVerifierService(transactionVerifierWorkerCount).tokenize()
    override val contractUpgradeService = ContractUpgradeServiceImpl().tokenize()
    override val auditService = DummyAuditService().tokenize()

    @Suppress("LeakingThis")
    val smm = makeStateMachineManager()

    private fun makeStateMachineManager(): StateMachineManager {
        val executor = MultiThreadedStateMachineExecutor(configuration.enterpriseConfiguration.tuning.flowThreadPoolSize)
        runOnStop += { executor.shutdown() }
        return MultiThreadedStateMachineManager(
                this,
                checkpointStorage,
                executor,
                database,
                newSecureRandom(),
                busyNodeLatch,
                cordappLoader.appClassLoader
        )
    }

    // TODO Making this non-lateinit requires MockNode being able to create a blank InMemoryMessaging instance
    private lateinit var network: MessagingService

    private val cordappServices = MutableClassToInstanceMap.create<SerializeAsToken>()
    private val flowFactories = ConcurrentHashMap<Class<out FlowLogic<*>>, InitiatedFlowFactory<*>>()

    override val rpcFlows: ArrayList<Class<out FlowLogic<*>>>
        get() {
            throw NotImplementedError()
        }

    override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage(database)
    override val networkService: MessagingService get() = network

    /**
     * Completes once the node has successfully registered with the network map service
     * or has loaded network map data from local database
     */
    // TODO val nodeReadyFuture: CordaFuture<Unit> get() = networkMapCache.nodeReady.map { Unit }
    // TODO started

    private fun <T : Any> T.tokenize(): T {
        tokenizableServices?.add(this)
                ?: throw IllegalStateException("The tokenisable services list has already been finialised")
        return this
    }

    private fun <T : AutoCloseable> T.closeOnStop(): T {
        runOnStop += this::close
        return this
    }

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

    private fun makeMessagingService(): MessagingService {
        return P2PMessagingClient(
                config = configuration,
                versionInfo = versionInfo,
                serverAddress = configuration.messagingServerAddress!!,
                nodeExecutor = serverThread,
                database = database,
                networkMap = networkMapCache,
                metricRegistry = metricRegistry,
                isDrainingModeOn = nodeProperties.flowsDrainingMode::isEnabled,
                drainingModeWasChangedEvents = nodeProperties.flowsDrainingMode.values
        )
    }

    private fun registerCordappFlows() {
        cordappLoader.cordapps.flatMap { it.initiatedFlows }
                .forEach {
                    try {
                        registerInitiatedFlowInternal(smm, it, track = false)
                    } catch (e: NoSuchMethodException) {
                        log.error("${it.name}, as an initiated flow, must have a constructor with a single parameter " +
                                "of type ${Party::class.java.name}")
                    } catch (e: Exception) {
                        log.error("Unable to register initiated flow ${it.name}", e)
                    }
                }
    }

    // TODO remove once not needed
    private fun deprecatedFlowConstructorMessage(flowClass: Class<*>): String {
        return "Installing flow factory for $flowClass accepting a ${Party::class.java.simpleName}, which is deprecated. " +
                "It should accept a ${FlowSession::class.java.simpleName} instead"
    }

    private fun <F : FlowLogic<*>> registerInitiatedFlowInternal(smm: StateMachineManager, initiatedFlow: Class<F>, track: Boolean): Observable<F> {
        val constructors = initiatedFlow.declaredConstructors.associateBy { it.parameterTypes.toList() }
        val flowSessionCtor = constructors[listOf(FlowSession::class.java)]?.apply { isAccessible = true }
        val ctor: (FlowSession) -> F = if (flowSessionCtor == null) {
            // Try to fallback to a Party constructor
            val partyCtor = constructors[listOf(Party::class.java)]?.apply { isAccessible = true }
            if (partyCtor == null) {
                throw IllegalArgumentException("$initiatedFlow must have a constructor accepting a ${FlowSession::class.java.name}")
            } else {
                log.warn(deprecatedFlowConstructorMessage(initiatedFlow))
            }
            { flowSession: FlowSession -> uncheckedCast(partyCtor.newInstance(flowSession.counterparty)) }
        } else {
            { flowSession: FlowSession -> uncheckedCast(flowSessionCtor.newInstance(flowSession)) }
        }
        val initiatingFlow = initiatedFlow.requireAnnotation<InitiatedBy>().value.java
        val (version, classWithAnnotation) = initiatingFlow.flowVersionAndInitiatingClass
        require(classWithAnnotation == initiatingFlow) {
            "${InitiatedBy::class.java.name} must point to ${classWithAnnotation.name} and not ${initiatingFlow.name}"
        }
        val flowFactory = InitiatedFlowFactory.CorDapp(version, initiatedFlow.appName, ctor)
        val observable = internalRegisterFlowFactory(smm, initiatingFlow, flowFactory, initiatedFlow, track)
        log.info("Registered ${initiatingFlow.name} to initiate ${initiatedFlow.name} (version $version)")
        return observable
    }

    private fun <F : FlowLogic<*>> internalRegisterFlowFactory(smm: StateMachineManager,
                                                               initiatingFlowClass: Class<out FlowLogic<*>>,
                                                               flowFactory: InitiatedFlowFactory<F>,
                                                               initiatedFlowClass: Class<F>,
                                                               track: Boolean): Observable<F> {
        val observable = if (track) {
            smm.changes.filter { it is StateMachineManager.Change.Add }.map { it.logic }.ofType(initiatedFlowClass)
        } else {
            Observable.empty()
        }
        flowFactories[initiatingFlowClass] = flowFactory
        return observable
    }

    /**
     * Installs a flow that's core to the Corda platform. Unlike CorDapp flows which are versioned individually using
     * [InitiatingFlow.version], core flows have the same version as the node's platform version. To cater for backwards
     * compatibility [flowFactory] provides a second parameter which is the platform version of the initiating party.
     */
    @VisibleForTesting
    fun installCoreFlow(clientFlowClass: KClass<out FlowLogic<*>>, flowFactory: (FlowSession) -> FlowLogic<*>) {
        require(clientFlowClass.java.flowVersionAndInitiatingClass.first == 1) {
            "${InitiatingFlow::class.java.name}.version not applicable for core flows; their version is the node's platform version"
        }
        flowFactories[clientFlowClass.java] = InitiatedFlowFactory.Core(flowFactory)
        log.debug { "Installed core flow ${clientFlowClass.java.name}" }
    }

    private fun installCoreFlows() {
        installCoreFlow(FinalityFlow::class, ::FinalityHandler)
        installCoreFlow(NotaryChangeFlow::class, ::NotaryChangeHandler)
        installCoreFlow(ContractUpgradeFlow.Initiate::class, ::ContractUpgradeHandler)
        installCoreFlow(SwapIdentitiesFlow::class, ::SwapIdentitiesHandler)
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

    fun start() {
        log.info("Flow Worker starting up ...")

        initialiseSerialization()

        // TODO First thing we do is create the MessagingService. This should have been done by the c'tor but it's not
        // possible (yet) to due restriction from MockNode
        network = makeMessagingService().tokenize()

        installCoreFlows()
        registerCordappFlows()

        servicesForResolution.start(networkParameters)

        val isH2Database = isH2Database(configuration.dataSourceProperties.getProperty("dataSource.url", ""))
        val schemas = if (isH2Database) schemaService.internalSchemas() else schemaService.schemaOptions.keys

        database.startHikariPool(configuration.dataSourceProperties, configuration.database, schemas)
        identityService.start(trustRoot, listOf(myInfo.legalIdentitiesAndCerts.first().certificate, nodeCa))

        database.transaction {
            networkMapCache.start(networkParameters.notaries)
        }

        identityService.ourNames = myInfo.legalIdentities.map { it.name }.toSet()
        startMessagingService()

        database.transaction {
            identityService.loadIdentities(myInfo.legalIdentitiesAndCerts)
            attachments.start()
            cordappProvider.start(networkParameters.whitelistedContractImplementations)
            nodeProperties.start()
            keyManagementService.start(setOf(ourKeyPair))

            contractUpgradeService.start()
            vaultService.start()

            val frozenTokenizableServices = tokenizableServices!!
            tokenizableServices = null

            runOnStop += { smm.stop(0) }
            smm.start(frozenTokenizableServices)
        }
    }

    fun stop() {
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
    }

    private fun startMessagingService() {
        val client = network as P2PMessagingClient
        Node.printBasicNodeInfo("Advertised P2P messaging addresses", myInfo.addresses.joinToString())
        client.closeOnStop()
        client.start(
                myIdentity = myInfo.legalIdentities[0].owningKey,
                serviceIdentity = null,
                maxMessageSize = networkParameters.maxMessageSize,
                legalName = myInfo.legalIdentities[0].name.toString()
        )
    }
}