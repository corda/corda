package net.corda.node.internal

import com.codahale.metrics.MetricRegistry
import com.google.common.collect.MutableClassToInstanceMap
import com.google.common.util.concurrent.MoreExecutors
import com.zaxxer.hikari.pool.HikariPool
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.confidential.SwapIdentitiesHandler
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sign
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.*
import net.corda.core.node.*
import net.corda.core.node.services.*
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.*
import net.corda.node.CordaClock
import net.corda.node.VersionInfo
import net.corda.node.cordapp.CordappLoader
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.node.internal.rpc.proxies.AuthenticatedRpcOpsProxy
import net.corda.node.internal.rpc.proxies.ExceptionSerialisingRpcOpsProxy
import net.corda.node.services.ContractUpgradeHandler
import net.corda.node.services.FinalityHandler
import net.corda.node.services.NotaryChangeHandler
import net.corda.node.services.api.*
import net.corda.node.services.config.*
import net.corda.node.services.config.rpc.NodeRpcOptions
import net.corda.node.services.config.shell.toShellConfig
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.events.ScheduledActivityObserver
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.KeyManagementServiceInternal
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.services.network.NetworkMapUpdater
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.persistence.*
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.*
import net.corda.node.services.transactions.*
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.JVMAgentRegistry
import net.corda.node.utilities.NamedThreadFactory
import net.corda.node.utilities.NodeBuildProperties
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.DISTRIBUTED_NOTARY_ALIAS_PREFIX
import net.corda.nodeapi.internal.crypto.X509Utilities.NODE_IDENTITY_ALIAS_PREFIX
import net.corda.nodeapi.internal.persistence.*
import net.corda.nodeapi.internal.storeLegalIdentity
import net.corda.tools.shell.InteractiveShell
import org.apache.activemq.artemis.utils.ReusableLatch
import org.hibernate.type.descriptor.java.JavaTypeDescriptorRegistry
import org.slf4j.Logger
import rx.Observable
import rx.Scheduler
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Paths
import java.security.KeyPair
import java.security.KeyStoreException
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.collections.set
import kotlin.reflect.KClass
import net.corda.core.crypto.generateKeyPair as cryptoGenerateKeyPair

/**
 * A base node implementation that can be customised either for production (with real implementations that do real
 * I/O), or a mock implementation suitable for unit test environments.
 *
 * Marked as SingletonSerializeAsToken to prevent the invisible reference to AbstractNode in the ServiceHub accidentally
 * sweeping up the Node into the Kryo checkpoint serialization via any flows holding a reference to ServiceHub.
 */
