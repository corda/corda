/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal

import com.codahale.metrics.MetricRegistry
import com.google.common.collect.MutableClassToInstanceMap
import com.google.common.util.concurrent.MoreExecutors
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.confidential.SwapIdentitiesHandler
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sign
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.StartableByService
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.Emoji
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowHandleImpl
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.core.messaging.RPCOps
import net.corda.core.node.AppServiceHub
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.days
import net.corda.core.utilities.debug
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.node.CordaClock
import net.corda.node.VersionInfo
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.node.internal.rpc.proxies.AuthenticatedRpcOpsProxy
import net.corda.node.internal.rpc.proxies.ExceptionMaskingRpcOpsProxy
import net.corda.node.internal.rpc.proxies.ExceptionSerialisingRpcOpsProxy
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.services.ContractUpgradeHandler
import net.corda.node.services.FinalityHandler
import net.corda.node.services.NotaryChangeHandler
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.DummyAuditService
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.NetworkMapCacheBaseInternal
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.api.SchedulerService
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.config.shell.toShellConfig
import net.corda.node.services.config.shouldInitCrashShell
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.events.ScheduledActivityObserver
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapCacheImpl
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.services.network.NetworkMapUpdater
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.persistence.AbstractPartyDescriptor
import net.corda.node.services.persistence.AbstractPartyToX500NameAsStringConverter
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.persistence.DBTransactionMappingStorage
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.persistence.NodePropertiesPersistentStore
import net.corda.node.services.persistence.RunOnceService
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.node.services.statemachine.FlowLogicRefFactoryImpl
import net.corda.node.services.statemachine.SingleThreadedStateMachineManager
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.statemachine.appName
import net.corda.node.services.statemachine.flowVersionAndInitiatingClass
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.BFTSMaRt
import net.corda.node.services.transactions.MySQLNonValidatingNotaryService
import net.corda.node.services.transactions.MySQLValidatingNotaryService
import net.corda.node.services.transactions.RaftNonValidatingNotaryService
import net.corda.node.services.transactions.RaftUniquenessProvider
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.JVMAgentRegistry
import net.corda.node.utilities.NamedThreadFactory
import net.corda.node.utilities.NodeBuildProperties
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.nodeapi.internal.persistence.isH2Database
import net.corda.nodeapi.internal.storeLegalIdentity
import net.corda.tools.shell.InteractiveShell
import org.apache.activemq.artemis.utils.ReusableLatch
import org.hibernate.type.descriptor.java.JavaTypeDescriptorRegistry
import org.slf4j.Logger
import rx.Observable
import rx.Scheduler
import java.io.IOException
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import java.nio.file.Paths
import java.security.KeyPair
import java.security.KeyStoreException
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