// TODO Log warning if this node is a notary but not one of the ones specified in the network parameters, both for core and custom
abstract class AbstractNode<S>(val configuration: NodeConfiguration,
                               val platformClock: CordaClock,
                               protected val versionInfo: VersionInfo,
                               protected val cordappLoader: CordappLoader,
                               protected val serverThread: AffinityExecutor.ServiceAffinityExecutor,
                               private val busyNodeLatch: ReusableLatch = ReusableLatch()) : SingletonSerializeAsToken() {

    protected abstract val log: Logger

    @Suppress("LeakingThis")
    private var tokenizableServices: MutableList<Any>? = mutableListOf(platformClock, this)
    protected val runOnStop = ArrayList<() -> Any?>()

    init {
        (serverThread as? ExecutorService)?.let {
            runOnStop += {
                // We wait here, even though any in-flight messages should have been drained away because the
                // server thread can potentially have other non-messaging tasks scheduled onto it. The timeout value is
                // arbitrary and might be inappropriate.
                MoreExecutors.shutdownAndAwaitTermination(it, 50, SECONDS)
            }
        }
    }

    val schemaService = NodeSchemaService(cordappLoader.cordappSchemas, configuration.notary != null).tokenize()
    val identityService = PersistentIdentityService().tokenize()
    val database: CordaPersistence = createCordaPersistence(
            configuration.database,
            identityService::wellKnownPartyFromX500Name,
            identityService::wellKnownPartyFromAnonymous,
            schemaService,
            configuration.dataSourceProperties
    )
    init {
        // TODO Break cyclic dependency
        identityService.database = database
    }
    val networkMapCache = PersistentNetworkMapCache(database, identityService, configuration.myLegalName).tokenize()
    val checkpointStorage = DBCheckpointStorage()
    @Suppress("LeakingThis")
    val transactionStorage = makeTransactionStorage(configuration.transactionCacheSizeBytes).tokenize()
    val networkMapClient: NetworkMapClient? = configuration.networkServices?.let { NetworkMapClient(it.networkMapURL, versionInfo) }
    private val metricRegistry = MetricRegistry()
    val attachments = NodeAttachmentService(metricRegistry, database, configuration.attachmentContentCacheSizeBytes, configuration.attachmentCacheBound).tokenize()
    val cordappProvider = CordappProviderImpl(cordappLoader, CordappConfigFileProvider(), attachments).tokenize()
    @Suppress("LeakingThis")
    val keyManagementService = makeKeyManagementService(identityService).tokenize()
    val servicesForResolution = ServicesForResolutionImpl(identityService, attachments, cordappProvider, transactionStorage)
    @Suppress("LeakingThis")
    val vaultService = makeVaultService(keyManagementService, servicesForResolution, database).tokenize()
    val nodeProperties = NodePropertiesPersistentStore(StubbedNodeUniqueIdProvider::value, database)
    val flowLogicRefFactory = FlowLogicRefFactoryImpl(cordappLoader.appClassLoader)
    val monitoringService = MonitoringService(metricRegistry).tokenize()
    val networkMapUpdater = NetworkMapUpdater(
            networkMapCache,
            NodeInfoWatcher(
                    configuration.baseDirectory,
                    @Suppress("LeakingThis")
                    rxIoScheduler,
                    Duration.ofMillis(configuration.additionalNodeInfoPollingFrequencyMsec)
            ),
            networkMapClient,
            configuration.baseDirectory,
            configuration.extraNetworkMapKeys
    ).closeOnStop()
    @Suppress("LeakingThis")
    val transactionVerifierService = InMemoryTransactionVerifierService(transactionVerifierWorkerCount).tokenize()
    val contractUpgradeService = ContractUpgradeServiceImpl().tokenize()
    val auditService = DummyAuditService().tokenize()
    @Suppress("LeakingThis")
    protected val network: MessagingService = makeMessagingService().tokenize()
    val services = ServiceHubInternalImpl().tokenize()
    @Suppress("LeakingThis")
    val smm = makeStateMachineManager()
    val flowStarter = FlowStarterImpl(smm, flowLogicRefFactory)
    private val schedulerService = NodeSchedulerService(
            platformClock,
            database,
            flowStarter,
            servicesForResolution,
            flowLogicRefFactory,
            nodeProperties,
            configuration.drainingModePollPeriod,
            unfinishedSchedules = busyNodeLatch
    ).tokenize().closeOnStop()

    private val cordappServices = MutableClassToInstanceMap.create<SerializeAsToken>()
    private val flowFactories = ConcurrentHashMap<Class<out FlowLogic<*>>, InitiatedFlowFactory<*>>()
    private val shutdownExecutor = Executors.newSingleThreadExecutor()

    protected abstract val transactionVerifierWorkerCount: Int
    /**
     * Should be [rx.schedulers.Schedulers.io] for production,
     * or [rx.internal.schedulers.CachedThreadScheduler] (with shutdown registered with [runOnStop]) for shared-JVM testing.
     */
    protected abstract val rxIoScheduler: Scheduler

    /**
     * Completes once the node has successfully registered with the network map service
     * or has loaded network map data from local database.
     */
    val nodeReadyFuture: CordaFuture<Unit> get() = networkMapCache.nodeReady.map { Unit }

    open val serializationWhitelists: List<SerializationWhitelist> by lazy {
        cordappLoader.cordapps.flatMap { it.serializationWhitelists }
    }

    /** Set to non-null once [start] has been successfully called. */
    open val started get() = _started
    @Volatile
    private var _started: S? = null

    private fun <T : Any> T.tokenize(): T {
        tokenizableServices?.add(this) ?: throw IllegalStateException("The tokenisable services list has already been finalised")
        return this
    }

    protected fun <T : AutoCloseable> T.closeOnStop(): T {
        runOnStop += this::close
        return this
    }

    /** The implementation of the [CordaRPCOps] interface used by this node. */
    open fun makeRPCOps(): CordaRPCOps {
        val ops: CordaRPCOps = CordaRPCOpsImpl(services, smm, flowStarter) { shutdownExecutor.submit { stop() } }
        // Mind that order is relevant here.
        val proxies = listOf<(CordaRPCOps) -> CordaRPCOps>(::AuthenticatedRpcOpsProxy, { ExceptionSerialisingRpcOpsProxy(it, true) })
        return proxies.fold(ops) { delegate, decorate -> decorate(delegate) }
    }

    private fun initKeyStores(): X509Certificate {
        if (configuration.devMode) {
            configuration.configureWithDevSSLCertificate()
        }
        return validateKeyStores()
    }

    open fun generateAndSaveNodeInfo(): NodeInfo {
        check(started == null) { "Node has already been started" }
        log.info("Generating nodeInfo ...")
        val trustRoot = initKeyStores()
        val (identity, identityKeyPair) = obtainIdentity(notaryConfig = null)
        startDatabase()
        val nodeCa = configuration.signingCertificateStore.get()[X509Utilities.CORDA_CLIENT_CA]
        identityService.start(trustRoot, listOf(identity.certificate, nodeCa))
        return database.use {
            it.transaction {
                val (_, nodeInfoAndSigned) = updateNodeInfo(identity, identityKeyPair, publish = false)
                nodeInfoAndSigned.nodeInfo
            }
        }
    }

    fun clearNetworkMapCache() {
        Node.printBasicNodeInfo("Clearing network map cache entries")
        log.info("Starting clearing of network map cache entries...")
        startDatabase()
        database.use {
            networkMapCache.clearNetworkMapCache()
        }
    }

    open fun start(): S {
        check(started == null) { "Node has already been started" }

        if (configuration.devMode) {
            System.setProperty("co.paralleluniverse.fibers.verifyInstrumentation", "true")
        }
        log.info("Node starting up ...")

        val trustRoot = initKeyStores()
        val nodeCa = configuration.signingCertificateStore.get()[X509Utilities.CORDA_CLIENT_CA]
        initialiseJVMAgents()

        schemaService.mappedSchemasWarnings().forEach {
            val warning = it.toWarning()
            log.warn(warning)
            Node.printWarning(warning)
        }

        installCoreFlows()
        registerCordappFlows()
        services.rpcFlows += cordappLoader.cordapps.flatMap { it.rpcFlows }
        val rpcOps = makeRPCOps()
        startShell()
        networkMapClient?.start(trustRoot)

        val (netParams, signedNetParams) = NetworkParametersReader(trustRoot, networkMapClient, configuration.baseDirectory).read()
        log.info("Loaded network parameters: $netParams")
        check(netParams.minimumPlatformVersion <= versionInfo.platformVersion) {
            "Node's platform version is lower than network's required minimumPlatformVersion"
        }
        servicesForResolution.start(netParams)

        startDatabase()
        val (identity, identityKeyPair) = obtainIdentity(notaryConfig = null)
        identityService.start(trustRoot, listOf(identity.certificate, nodeCa))

        val (keyPairs, nodeInfoAndSigned, myNotaryIdentity) = database.transaction {
            networkMapCache.start(netParams.notaries)
            updateNodeInfo(identity, identityKeyPair, publish = true)
        }

        val (nodeInfo, signedNodeInfo) = nodeInfoAndSigned
        identityService.ourNames = nodeInfo.legalIdentities.map { it.name }.toSet()
        services.start(nodeInfo, netParams)
        networkMapUpdater.start(trustRoot, signedNetParams.raw.hash, signedNodeInfo.raw.hash)
        startMessagingService(rpcOps, nodeInfo, myNotaryIdentity, netParams)

        // Do all of this in a database transaction so anything that might need a connection has one.
        return database.transaction {
            identityService.loadIdentities(nodeInfo.legalIdentitiesAndCerts)
            attachments.start()
            cordappProvider.start(netParams.whitelistedContractImplementations)
            nodeProperties.start()
            keyManagementService.start(keyPairs)
            val notaryService = makeNotaryService(myNotaryIdentity)
            installCordaServices(myNotaryIdentity)
            contractUpgradeService.start()
            vaultService.start()
            ScheduledActivityObserver.install(vaultService, schedulerService, flowLogicRefFactory)

            val frozenTokenizableServices = tokenizableServices!!
            tokenizableServices = null

            verifyCheckpointsCompatible(frozenTokenizableServices)

            smm.start(frozenTokenizableServices)
            // Shut down the SMM so no Fibers are scheduled.
            runOnStop += { smm.stop(acceptableLiveFiberCountOnStop()) }
            (smm as? StateMachineManagerInternal)?.let {
                val flowMonitor = FlowMonitor(smm::snapshot, configuration.flowMonitorPeriodMillis, configuration.flowMonitorSuspensionLoggingThresholdMillis)
                runOnStop += flowMonitor::stop
                flowMonitor.start()
            }

            schedulerService.start()

            createStartedNode(nodeInfo, rpcOps, notaryService).also { _started = it }
        }
    }

    /** Subclasses must override this to create a "started" node of the desired type, using the provided machinery. */
    abstract fun createStartedNode(nodeInfo: NodeInfo, rpcOps: CordaRPCOps, notaryService: NotaryService?): S

    private fun verifyCheckpointsCompatible(tokenizableServices: List<Any>) {
        try {
            CheckpointVerifier.verifyCheckpointsCompatible(checkpointStorage, cordappProvider.cordapps, versionInfo.platformVersion, services, tokenizableServices)
        } catch (e: CheckpointIncompatibleException) {
            if (configuration.devMode) {
                Node.printWarning(e.message)
            } else {
                throw e
            }
        }
    }

    open fun startShell() {
        if (configuration.shouldInitCrashShell()) {
            val shellConfiguration = configuration.toShellConfig()
            shellConfiguration.sshdPort?.let {
                log.info("Binding Shell SSHD server on port $it.")
            }
            InteractiveShell.startShell(shellConfiguration, cordappLoader.appClassLoader)
        }
    }

    private fun updateNodeInfo(identity: PartyAndCertificate,
                               identityKeyPair: KeyPair,
                               publish: Boolean): Triple<MutableSet<KeyPair>, NodeInfoAndSigned, PartyAndCertificate?> {
        val keyPairs = mutableSetOf(identityKeyPair)

        val myNotaryIdentity = configuration.notary?.let {
            if (it.isClusterConfig) {
                val (notaryIdentity, notaryIdentityKeyPair) = obtainIdentity(it)
                keyPairs += notaryIdentityKeyPair
                notaryIdentity
            } else {
                // In case of a single notary service myNotaryIdentity will be the node's single identity.
                identity
            }
        }

        val potentialNodeInfo = NodeInfo(
                myAddresses(),
                setOf(identity, myNotaryIdentity).filterNotNull(),
                versionInfo.platformVersion,
                serial = 0
        )

        val nodeInfoFromDb = getPreviousNodeInfoIfPresent(identity)


        val nodeInfo = if (potentialNodeInfo == nodeInfoFromDb?.copy(serial = 0)) {
            // The node info hasn't changed. We use the one from the database to preserve the serial.
            log.debug("Node-info hasn't changed")
            nodeInfoFromDb
        } else {
            log.info("Node-info has changed so submitting update. Old node-info was $nodeInfoFromDb")
            val newNodeInfo = potentialNodeInfo.copy(serial = platformClock.millis())
            networkMapCache.addNode(newNodeInfo)
            log.info("New node-info: $newNodeInfo")
            newNodeInfo
        }

        val nodeInfoAndSigned = NodeInfoAndSigned(nodeInfo) { publicKey, serialised ->
            val privateKey = keyPairs.single { it.public == publicKey }.private
            privateKey.sign(serialised.bytes)
        }

        // Write the node-info file even if nothing's changed, just in case the file has been deleted.
        NodeInfoWatcher.saveToFile(configuration.baseDirectory, nodeInfoAndSigned)

        // Always republish on startup, it's treated by network map server as a heartbeat.
        if (publish && networkMapClient != null) {
            tryPublishNodeInfoAsync(nodeInfoAndSigned.signed, networkMapClient)
        }

        return Triple(keyPairs, nodeInfoAndSigned, myNotaryIdentity)
    }

    private fun getPreviousNodeInfoIfPresent(identity: PartyAndCertificate): NodeInfo? {
        val nodeInfosFromDb = networkMapCache.getNodesByLegalName(identity.name)

        return when (nodeInfosFromDb.size) {
            0 -> null
            1 -> nodeInfosFromDb[0]
            else -> {
                log.warn("Found more than one node registration with our legal name, this is only expected if our keypair has been regenerated")
                nodeInfosFromDb[0]
            }
        }
    }

    // Publish node info on startup and start task that sends every day a heartbeat - republishes node info.
    private fun tryPublishNodeInfoAsync(signedNodeInfo: SignedNodeInfo, networkMapClient: NetworkMapClient) {
        // By default heartbeat interval should be set to 1 day, but for testing we may change it.
        val republishProperty = System.getProperty("net.corda.node.internal.nodeinfo.publish.interval")
        val heartbeatInterval = if (republishProperty != null) {
            try {
                Duration.parse(republishProperty)
            } catch (e: DateTimeParseException) {
                1.days
            }
        } else {
            1.days
        }
        val executor = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory("Network Map Updater", Executors.defaultThreadFactory()))
        executor.submit(object : Runnable {
            override fun run() {
                val republishInterval = try {
                    networkMapClient.publish(signedNodeInfo)
                    heartbeatInterval
                } catch (t: Throwable) {
                    log.warn("Error encountered while publishing node info, will retry again", t)
                    // TODO: Exponential backoff? It should reach max interval of eventHorizon/2.
                    1.minutes
                }
                executor.schedule(this, republishInterval.toMinutes(), MINUTES)
            }
        })
    }

    protected abstract fun myAddresses(): List<NetworkHostAndPort>

    protected open fun makeStateMachineManager(): StateMachineManager {
        return SingleThreadedStateMachineManager(
                services,
                checkpointStorage,
                serverThread,
                database,
                newSecureRandom(),
                busyNodeLatch,
                cordappLoader.appClassLoader
        )
    }

    private class ServiceInstantiationException(cause: Throwable?) : CordaException("Service Instantiation Error", cause)

    private fun installCordaServices(myNotaryIdentity: PartyAndCertificate?) {
        val loadedServices = cordappLoader.cordapps.flatMap { it.services }
        filterServicesToInstall(loadedServices).forEach {
            try {
                installCordaService(flowStarter, it, myNotaryIdentity)
            } catch (e: NoSuchMethodException) {
                log.error("${it.name}, as a Corda service, must have a constructor with a single parameter of type " +
                        ServiceHub::class.java.name)
            } catch (e: ServiceInstantiationException) {
                log.error("Corda service ${it.name} failed to instantiate", e.cause)
            } catch (e: Exception) {
                log.error("Unable to install Corda service ${it.name}", e)
            }
        }
    }

    private fun filterServicesToInstall(loadedServices: List<Class<out SerializeAsToken>>): List<Class<out SerializeAsToken>> {
        val customNotaryServiceList = loadedServices.filter { isNotaryService(it) }
        if (customNotaryServiceList.isNotEmpty()) {
            if (configuration.notary?.custom == true) {
                require(customNotaryServiceList.size == 1) {
                    "Attempting to install more than one notary service: ${customNotaryServiceList.joinToString()}"
                }
            } else return loadedServices - customNotaryServiceList
        }
        return loadedServices
    }

    /**
     * If the [serviceClass] is a notary service, it will only be enable if the "custom" flag is set in
     * the notary configuration.
     */
    private fun isNotaryService(serviceClass: Class<*>) = NotaryService::class.java.isAssignableFrom(serviceClass)

    /**
     * This customizes the ServiceHub for each CordaService that is initiating flows.
     */
    // TODO Move this into its own file
    private class AppServiceHubImpl<T : SerializeAsToken>(private val serviceHub: ServiceHub, private val flowStarter: FlowStarter) : AppServiceHub, ServiceHub by serviceHub {
        lateinit var serviceInstance: T
        override fun <T> startTrackedFlow(flow: FlowLogic<T>): FlowProgressHandle<T> {
            val stateMachine = startFlowChecked(flow)
            return FlowProgressHandleImpl(
                    id = stateMachine.id,
                    returnValue = stateMachine.resultFuture,
                    progress = stateMachine.logic.track()?.updates ?: Observable.empty()
            )
        }

        override fun <T> startFlow(flow: FlowLogic<T>): FlowHandle<T> {
            val stateMachine = startFlowChecked(flow)
            return FlowHandleImpl(id = stateMachine.id, returnValue = stateMachine.resultFuture)
        }

        private fun <T> startFlowChecked(flow: FlowLogic<T>): FlowStateMachine<T> {
            val logicType = flow.javaClass
            require(logicType.isAnnotationPresent(StartableByService::class.java)) { "${logicType.name} was not designed for starting by a CordaService" }
            // TODO check service permissions
            // TODO switch from myInfo.legalIdentities[0].name to current node's identity as soon as available
            val context = InvocationContext.service(serviceInstance.javaClass.name, myInfo.legalIdentities[0].name)
            return flowStarter.startFlow(flow, context).getOrThrow()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AppServiceHubImpl<*>) return false
            return serviceHub == other.serviceHub
                    && flowStarter == other.flowStarter
                    && serviceInstance == other.serviceInstance
        }

        override fun hashCode() = Objects.hash(serviceHub, flowStarter, serviceInstance)
    }

    private fun <T : SerializeAsToken> installCordaService(flowStarter: FlowStarter, serviceClass: Class<T>, myNotaryIdentity: PartyAndCertificate?) {
        serviceClass.requireAnnotation<CordaService>()
        val service = try {
            val serviceContext = AppServiceHubImpl<T>(services, flowStarter)
            if (isNotaryService(serviceClass)) {
                myNotaryIdentity ?: throw IllegalStateException("Trying to install a notary service but no notary identity specified")
                val constructor = serviceClass.getDeclaredConstructor(AppServiceHub::class.java, PublicKey::class.java).apply { isAccessible = true }
                serviceContext.serviceInstance = constructor.newInstance(serviceContext, myNotaryIdentity.owningKey)
                serviceContext.serviceInstance
            } else {
                try {
                    val extendedServiceConstructor = serviceClass.getDeclaredConstructor(AppServiceHub::class.java).apply { isAccessible = true }
                    serviceContext.serviceInstance = extendedServiceConstructor.newInstance(serviceContext)
                    serviceContext.serviceInstance
                } catch (ex: NoSuchMethodException) {
                    val constructor = serviceClass.getDeclaredConstructor(ServiceHub::class.java).apply { isAccessible = true }
                    log.warn("${serviceClass.name} is using legacy CordaService constructor with ServiceHub parameter. " +
                            "Upgrade to an AppServiceHub parameter to enable updated API features.")
                    constructor.newInstance(services)
                }
            }
        } catch (e: InvocationTargetException) {
            throw ServiceInstantiationException(e.cause)
        }
        cordappServices.putInstance(serviceClass, service)

        if (service is NotaryService) handleCustomNotaryService(service)
        service.tokenize()
        log.info("Installed ${serviceClass.name} Corda service")
    }

    private fun handleCustomNotaryService(service: NotaryService) {
        runOnStop += service::stop
        installCoreFlow(NotaryFlow.Client::class, service::createServiceFlow)
        service.start()
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

    fun <T : FlowLogic<*>> registerInitiatedFlow(smm: StateMachineManager, initiatedFlowClass: Class<T>): Observable<T> {
        return registerInitiatedFlowInternal(smm, initiatedFlowClass, track = true)
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

    protected fun <F : FlowLogic<*>> internalRegisterFlowFactory(smm: StateMachineManager,
                                                                 initiatingFlowClass: Class<out FlowLogic<*>>,
                                                                 flowFactory: InitiatedFlowFactory<F>,
                                                                 initiatedFlowClass: Class<F>,
                                                                 track: Boolean): Observable<F> {
        val observable = if (track) {
            smm.changes.filter { it is StateMachineManager.Change.Add }.map { it.logic }.ofType(initiatedFlowClass)
        } else {
            Observable.empty()
        }
        check(initiatingFlowClass !in flowFactories.keys) {
            "$initiatingFlowClass is attempting to register multiple initiated flows"
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

    protected open fun makeTransactionStorage(transactionCacheSizeBytes: Long): WritableTransactionStorage {
        return DBTransactionStorage(transactionCacheSizeBytes, database)
    }

    @VisibleForTesting
    protected open fun acceptableLiveFiberCountOnStop(): Int = 0

    private fun getCertificateStores(): AllCertificateStores? {
        return try {
            // The following will throw IOException if key file not found or KeyStoreException if keystore password is incorrect.
            val sslKeyStore = configuration.p2pSslOptions.keyStore.get()
            val identitiesKeyStore = configuration.signingCertificateStore.get()
            val trustStore = configuration.p2pSslOptions.trustStore.get()
            AllCertificateStores(trustStore, sslKeyStore, identitiesKeyStore)
        } catch (e: KeyStoreException) {
            log.warn("At least one of the keystores or truststore passwords does not match configuration.")
            null
        } catch (e: IOException) {
            log.error("IO exception while trying to validate keystores and truststore", e)
            null
        }
    }

    private data class AllCertificateStores(val trustStore: CertificateStore, val sslKeyStore: CertificateStore, val identitiesKeyStore: CertificateStore)

    private fun validateKeyStores(): X509Certificate {
        // Step 1. Check trustStore, sslKeyStore and identitiesKeyStore exist.
        val certStores = getCertificateStores()
        requireNotNull(certStores) {
            "One or more keyStores (identity or TLS) or trustStore not found. " +
                    "Please either copy your existing keys and certificates from another node, " +
                    "or if you don't have one yet, fill out the config file and run corda.jar --initial-registration. " +
                    "Read more at: https://docs.corda.net/permissioning.html"
        }
        // Step 2. Check that trustStore contains the correct key-alias entry.
        require(X509Utilities.CORDA_ROOT_CA in certStores!!.trustStore) {
            "Alias for trustRoot key not found. Please ensure you have an updated trustStore file."
        }
        // Step 3. Check that tls keyStore contains the correct key-alias entry.
        require(X509Utilities.CORDA_CLIENT_TLS in certStores.sslKeyStore) {
            "Alias for TLS key not found. Please ensure you have an updated TLS keyStore file."
        }

        // Step 4. Check that identity keyStores contain the correct key-alias entry for Node CA.
        require(X509Utilities.CORDA_CLIENT_CA in certStores.identitiesKeyStore) {
            "Alias for Node CA key not found. Please ensure you have an updated identity keyStore file."
        }

        // Step 5. Check all cert paths chain to the trusted root.
        val trustRoot = certStores.trustStore[X509Utilities.CORDA_ROOT_CA]
        val sslCertChainRoot = certStores.sslKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_TLS) }.last()
        val nodeCaCertChainRoot = certStores.identitiesKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_CA) }.last()

        require(sslCertChainRoot == trustRoot) { "TLS certificate must chain to the trusted root." }
        require(nodeCaCertChainRoot == trustRoot) { "Client CA certificate must chain to the trusted root." }

        return trustRoot
    }

    // Specific class so that MockNode can catch it.
    class DatabaseConfigurationException(msg: String) : CordaException(msg)

    protected open fun startDatabase() {
        val props = configuration.dataSourceProperties
        if (props.isEmpty) throw DatabaseConfigurationException("There must be a database configured.")
        database.startHikariPool(props, configuration.database, schemaService.internalSchemas())
        // Now log the vendor string as this will also cause a connection to be tested eagerly.
        logVendorString(database, log)
    }

    private fun makeNotaryService(myNotaryIdentity: PartyAndCertificate?): NotaryService? {
        return configuration.notary?.let {
            makeCoreNotaryService(it, myNotaryIdentity).also {
                it.tokenize()
                runOnStop += it::stop
                installCoreFlow(NotaryFlow.Client::class, it::createServiceFlow)
                log.info("Running core notary: ${it.javaClass.name}")
                it.start()
            }
        }
    }

    protected open fun makeKeyManagementService(identityService: PersistentIdentityService): KeyManagementServiceInternal {
        // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
        // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
        // the identity key. But the infrastructure to make that easy isn't here yet.
        return PersistentKeyManagementService(identityService, database)
    }

    private fun makeCoreNotaryService(notaryConfig: NotaryConfig, myNotaryIdentity: PartyAndCertificate?): NotaryService {
        val notaryKey = myNotaryIdentity?.owningKey
                ?: throw IllegalArgumentException("No notary identity initialized when creating a notary service")
        return notaryConfig.run {
            when {
                raft != null -> {
                    val uniquenessProvider = RaftUniquenessProvider(configuration.baseDirectory, configuration.p2pSslOptions, database, platformClock, monitoringService.metrics, raft)
                    (if (validating) ::RaftValidatingNotaryService else ::RaftNonValidatingNotaryService)(services, notaryKey, uniquenessProvider)
                }
                bftSMaRt != null -> {
                    if (validating) throw IllegalArgumentException("Validating BFTSMaRt notary not supported")
                    BFTNonValidatingNotaryService(services, notaryKey, bftSMaRt, makeBFTCluster(notaryKey, bftSMaRt))
                }
                else -> (if (validating) ::ValidatingNotaryService else ::SimpleNotaryService)(services, notaryKey)
            }
        }
    }

    protected open fun makeBFTCluster(notaryKey: PublicKey, bftSMaRtConfig: BFTSMaRtConfiguration): BFTSMaRt.Cluster {
        return object : BFTSMaRt.Cluster {
            override fun waitUntilAllReplicasHaveInitialized() {
                log.warn("A BFT replica may still be initializing, in which case the upcoming consensus change may cause it to spin.")
            }
        }
    }

    open fun stop() {
        // TODO: We need a good way of handling "nice to have" shutdown events, especially those that deal with the
        // network, including unsubscribing from updates from remote services. Possibly some sort of parameter to stop()
        // to indicate "Please shut down gracefully" vs "Shut down now".
        // Meanwhile, we let the remote service send us updates until the acknowledgment buffer overflows and it
        // unsubscribes us forcibly, rather than blocking the shutdown process.

        // Run shutdown hooks in opposite order to starting.
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
        shutdownExecutor.shutdown()
        _started = null
    }

    protected abstract fun makeMessagingService(): MessagingService

    protected abstract fun startMessagingService(rpcOps: RPCOps,
                                                 nodeInfo: NodeInfo,
                                                 myNotaryIdentity: PartyAndCertificate?,
                                                 networkParameters: NetworkParameters)

    private fun obtainIdentity(notaryConfig: NotaryConfig?): Pair<PartyAndCertificate, KeyPair> {
        val keyStore = configuration.signingCertificateStore.get()

        val (id, singleName) = if (notaryConfig == null || !notaryConfig.isClusterConfig) {
            // Node's main identity or if it's a single node notary.
            Pair(NODE_IDENTITY_ALIAS_PREFIX, configuration.myLegalName)
        } else {
            // The node is part of a distributed notary whose identity must already be generated beforehand.
            Pair(DISTRIBUTED_NOTARY_ALIAS_PREFIX, null)
        }
        // TODO: Integrate with Key management service?
        val privateKeyAlias = "$id-private-key"

        if (privateKeyAlias !in keyStore) {
            singleName ?: throw IllegalArgumentException(
                    "Unable to find in the key store the identity of the distributed notary the node is part of")
            log.info("$privateKeyAlias not found in key store, generating fresh key!")
            // TODO This check shouldn't be needed
            check(singleName == configuration.myLegalName)
            keyStore.storeLegalIdentity(privateKeyAlias, generateKeyPair())
        }

        val (x509Cert, keyPair) = keyStore.query { getCertificateAndKeyPair(privateKeyAlias) }

        // TODO: Use configuration to indicate composite key should be used instead of public key for the identity.
        val compositeKeyAlias = "$id-composite-key"
        val certificates = if (compositeKeyAlias in keyStore) {
            // Use composite key instead if it exists
            val certificate = keyStore[compositeKeyAlias]
            // We have to create the certificate chain for the composite key manually, this is because we don't have a keystore
            // provider that understand compositeKey-privateKey combo. The cert chain is created using the composite key certificate +
            // the tail of the private key certificates, as they are both signed by the same certificate chain.
            listOf(certificate) + keyStore.query { getCertificateChain(privateKeyAlias) }.drop(1)
        } else {
            keyStore.query { getCertificateChain(privateKeyAlias) }.let {
                check(it[0] == x509Cert) { "Certificates from key store do not line up!" }
                it
            }
        }

        val subject = CordaX500Name.build(certificates[0].subjectX500Principal)
        if (singleName != null && subject != singleName) {
            throw ConfigurationException("The name '$singleName' for $id doesn't match what's in the key store: $subject")
        } else if (notaryConfig != null && notaryConfig.isClusterConfig && notaryConfig.serviceLegalName != null && subject != notaryConfig.serviceLegalName) {
            // Note that we're not checking if `notaryConfig.serviceLegalName` is not present for backwards compatibility.
            throw ConfigurationException("The name of the notary service '${notaryConfig.serviceLegalName}' for $id doesn't " +
                    "match what's in the key store: $subject. You might need to adjust the configuration of `notary.serviceLegalName`.")
        }

        val certPath = X509Utilities.buildCertPath(certificates)
        return Pair(PartyAndCertificate(certPath), keyPair)
    }

    protected open fun generateKeyPair() = cryptoGenerateKeyPair()

    protected open fun makeVaultService(keyManagementService: KeyManagementService,
                                        services: ServicesForResolution,
                                        database: CordaPersistence): VaultServiceInternal {
        return NodeVaultService(platformClock, keyManagementService, services, database, schemaService)
    }

    /** Load configured JVM agents */
    private fun initialiseJVMAgents() {
        configuration.jmxMonitoringHttpPort?.let { port ->
            requireNotNull(NodeBuildProperties.JOLOKIA_AGENT_VERSION) {
                "'jolokiaAgentVersion' missing from build properties"
            }
            log.info("Starting Jolokia agent on HTTP port: $port")
            val libDir = Paths.get(configuration.baseDirectory.toString(), "drivers")
            val jarFilePath = JVMAgentRegistry.resolveAgentJar(
                    "jolokia-jvm-${NodeBuildProperties.JOLOKIA_AGENT_VERSION}-agent.jar", libDir)
                    ?: throw Error("Unable to locate agent jar file")
            log.info("Agent jar file: $jarFilePath")
            JVMAgentRegistry.attach("jolokia", "port=$port", jarFilePath)
        }
    }

    inner class ServiceHubInternalImpl : SingletonSerializeAsToken(), ServiceHubInternal, ServicesForResolution by servicesForResolution {
        override val rpcFlows = ArrayList<Class<out FlowLogic<*>>>()
        override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage(database)
        override val identityService: IdentityService get() = this@AbstractNode.identityService
        override val keyManagementService: KeyManagementService get() = this@AbstractNode.keyManagementService
        override val schemaService: SchemaService get() = this@AbstractNode.schemaService
        override val validatedTransactions: WritableTransactionStorage get() = this@AbstractNode.transactionStorage
        override val cordappProvider: CordappProviderInternal get() = this@AbstractNode.cordappProvider
        override val networkMapCache: NetworkMapCacheInternal get() = this@AbstractNode.networkMapCache
        override val vaultService: VaultServiceInternal get() = this@AbstractNode.vaultService
        override val nodeProperties: NodePropertiesStore get() = this@AbstractNode.nodeProperties
        override val database: CordaPersistence get() = this@AbstractNode.database
        override val monitoringService: MonitoringService get() = this@AbstractNode.monitoringService
        override val transactionVerifierService: TransactionVerifierService get() = this@AbstractNode.transactionVerifierService
        override val contractUpgradeService: ContractUpgradeService get() = this@AbstractNode.contractUpgradeService
        override val auditService: AuditService get() = this@AbstractNode.auditService
        override val attachments: AttachmentStorageInternal get() = this@AbstractNode.attachments
        override val networkService: MessagingService get() = network
        override val clock: Clock get() = platformClock
        override val configuration: NodeConfiguration get() = this@AbstractNode.configuration
        override val networkMapUpdater: NetworkMapUpdater get() = this@AbstractNode.networkMapUpdater

        private lateinit var _myInfo: NodeInfo
        override val myInfo: NodeInfo get() = _myInfo

        private lateinit var _networkParameters: NetworkParameters
        override val networkParameters: NetworkParameters get() = _networkParameters

        fun start(myInfo: NodeInfo, networkParameters: NetworkParameters) {
            this._myInfo = myInfo
            this._networkParameters = networkParameters
        }

        override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
            require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
            return cordappServices.getInstance(type)
                    ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
        }

        override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
            return flowFactories[initiatingFlowClass]
        }

        override fun jdbcSession(): Connection = database.createSession()

        // allows services to register handlers to be informed when the node stop method is called
        override fun registerUnloadHandler(runOnStop: () -> Unit) {
            this@AbstractNode.runOnStop += runOnStop
        }
    }
}

@VisibleForTesting
internal fun logVendorString(database: CordaPersistence, log: Logger) {
    database.transaction {
        log.info("Connected to ${connection.metaData.databaseProductName} database.")
    }
}

// TODO Move this into its own file
class FlowStarterImpl(private val smm: StateMachineManager, private val flowLogicRefFactory: FlowLogicRefFactory) : FlowStarter {
    override fun <T> startFlow(event: ExternalEvent.ExternalStartFlowEvent<T>): CordaFuture<FlowStateMachine<T>> {
        smm.deliverExternalEvent(event)
        return event.future
    }

    override fun <T> startFlow(logic: FlowLogic<T>, context: InvocationContext): CordaFuture<FlowStateMachine<T>> {
        val startFlowEvent = object : ExternalEvent.ExternalStartFlowEvent<T>, DeduplicationHandler {
            override fun insideDatabaseTransaction() {}

            override fun afterDatabaseTransaction() {}

            override val externalCause: ExternalEvent
                get() = this
            override val deduplicationHandler: DeduplicationHandler
                get() = this

            override val flowLogic: FlowLogic<T>
                get() = logic
            override val context: InvocationContext
                get() = context

            override fun wireUpFuture(flowFuture: CordaFuture<FlowStateMachine<T>>) {
                _future.captureLater(flowFuture)
            }

            private val _future = openFuture<FlowStateMachine<T>>()
            override val future: CordaFuture<FlowStateMachine<T>>
                get() = _future

        }
        return startFlow(startFlowEvent)
    }