// In theory the NodeInfo for the node should be passed in, instead, however currently this is constructed by the
// AbstractNode. It should be possible to generate the NodeInfo outside of AbstractNode, so it can be passed in.
abstract class AbstractNode(val configuration: NodeConfiguration,
                            val platformClock: CordaClock,
                            protected val versionInfo: VersionInfo,
                            protected val cordappLoader: CordappLoader,
                            protected val busyNodeLatch: ReusableLatch = ReusableLatch()) : SingletonSerializeAsToken() {

    private class StartedNodeImpl<out N : AbstractNode>(
            override val internals: N,
            services: ServiceHubInternalImpl,
            override val info: NodeInfo,
            override val checkpointStorage: CheckpointStorage,
            override val smm: StateMachineManager,
            override val attachments: NodeAttachmentService,
            override val network: MessagingService,
            override val database: CordaPersistence,
            override val rpcOps: CordaRPCOps,
            flowStarter: FlowStarter,
            override val notaryService: NotaryService?) : StartedNode<N> {
        override val services: StartedNodeServices = object : StartedNodeServices, ServiceHubInternal by services, FlowStarter by flowStarter {}
    }

    protected abstract val log: Logger

    // We will run as much stuff in this single thread as possible to keep the risk of thread safety bugs low during the
    // low-performance prototyping period.
    protected abstract val serverThread: AffinityExecutor.ServiceAffinityExecutor

    private val cordappServices = MutableClassToInstanceMap.create<SerializeAsToken>()
    private val flowFactories = ConcurrentHashMap<Class<out FlowLogic<*>>, InitiatedFlowFactory<*>>()

    protected val services: ServiceHubInternal get() = _services
    private lateinit var _services: ServiceHubInternalImpl
    protected var myNotaryIdentity: PartyAndCertificate? = null
    protected lateinit var checkpointStorage: CheckpointStorage
    private lateinit var tokenizableServices: List<Any>
    protected lateinit var attachments: NodeAttachmentService
    protected lateinit var network: MessagingService
    protected val runOnStop = ArrayList<() -> Any?>()
    private val _nodeReadyFuture = openFuture<Unit>()
    protected var networkMapClient: NetworkMapClient? = null
    private lateinit var networkMapUpdater: NetworkMapUpdater
    lateinit var securityManager: RPCSecurityManager

    private val shutdownExecutor = Executors.newSingleThreadExecutor()

    /** Completes once the node has successfully registered with the network map service
     * or has loaded network map data from local database */
    val nodeReadyFuture: CordaFuture<Unit> get() = _nodeReadyFuture

    open val serializationWhitelists: List<SerializationWhitelist> by lazy {
        cordappLoader.cordapps.flatMap { it.serializationWhitelists }
    }

    /** Set to non-null once [start] has been successfully called. */
    open val started get() = _started
    @Volatile private var _started: StartedNode<AbstractNode>? = null

    /** The implementation of the [CordaRPCOps] interface used by this node. */
    open fun makeRPCOps(flowStarter: FlowStarter, database: CordaPersistence, smm: StateMachineManager): CordaRPCOps {

        val ops: CordaRPCOps = CordaRPCOpsImpl(services, smm, database, flowStarter, { shutdownExecutor.submit { stop() } })
        val proxies = mutableListOf<(CordaRPCOps) -> CordaRPCOps>()
        // Mind that order is relevant here.
        proxies += ::AuthenticatedRpcOpsProxy
        if (!configuration.devMode) {
            proxies += { it -> ExceptionMaskingRpcOpsProxy(it, true) }
        }
        proxies += { it -> ExceptionSerialisingRpcOpsProxy(it, configuration.devMode) }
        return proxies.fold(ops) { delegate, decorate -> decorate(delegate) }
    }

    private fun initCertificate() {
        if (configuration.devMode) {
            log.warn("The Corda node is running in developer mode. This is not suitable for production usage.")
            configuration.configureWithDevSSLCertificate()
        } else {
            log.info("The Corda node is running in production mode. If this is a developer environment you can set 'devMode=true' in the node.conf file.")
        }
        validateKeystore()
    }

    open fun generateAndSaveNodeInfo(): NodeInfo {
        check(started == null) { "Node has already been started" }
        log.info("Generating nodeInfo ...")
        initCertificate()
        val schemaService = NodeSchemaService(cordappLoader.cordappSchemas)
        val (identity, identityKeyPair) = obtainIdentity(notaryConfig = null)
        return initialiseDatabasePersistence(schemaService, makeIdentityService(identity.certificate)).use {
            it.transaction {
                // TODO The fact that we need to specify an empty list of notaries just to generate our node info looks
                // like a design smell.
                val persistentNetworkMapCache = PersistentNetworkMapCache(database, notaries = emptyList())
                persistentNetworkMapCache.start()
                val (_, nodeInfo) = updateNodeInfo(persistentNetworkMapCache, null, identity, identityKeyPair)
                nodeInfo
            }
        }
    }

    open fun start(): StartedNode<AbstractNode> {
        check(started == null) { "Node has already been started" }
        if (configuration.devMode) {
            System.setProperty("co.paralleluniverse.fibers.verifyInstrumentation", "true")
            Emoji.renderIfSupported { Node.printWarning("This node is running in developer mode! ${Emoji.developer} This is not safe for production deployment.") }
        }
        log.info("Node starting up ...")
        initCertificate()
        initialiseJVMAgents()
        val schemaService = NodeSchemaService(cordappLoader.cordappSchemas, configuration.notary != null)
        val (identity, identityKeyPair) = obtainIdentity(notaryConfig = null)
        val identityService = makeIdentityService(identity.certificate)

        networkMapClient = configuration.networkServices?.let { NetworkMapClient(it.networkMapURL, identityService.trustRoot) }

        val networkParameters = NetworkParametersReader(identityService.trustRoot, networkMapClient, configuration.baseDirectory).networkParameters
        check(networkParameters.minimumPlatformVersion <= versionInfo.platformVersion) {
            "Node's platform version is lower than network's required minimumPlatformVersion"
        }

        // Do all of this in a database transaction so anything that might need a connection has one.
        val (startedImpl, schedulerService) = initialiseDatabasePersistence(schemaService, identityService).transaction {
            val networkMapCache = NetworkMapCacheImpl(PersistentNetworkMapCache(database, networkParameters.notaries).start(), identityService)
            val (keyPairs, nodeInfo) = updateNodeInfo(networkMapCache, networkMapClient, identity, identityKeyPair)
            identityService.loadIdentities(nodeInfo.legalIdentitiesAndCerts)
            val metrics = MetricRegistry()
            val transactionStorage = makeTransactionStorage(database, configuration.transactionCacheSizeBytes)
            log.debug("Transaction storage created")
            attachments = NodeAttachmentService(metrics, configuration.attachmentContentCacheSizeBytes, configuration.attachmentCacheBound)
            log.debug("Attachment service created")
            val cordappProvider = CordappProviderImpl(cordappLoader, CordappConfigFileProvider(), attachments, networkParameters.whitelistedContractImplementations)
            log.debug("Cordapp provider created")
            val servicesForResolution = ServicesForResolutionImpl(identityService, attachments, cordappProvider, networkParameters, transactionStorage)
            val nodeProperties = NodePropertiesPersistentStore(StubbedNodeUniqueIdProvider::value, database)
            val nodeServices = makeServices(
                    keyPairs,
                    schemaService,
                    transactionStorage,
                    metrics,
                    servicesForResolution,
                    database,
                    nodeInfo,
                    identityService,
                    networkMapCache,
                    nodeProperties,
                    cordappProvider,
                    networkParameters)
            val mutualExclusionConfiguration = configuration.enterpriseConfiguration.mutualExclusionConfiguration
            if (mutualExclusionConfiguration.on) {
                // Ensure uniqueness in case nodes are hosted on the same machine.
                val extendedMachineName = "${configuration.baseDirectory}/${mutualExclusionConfiguration.machineName}"
                RunOnceService(database, extendedMachineName,
                        ManagementFactory.getRuntimeMXBean().name.split("@")[0],
                        mutualExclusionConfiguration.updateInterval, mutualExclusionConfiguration.waitInterval).start()
            }
            val notaryService = makeNotaryService(nodeServices, database)
            val smm = makeStateMachineManager(database)
            val flowLogicRefFactory = FlowLogicRefFactoryImpl(cordappLoader.appClassLoader)
            val flowStarter = FlowStarterImpl(smm, flowLogicRefFactory)
            val schedulerService = NodeSchedulerService(
                    platformClock,
                    database,
                    flowStarter,
                    servicesForResolution,
                    unfinishedSchedules = busyNodeLatch,
                    flowLogicRefFactory = flowLogicRefFactory,
                    drainingModePollPeriod = configuration.drainingModePollPeriod,
                    nodeProperties = nodeProperties)

            (serverThread as? ExecutorService)?.let {
                runOnStop += {
                    // We wait here, even though any in-flight messages should have been drained away because the
                    // server thread can potentially have other non-messaging tasks scheduled onto it. The timeout value is
                    // arbitrary and might be inappropriate.
                    MoreExecutors.shutdownAndAwaitTermination(it, 50, TimeUnit.SECONDS)
                }
            }

            makeVaultObservers(schedulerService, database.hibernateConfig, schemaService, flowLogicRefFactory)
            val rpcOps = makeRPCOps(flowStarter, database, smm)
            startMessagingService(rpcOps)
            installCoreFlows()
            val cordaServices = installCordaServices(flowStarter)
            tokenizableServices = nodeServices + cordaServices + schedulerService
            registerCordappFlows(smm)
            _services.rpcFlows += cordappLoader.cordapps.flatMap { it.rpcFlows }
            startShell()
            Pair(StartedNodeImpl(this@AbstractNode, _services, nodeInfo, checkpointStorage, smm, attachments, network, database, rpcOps, flowStarter, notaryService), schedulerService)
        }

        networkMapUpdater = NetworkMapUpdater(services.networkMapCache,
                NodeInfoWatcher(configuration.baseDirectory, getRxIoScheduler(), Duration.ofMillis(configuration.additionalNodeInfoPollingFrequencyMsec)),
                networkMapClient,
                networkParameters.serialize().hash,
                services.myInfo.serialize().hash,
                configuration.baseDirectory,
                configuration.extraNetworkMapKeys)
        runOnStop += networkMapUpdater::close

        networkMapUpdater.subscribeToNetworkMap()

        // If we successfully loaded network data from database, we set this future to Unit.
        _nodeReadyFuture.captureLater(services.networkMapCache.nodeReady.map { Unit })

        return startedImpl.apply {
            database.transaction {
                smm.start(tokenizableServices)
                // Shut down the SMM so no Fibers are scheduled.
                runOnStop += { smm.stop(acceptableLiveFiberCountOnStop()) }
                schedulerService.start()
            }
            _started = this
        }
    }

    /**
     * Should be [rx.schedulers.Schedulers.io] for production,
     * or [rx.internal.schedulers.CachedThreadScheduler] (with shutdown registered with [runOnStop]) for shared-JVM testing.
     */
    protected abstract fun getRxIoScheduler(): Scheduler

    open fun startShell() {
        if (configuration.shouldInitCrashShell()) {
            InteractiveShell.startShellInternal(configuration.toShellConfig(), cordappLoader.appClassLoader)
        }
    }

    private fun updateNodeInfo(networkMapCache: NetworkMapCacheBaseInternal,
                               networkMapClient: NetworkMapClient?,
                               identity: PartyAndCertificate,
                               identityKeyPair: KeyPair): Pair<Set<KeyPair>, NodeInfo> {
        val keyPairs = mutableSetOf(identityKeyPair)

        myNotaryIdentity = configuration.notary?.let {
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

        val nodeInfoFromDb = networkMapCache.getNodeByLegalName(identity.name)

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
        if (networkMapClient != null) {
            tryPublishNodeInfoAsync(nodeInfoAndSigned.signed, networkMapClient)
        }

        return Pair(keyPairs, nodeInfo)
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
                executor.schedule(this, republishInterval.toMinutes(), TimeUnit.MINUTES)
            }
        })
    }

    protected abstract fun myAddresses(): List<NetworkHostAndPort>

    protected open fun makeStateMachineManager(database: CordaPersistence): StateMachineManager {
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

    private fun installCordaServices(flowStarter: FlowStarter): List<SerializeAsToken> {
        val loadedServices = cordappLoader.cordapps.flatMap { it.services }
        return filterServicesToInstall(loadedServices).mapNotNull {
            try {
                installCordaService(flowStarter, it)
            } catch (e: NoSuchMethodException) {
                log.error("${it.name}, as a Corda service, must have a constructor with a single parameter of type " +
                        ServiceHub::class.java.name)
                null
            } catch (e: ServiceInstantiationException) {
                log.error("Corda service ${it.name} failed to instantiate", e.cause)
                null
            } catch (e: Exception) {
                log.error("Unable to install Corda service ${it.name}", e)
                null
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
     * This customizes the ServiceHub for each CordaService that is initiating flows
     */
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

    private fun <T : SerializeAsToken> installCordaService(flowStarter: FlowStarter, serviceClass: Class<T>): T {
        serviceClass.requireAnnotation<CordaService>()
        val service = try {
            val serviceContext = AppServiceHubImpl<T>(services, flowStarter)
            if (isNotaryService(serviceClass)) {
                check(myNotaryIdentity != null) { "Trying to install a notary service but no notary identity specified" }
                val constructor = serviceClass.getDeclaredConstructor(AppServiceHub::class.java, PublicKey::class.java).apply { isAccessible = true }
                serviceContext.serviceInstance = constructor.newInstance(serviceContext, myNotaryIdentity!!.owningKey)
                serviceContext.serviceInstance
            } else {
                try {
                    val extendedServiceConstructor = serviceClass.getDeclaredConstructor(AppServiceHub::class.java).apply { isAccessible = true }
                    serviceContext.serviceInstance = extendedServiceConstructor.newInstance(serviceContext)
                    serviceContext.serviceInstance
                } catch (ex: NoSuchMethodException) {
                    val constructor = serviceClass.getDeclaredConstructor(ServiceHub::class.java).apply { isAccessible = true }
                    log.warn("${serviceClass.name} is using legacy CordaService constructor with ServiceHub parameter. Upgrade to an AppServiceHub parameter to enable updated API features.")
                    constructor.newInstance(services)
                }
            }
        } catch (e: InvocationTargetException) {
            throw ServiceInstantiationException(e.cause)
        }
        cordappServices.putInstance(serviceClass, service)

        if (service is NotaryService) handleCustomNotaryService(service)

        log.info("Installed ${serviceClass.name} Corda service")
        return service
    }

    private fun handleCustomNotaryService(service: NotaryService) {
        runOnStop += service::stop
        service.start()
        installCoreFlow(NotaryFlow.Client::class, service::createServiceFlow)
    }

    private fun registerCordappFlows(smm: StateMachineManager) {
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

    internal fun <T : FlowLogic<*>> registerInitiatedFlow(smm: StateMachineManager, initiatedFlowClass: Class<T>): Observable<T> {
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

    internal fun <F : FlowLogic<*>> internalRegisterFlowFactory(smm: StateMachineManager,
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

    /**
     * Builds node internal, advertised, and plugin services.
     * Returns a list of tokenizable services to be added to the serialisation context.
     */
    private fun makeServices(keyPairs: Set<KeyPair>,
                             schemaService: SchemaService,
                             transactionStorage: WritableTransactionStorage,
                             metrics: MetricRegistry,
                             servicesForResolution: ServicesForResolution,
                             database: CordaPersistence,
                             nodeInfo: NodeInfo,
                             identityService: IdentityServiceInternal,
                             networkMapCache: NetworkMapCacheInternal,
                             nodeProperties: NodePropertiesStore,
                             cordappProvider: CordappProviderInternal,
                             networkParameters: NetworkParameters): MutableList<Any> {
        checkpointStorage = DBCheckpointStorage()

        val keyManagementService = makeKeyManagementService(identityService, keyPairs)
        _services = ServiceHubInternalImpl(
                identityService,
                keyManagementService,
                schemaService,
                transactionStorage,
                MonitoringService(metrics),
                cordappProvider,
                database,
                nodeInfo,
                networkMapCache,
                nodeProperties,
                networkParameters,
                servicesForResolution)
        network = makeMessagingService(database, nodeInfo, nodeProperties, networkParameters)
        return mutableListOf(attachments, network, services.vaultService,
                services.keyManagementService, services.identityService, platformClock,
                services.auditService, services.monitoringService, services.networkMapCache, services.schemaService,
                services.transactionVerifierService, services.validatedTransactions, services.contractUpgradeService,
                services, cordappProvider, this)
    }

    protected open fun makeTransactionStorage(database: CordaPersistence, transactionCacheSizeBytes: Long): WritableTransactionStorage = DBTransactionStorage(transactionCacheSizeBytes)
    private fun makeVaultObservers(schedulerService: SchedulerService, hibernateConfig: HibernateConfiguration, schemaService: SchemaService, flowLogicRefFactory: FlowLogicRefFactory) {
        ScheduledActivityObserver.install(services.vaultService, schedulerService, flowLogicRefFactory)
        HibernateObserver.install(services.vaultService.rawUpdates, hibernateConfig, schemaService)
    }

    @VisibleForTesting
    protected open fun acceptableLiveFiberCountOnStop(): Int = 0

    private fun validateKeystore() {
        val containCorrectKeys = try {
            // This will throw IOException if key file not found or KeyStoreException if keystore password is incorrect.
            val sslKeystore = configuration.loadSslKeyStore()
            val identitiesKeystore = configuration.loadNodeKeyStore()
            X509Utilities.CORDA_CLIENT_TLS in sslKeystore && X509Utilities.CORDA_CLIENT_CA in identitiesKeystore
        } catch (e: KeyStoreException) {
            log.warn("Certificate key store found but key store password does not match configuration.")
            false
        } catch (e: IOException) {
            log.error("IO exception while trying to validate keystore", e)
            false
        }
        require(containCorrectKeys) {
            "Identity certificate not found. " +
                    "Please either copy your existing identity key and certificate from another node, " +
                    "or if you don't have one yet, fill out the config file and run corda.jar --initial-registration. " +
                    "Read more at: https://docs.corda.net/permissioning.html"
        }

        // Check all cert path chain to the trusted root
        val sslCertChainRoot = configuration.loadSslKeyStore().getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).last()
        val nodeCaCertChainRoot = configuration.loadNodeKeyStore().getCertificateChain(X509Utilities.CORDA_CLIENT_CA).last()
        val trustRoot = configuration.loadTrustStore().getCertificate(X509Utilities.CORDA_ROOT_CA)

        require(sslCertChainRoot == trustRoot) { "TLS certificate must chain to the trusted root." }
        require(nodeCaCertChainRoot == trustRoot) { "Client CA certificate must chain to the trusted root." }
    }

    // Specific class so that MockNode can catch it.
    class DatabaseConfigurationException(msg: String) : CordaException(msg)

    protected open fun initialiseDatabasePersistence(schemaService: SchemaService, identityService: IdentityService): CordaPersistence {
        log.debug {
            val driverClasses = DriverManager.getDrivers().asSequence().map { it.javaClass.name }
            "Available JDBC drivers: $driverClasses"
        }

        val props = configuration.dataSourceProperties
        if (props.isEmpty) throw DatabaseConfigurationException("There must be a database configured.")
        val database = configureDatabase(props, configuration.database, identityService, schemaService)
        // Now log the vendor string as this will also cause a connection to be tested eagerly.
        logVendorString(database, log)
        runOnStop += database::close
        return database
    }

    private fun makeNotaryService(tokenizableServices: MutableList<Any>, database: CordaPersistence): NotaryService? {
        return configuration.notary?.let {
            makeCoreNotaryService(it, database).also {
                tokenizableServices.add(it)
                runOnStop += it::stop
                installCoreFlow(NotaryFlow.Client::class, it::createServiceFlow)
                log.info("Running core notary: ${it.javaClass.name}")
                it.start()
            }
        }
    }

    protected open fun checkNetworkMapIsInitialized() {
        if (!services.networkMapCache.loadDBSuccess) {
            // TODO: There should be a consistent approach to configuration error exceptions.
            throw NetworkMapCacheEmptyException()
        }
    }

    protected open fun makeKeyManagementService(identityService: IdentityService, keyPairs: Set<KeyPair>): KeyManagementService {
        return PersistentKeyManagementService(identityService, keyPairs)
    }

    private fun makeCoreNotaryService(notaryConfig: NotaryConfig, database: CordaPersistence): NotaryService {
        val notaryKey = myNotaryIdentity?.owningKey ?: throw IllegalArgumentException("No notary identity initialized when creating a notary service")
        return notaryConfig.run {
            when {
                raft != null -> {
                    val uniquenessProvider = RaftUniquenessProvider(configuration, database, services.clock, services.monitoringService.metrics, raft)
                    (if (validating) ::RaftValidatingNotaryService else ::RaftNonValidatingNotaryService)(services, notaryKey, uniquenessProvider)
                }
                bftSMaRt != null -> {
                    if (validating) throw IllegalArgumentException("Validating BFTSMaRt notary not supported")
                    BFTNonValidatingNotaryService(services, notaryKey, bftSMaRt, makeBFTCluster(notaryKey, bftSMaRt))
                }
                mysql != null -> {
                    (if (validating) ::MySQLValidatingNotaryService else ::MySQLNonValidatingNotaryService)(services, notaryKey, mysql, configuration.devMode)
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

    private fun makeIdentityService(identityCert: X509Certificate): PersistentIdentityService {
        val trustRoot = configuration.loadTrustStore().getCertificate(X509Utilities.CORDA_ROOT_CA)
        val nodeCa = configuration.loadNodeKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_CA)
        return PersistentIdentityService(trustRoot, listOf(identityCert, nodeCa))
    }

    protected abstract fun makeTransactionVerifierService(): TransactionVerifierService

    open fun stop() {
        // TODO: We need a good way of handling "nice to have" shutdown events, especially those that deal with the
        // network, including unsubscribing from updates from remote services. Possibly some sort of parameter to stop()
        // to indicate "Please shut down gracefully" vs "Shut down now".
        // Meanwhile, we let the remote service send us updates until the acknowledgment buffer overflows and it
        // unsubscribes us forcibly, rather than blocking the shutdown process.

        // Run shutdown hooks in opposite order to starting
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
        shutdownExecutor.shutdown()
        _started = null
    }

    protected abstract fun makeMessagingService(database: CordaPersistence, info: NodeInfo, nodeProperties: NodePropertiesStore, networkParameters: NetworkParameters): MessagingService
    protected abstract fun startMessagingService(rpcOps: RPCOps)

    private fun obtainIdentity(notaryConfig: NotaryConfig?): Pair<PartyAndCertificate, KeyPair> {
        val keyStore = configuration.loadNodeKeyStore()

        val (id, singleName) = if (notaryConfig == null || !notaryConfig.isClusterConfig) {
            // Node's main identity or if it's a single node notary
            Pair(DevIdentityGenerator.NODE_IDENTITY_ALIAS_PREFIX, configuration.myLegalName)
        } else {
            // The node is part of a distributed notary whose identity must already be generated beforehand.
            Pair(DevIdentityGenerator.DISTRIBUTED_NOTARY_ALIAS_PREFIX, null)
        }
        // TODO: Integrate with Key management service?
        val privateKeyAlias = "$id-private-key"

        if (privateKeyAlias !in keyStore) {
            singleName ?: throw IllegalArgumentException(
                    "Unable to find in the key store the identity of the distributed notary the node is part of")
            log.info("$privateKeyAlias not found in key store ${configuration.nodeKeystore}, generating fresh key!")
            // TODO This check shouldn't be needed
            check(singleName == configuration.myLegalName)
            keyStore.storeLegalIdentity(privateKeyAlias, generateKeyPair())
        }

        val (x509Cert, keyPair) = keyStore.getCertificateAndKeyPair(privateKeyAlias)

        // TODO: Use configuration to indicate composite key should be used instead of public key for the identity.
        val compositeKeyAlias = "$id-composite-key"
        val certificates = if (compositeKeyAlias in keyStore) {
            // Use composite key instead if it exists
            val certificate = keyStore.getCertificate(compositeKeyAlias)
            // We have to create the certificate chain for the composite key manually, this is because we don't have a keystore
            // provider that understand compositeKey-privateKey combo. The cert chain is created using the composite key certificate +
            // the tail of the private key certificates, as they are both signed by the same certificate chain.
            listOf(certificate) + keyStore.getCertificateChain(privateKeyAlias).drop(1)
        } else {
            keyStore.getCertificateChain(privateKeyAlias).let {
                check(it[0] == x509Cert) { "Certificates from key store do not line up!" }
                it
            }
        }

        val subject = CordaX500Name.build(certificates[0].subjectX500Principal)
        if (singleName != null && subject != singleName) {
            throw ConfigurationException("The name '$singleName' for $id doesn't match what's in the key store: $subject")
        } else if (notaryConfig != null && notaryConfig.isClusterConfig && notaryConfig.serviceLegalName != null && subject != notaryConfig.serviceLegalName) {
            // Note that we're not checking if `notaryConfig.serviceLegalName` is not present for backwards compatibility.
            throw ConfigurationException("The name of the notary service '${notaryConfig.serviceLegalName}' for $id doesn't match what's in the key store: $subject. "+
                    "You might need to adjust the configuration of `notary.serviceLegalName`.")
        }

        val certPath = X509Utilities.buildCertPath(certificates)
        return Pair(PartyAndCertificate(certPath), keyPair)
    }

    protected open fun generateKeyPair() = cryptoGenerateKeyPair()
    protected open fun makeVaultService(keyManagementService: KeyManagementService, services: ServicesForResolution, hibernateConfig: HibernateConfiguration): VaultServiceInternal {
        return NodeVaultService(platformClock, keyManagementService, services, hibernateConfig)
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
                    "jolokia-jvm-${NodeBuildProperties.JOLOKIA_AGENT_VERSION}-agent.jar", libDir) ?:
                    throw Error("Unable to locate agent jar file")
            log.info("Agent jar file: $jarFilePath")
            JVMAgentRegistry.attach("jolokia", "port=$port", jarFilePath)
        }
    }

    private inner class ServiceHubInternalImpl(
            override val identityService: IdentityService,
            // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
            // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
            // the identity key. But the infrastructure to make that easy isn't here yet.
            override val keyManagementService: KeyManagementService,
            override val schemaService: SchemaService,
            override val validatedTransactions: WritableTransactionStorage,
            override val monitoringService: MonitoringService,
            override val cordappProvider: CordappProviderInternal,
            override val database: CordaPersistence,
            override val myInfo: NodeInfo,
            override val networkMapCache: NetworkMapCacheInternal,
            override val nodeProperties: NodePropertiesStore,
            override val networkParameters: NetworkParameters,
            private val servicesForResolution: ServicesForResolution
    ) : SingletonSerializeAsToken(), ServiceHubInternal, ServicesForResolution by servicesForResolution {
        override val rpcFlows = ArrayList<Class<out FlowLogic<*>>>()
        override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage()
        override val auditService = DummyAuditService()
        override val transactionVerifierService by lazy { makeTransactionVerifierService() }
        override val vaultService by lazy { makeVaultService(keyManagementService, servicesForResolution, database.hibernateConfig) }
        override val contractUpgradeService by lazy { ContractUpgradeServiceImpl() }
        override val attachments: AttachmentStorage get() = this@AbstractNode.attachments
        override val networkService: MessagingService get() = network
        override val clock: Clock get() = platformClock
        override val configuration: NodeConfiguration get() = this@AbstractNode.configuration
        override val networkMapUpdater: NetworkMapUpdater get() = this@AbstractNode.networkMapUpdater
        override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
            require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
            return cordappServices.getInstance(type) ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
        }

        override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
            return flowFactories[initiatingFlowClass]
        }

        override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
            database.transaction {
                super.recordTransactions(statesToRecord, txs)
            }
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

internal class FlowStarterImpl(private val smm: StateMachineManager, private val flowLogicRefFactory: FlowLogicRefFactory) : FlowStarter {
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

/**
 * Thrown when a node is about to start and its network map cache doesn't contain any node.
 */
internal class NetworkMapCacheEmptyException : Exception()

fun configureDatabase(hikariProperties: Properties,
                      databaseConfig: DatabaseConfig,
                      identityService: IdentityService,
                      schemaService: SchemaService = NodeSchemaService()): CordaPersistence {
    // Register the AbstractPartyDescriptor so Hibernate doesn't warn when encountering AbstractParty. Unfortunately
    // Hibernate warns about not being able to find a descriptor if we don't provide one, but won't use it by default
    // so we end up providing both descriptor and converter. We should re-examine this in later versions to see if
    // either Hibernate can be convinced to stop warning, use the descriptor by default, or something else.
    JavaTypeDescriptorRegistry.INSTANCE.addDescriptor(AbstractPartyDescriptor(identityService))
    val dataSource = DataSourceFactory.createDataSource(hikariProperties)
    val attributeConverters = listOf(AbstractPartyToX500NameAsStringConverter(identityService))
    val jdbcUrl = hikariProperties.getProperty("dataSource.url", "")
    SchemaMigration(
            schemaService.schemaOptions.keys,
            dataSource,
            !isH2Database(jdbcUrl),
            databaseConfig).nodeStartup()
    return CordaPersistence(dataSource, databaseConfig, schemaService.schemaOptions.keys, jdbcUrl, attributeConverters)
}