    override fun <T> invokeFlowAsync(
            logicType: Class<out FlowLogic<T>>,
            context: InvocationContext,
            vararg args: Any?): CordaFuture<FlowStateMachine<T>> {
        val logicRef = flowLogicRefFactory.createForRPC(logicType, *args)
        val logic: FlowLogic<T> = uncheckedCast(flowLogicRefFactory.toFlowLogic(logicRef))
        return startFlow(logic, context)
    }
}

class ConfigurationException(message: String) : CordaException(message)

// TODO This is no longer used by AbstractNode and can be moved elsewhere
fun configureDatabase(hikariProperties: Properties,
                      databaseConfig: DatabaseConfig,
                      wellKnownPartyFromX500Name: (CordaX500Name) -> Party?,
                      wellKnownPartyFromAnonymous: (AbstractParty) -> Party?,
                      schemaService: SchemaService = NodeSchemaService(),
                      internalSchemas: Set<MappedSchema> = NodeSchemaService().internalSchemas()): CordaPersistence {
    val persistence = createCordaPersistence(databaseConfig, wellKnownPartyFromX500Name, wellKnownPartyFromAnonymous, schemaService, hikariProperties)
    persistence.startHikariPool(hikariProperties, databaseConfig, internalSchemas)
    return persistence
}

fun createCordaPersistence(databaseConfig: DatabaseConfig,
                           wellKnownPartyFromX500Name: (CordaX500Name) -> Party?,
                           wellKnownPartyFromAnonymous: (AbstractParty) -> Party?,
                           schemaService: SchemaService,
                           hikariProperties: Properties): CordaPersistence {
    // Register the AbstractPartyDescriptor so Hibernate doesn't warn when encountering AbstractParty. Unfortunately
    // Hibernate warns about not being able to find a descriptor if we don't provide one, but won't use it by default
    // so we end up providing both descriptor and converter. We should re-examine this in later versions to see if
    // either Hibernate can be convinced to stop warning, use the descriptor by default, or something else.
    JavaTypeDescriptorRegistry.INSTANCE.addDescriptor(AbstractPartyDescriptor(wellKnownPartyFromX500Name, wellKnownPartyFromAnonymous))
    val attributeConverters = listOf(AbstractPartyToX500NameAsStringConverter(wellKnownPartyFromX500Name, wellKnownPartyFromAnonymous))
    val jdbcUrl = hikariProperties.getProperty("dataSource.url", "")
    return CordaPersistence(databaseConfig, schemaService.schemaOptions.keys, jdbcUrl, attributeConverters)
}

fun CordaPersistence.startHikariPool(hikariProperties: Properties, databaseConfig: DatabaseConfig, schemas: Set<MappedSchema>) {
    try {
        val dataSource = DataSourceFactory.createDataSource(hikariProperties)
        val schemaMigration = SchemaMigration(schemas, dataSource, databaseConfig)
        schemaMigration.nodeStartup(dataSource.connection.use { DBCheckpointStorage().getCheckpointCount(it) != 0L })
        start(dataSource)
    } catch (ex: Exception) {
        when {
            ex is HikariPool.PoolInitializationException -> throw CouldNotCreateDataSourceException("Could not connect to the database. Please check your JDBC connection URL, or the connectivity to the database.", ex)
            ex.cause is ClassNotFoundException -> throw CouldNotCreateDataSourceException("Could not find the database driver class. Please add it to the 'drivers' folder. See: https://docs.corda.net/corda-configuration-file.html")
            ex is OutstandingDatabaseChangesException -> throw (DatabaseIncompatibleException(ex.message))
            else -> throw CouldNotCreateDataSourceException("Could not create the DataSource: ${ex.message}", ex)
        }
    }
}

fun clientSslOptionsCompatibleWith(nodeRpcOptions: NodeRpcOptions): ClientRpcSslOptions? {

    if (!nodeRpcOptions.useSsl || nodeRpcOptions.sslConfig == null) {
        return null
    }
    // Here we're using the node's RPC key store as the RPC client's trust store.
    return ClientRpcSslOptions(trustStorePath = nodeRpcOptions.sslConfig!!.keyStorePath, trustStorePassword = nodeRpcOptions.sslConfig!!.keyStorePassword)
}