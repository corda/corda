package net.corda.node.internal

import co.paralleluniverse.fibers.instrument.Retransform
import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.google.common.collect.MutableClassToInstanceMap
import com.google.common.util.concurrent.MoreExecutors
import com.zaxxer.hikari.pool.HikariPool
import io.netty.util.concurrent.DefaultThreadFactory
import net.corda.common.logging.errorReporting.NodeDatabaseErrors
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.contracts.RotatedKeys
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.AttachmentTrustCalculator
import net.corda.core.internal.FlowStateMachineHandle
import net.corda.core.internal.NODE_INFO_DIRECTORY
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.NetworkParametersStorage
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.div
import net.corda.core.internal.messaging.AttachmentTrustInfoRPCOps
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.rootMessage
import net.corda.core.internal.telemetry.OpenTelemetryComponent
import net.corda.core.internal.telemetry.SimpleLogTelemetryComponent
import net.corda.core.internal.telemetry.TelemetryComponent
import net.corda.core.internal.telemetry.TelemetryServiceImpl
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.flows.FlowManagerRPCOps
import net.corda.core.node.AppServiceHub
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.ContractUpgradeService
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.TelemetryService
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.node.services.diagnostics.DiagnosticsService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.serialization.internal.AttachmentsClassLoaderCacheImpl
import net.corda.core.toFuture
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.days
import net.corda.core.utilities.millis
import net.corda.core.utilities.minutes
import net.corda.node.CordaClock
import net.corda.node.VersionInfo
import net.corda.node.internal.attachments.AttachmentTrustInfoRPCOpsImpl
import net.corda.node.internal.checkpoints.FlowManagerRPCOpsImpl
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.internal.cordapp.VirtualCordapp
import net.corda.node.internal.rpc.proxies.AuthenticatedRpcOpsProxy
import net.corda.node.internal.rpc.proxies.ThreadContextAdjustingRpcOpsProxy
import net.corda.node.internal.shell.InteractiveShell
import net.corda.node.services.ContractUpgradeHandler
import net.corda.node.services.FinalityHandler
import net.corda.node.services.NotaryChangeHandler
import net.corda.node.services.api.AuditService
import net.corda.node.services.api.DummyAuditService
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.attachments.NodeAttachmentTrustCalculator
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.rpc.NodeRpcOptions
import net.corda.node.services.config.shouldInitCrashShell
import net.corda.node.services.diagnostics.NodeDiagnosticsService
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.events.ScheduledActivityObserver
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.keys.KeyManagementServiceInternal
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.services.network.NetworkMapUpdater
import net.corda.node.services.network.NetworkParameterUpdateListener
import net.corda.node.services.network.NetworkParametersHotloader
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.network.PersistentPartyInfoCache
import net.corda.node.services.persistence.AbstractPartyDescriptor
import net.corda.node.services.persistence.AbstractPartyToX500NameAsStringConverter
import net.corda.node.services.persistence.AttachmentStorageInternal
import net.corda.node.services.persistence.DBCheckpointPerformanceRecorder
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.persistence.AesDbEncryptionService
import net.corda.node.services.persistence.DBTransactionMappingStorage
import net.corda.node.services.persistence.DBTransactionStorageLedgerRecovery
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.persistence.NodePropertiesPersistentStore
import net.corda.node.services.persistence.PublicKeyToOwningIdentityCacheImpl
import net.corda.node.services.persistence.PublicKeyToTextConverter
import net.corda.node.services.rpc.CheckpointDumperImpl
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.node.services.statemachine.FlowLogicRefFactoryImpl
import net.corda.node.services.statemachine.FlowMonitor
import net.corda.node.services.statemachine.FlowOperator
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.SingleThreadedStateMachineManager
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.BindableNamedCacheFactory
import net.corda.node.utilities.NamedThreadFactory
import net.corda.node.utilities.NotaryLoader
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.NodeStatus
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleEvent
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleEventsDistributor
import net.corda.nodeapi.internal.lifecycle.NodeServicesContext
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.CordaTransactionSupportImpl
import net.corda.nodeapi.internal.persistence.CouldNotCreateDataSourceException
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.OutstandingDatabaseChangesException
import net.corda.nodeapi.internal.persistence.RestrictedConnection
import net.corda.nodeapi.internal.persistence.RestrictedEntityManager
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.nodeapi.internal.persistence.contextDatabase
import net.corda.nodeapi.internal.persistence.withoutDatabaseAccess
import net.corda.nodeapi.internal.namedThreadPoolExecutor
import org.apache.activemq.artemis.utils.ReusableLatch
import org.jolokia.jvmagent.JolokiaServer
import org.jolokia.jvmagent.JolokiaServerConfig
import org.slf4j.Logger
import rx.Scheduler
import java.lang.reflect.InvocationTargetException
import java.sql.Connection
import java.sql.Savepoint
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeParseException
import java.util.ArrayList
import java.util.Properties
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import java.util.function.Consumer
import javax.persistence.EntityManager
import javax.sql.DataSource

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
                               cacheFactoryPrototype: BindableNamedCacheFactory,
                               protected val versionInfo: VersionInfo,
                               protected val flowManager: FlowManager,
                               val serverThread: AffinityExecutor.ServiceAffinityExecutor,
                               val busyNodeLatch: ReusableLatch = ReusableLatch(),
                               protected val allowHibernateToManageAppSchema: Boolean = false,
                               private val allowAppSchemaUpgradeWithCheckpoints: Boolean = false) : SingletonSerializeAsToken() {

    protected abstract val log: Logger

    @Suppress("LeakingThis")
    private var tokenizableServices: MutableList<SerializeAsToken>? = mutableListOf(platformClock, this)

    val metricRegistry = MetricRegistry()
    protected val cacheFactory = cacheFactoryPrototype.bindWithConfig(configuration).bindWithMetrics(metricRegistry).tokenize()
    val monitoringService = MonitoringService(metricRegistry).tokenize()

    protected val runOnStop = ArrayList<() -> Any?>()

    protected open val runMigrationScripts: Boolean = configuredDbIsInMemory()

    // if the configured DB is in memory, we will need to run db migrations, as the db does not persist between runs.
    private fun configuredDbIsInMemory() = configuration.dataSourceProperties.getProperty("dataSource.url").startsWith("jdbc:h2:mem:")

    init {
        (serverThread as? ExecutorService)?.let {
            runOnStop += {
                // We wait here, even though any in-flight messages should have been drained away because the
                // server thread can potentially have other non-messaging tasks scheduled onto it. The timeout value is
                // arbitrary and might be inappropriate.
                MoreExecutors.shutdownAndAwaitTermination(it, 50, SECONDS)
            }
        }

        quasarExcludePackages(configuration)

        if (allowHibernateToManageAppSchema && !configuration.devMode) {
            throw ConfigurationException("Hibernate can only be used to manage app schema in development while using dev mode. " +
                    "Please remove the --allow-hibernate-to-manage-app-schema command line flag and provide schema migration scripts for your CorDapps."
            )
        }
    }

    private val notaryLoader = configuration.notary?.let {
        NotaryLoader(it, versionInfo)
    }
    val rotatedKeys = makeRotatedKeysService(configuration).tokenize()
    val cordappLoader: CordappLoader = makeCordappLoader(configuration, versionInfo, rotatedKeys).closeOnStop(false)
    val telemetryService: TelemetryServiceImpl = TelemetryServiceImpl().also {
        val openTelemetryComponent = OpenTelemetryComponent(configuration.myLegalName.toString(), configuration.telemetry.spanStartEndEventsEnabled, configuration.telemetry.copyBaggageToTags)
        if (configuration.telemetry.openTelemetryEnabled && openTelemetryComponent.isEnabled()) {
            it.addTelemetryComponent(openTelemetryComponent)
        }
        if (configuration.telemetry.simpleLogTelemetryEnabled) {
            it.addTelemetryComponent(SimpleLogTelemetryComponent())
        }
        runOnStop += { it.shutdownTelemetry() }
    }.tokenize()
    val schemaService = NodeSchemaService(cordappLoader.cordappSchemas).tokenize()
    val identityService = PersistentIdentityService(cacheFactory).tokenize()
    val database: CordaPersistence = createCordaPersistence(
            configuration.database,
            identityService::wellKnownPartyFromX500Name,
            identityService::wellKnownPartyFromAnonymous,
            schemaService,
            configuration.dataSourceProperties,
            cacheFactory,
            cordappLoader.appClassLoader,
            allowHibernateToManageAppSchema)

    private val transactionSupport = CordaTransactionSupportImpl(database)

    init {
        // TODO Break cyclic dependency
        identityService.database = database
    }

    val networkMapCache = PersistentNetworkMapCache(cacheFactory, database, identityService).tokenize()
    val partyInfoCache = PersistentPartyInfoCache(networkMapCache, cacheFactory, database)
    val encryptionService = AesDbEncryptionService(database)
    @Suppress("LeakingThis")
    val cryptoService = makeCryptoService()
    @Suppress("LeakingThis")
    val transactionStorage = makeTransactionStorage(configuration.transactionCacheSizeBytes).tokenize()
    val networkMapClient: NetworkMapClient? = configuration.networkServices?.let { NetworkMapClient(it.networkMapURL, versionInfo) }
    val attachments = NodeAttachmentService(
        metricRegistry,
        cacheFactory,
        database,
        configuration.devMode
    ).tokenize()
    val attachmentTrustCalculator = makeAttachmentTrustCalculator(configuration, database, rotatedKeys)
    @Suppress("LeakingThis")
    val networkParametersStorage = makeNetworkParametersStorage()
    val cordappProvider = CordappProviderImpl(cordappLoader, CordappConfigFileProvider(configuration.cordappDirectories), attachments).tokenize()
    val diagnosticsService = NodeDiagnosticsService().tokenize()
    val pkToIdCache = PublicKeyToOwningIdentityCacheImpl(database, cacheFactory)
    @Suppress("LeakingThis")
    val keyManagementService = makeKeyManagementService(identityService).tokenize()
    val servicesForResolution = ServicesForResolutionImpl(identityService, attachments, cordappProvider, networkParametersStorage, transactionStorage).also {
        attachments.servicesForResolution = it
    }
    @Suppress("LeakingThis")
    val vaultService = makeVaultService(keyManagementService, servicesForResolution, database, cordappLoader).tokenize()
    val nodeProperties = NodePropertiesPersistentStore(StubbedNodeUniqueIdProvider::value, database, cacheFactory)
    val flowLogicRefFactory = makeFlowLogicRefFactoryImpl()
    // TODO Cancelling parameters updates - if we do that, how we ensure that no one uses cancelled parameters in the transactions?
    val networkMapUpdater = makeNetworkMapUpdater()

    @Suppress("LeakingThis")
    val transactionVerifierService = InMemoryTransactionVerifierService(
        numberOfWorkers = transactionVerifierWorkerCount,
        cordappProvider = cordappProvider,
        attachments = attachments
    ).tokenize()
    val verifierFactoryService: VerifierFactoryService = if (djvmCordaSource != null) {
        DeterministicVerifierFactoryService(djvmBootstrapSource, djvmCordaSource).apply {
            log.info("DJVM sandbox enabled for deterministic contract verification.")
            if (!configuration.devMode) {
                log.info("Generating Corda classes for DJVM sandbox.")
                generateSandbox()
            }
            tokenize()
        }
    } else {
        BasicVerifierFactoryService()
    }
    private val attachmentsClassLoaderCache: AttachmentsClassLoaderCache = AttachmentsClassLoaderCacheImpl(cacheFactory, rotatedKeys).tokenize()
    val contractUpgradeService = ContractUpgradeServiceImpl(cacheFactory).tokenize()
    val auditService = DummyAuditService().tokenize()
    @Suppress("LeakingThis")
    protected val network: MessagingService = makeMessagingService().tokenize().apply {
        activeChange.subscribe({
            log.info("MessagingService active change to: $it")
        }, {
            log.warn("MessagingService subscription error", it)
        })
    }
    val services = ServiceHubInternalImpl().tokenize()
    val checkpointStorage = DBCheckpointStorage(DBCheckpointPerformanceRecorder(services.monitoringService.metrics), platformClock)
    @Suppress("LeakingThis")
    val smm = makeStateMachineManager()
    val flowStarter = FlowStarterImpl(smm, flowLogicRefFactory, DBCheckpointStorage.MAX_CLIENT_ID_LENGTH)
	val flowOperator = FlowOperator(smm, platformClock)
    private val schedulerService = makeNodeSchedulerService()

    private val cordappServices = MutableClassToInstanceMap.create<SerializeAsToken>()
    private val cordappTelemetryComponents = MutableClassToInstanceMap.create<TelemetryComponent>()
    private val shutdownExecutor = Executors.newSingleThreadExecutor(DefaultThreadFactory("Shutdown"))

    protected abstract val transactionVerifierWorkerCount: Int
    /**
     * Should be [rx.schedulers.Schedulers.io] for production,
     * or [rx.internal.schedulers.CachedThreadScheduler] (with shutdown registered with [runOnStop]) for shared-JVM testing.
     */
    protected abstract val rxIoScheduler: Scheduler

    val externalOperationExecutor = createExternalOperationExecutor(configuration.flowExternalOperationThreadPoolSize)

    /**
     * Completes once the node has successfully registered with the network map service
     * or has loaded network map data from local database.
     */
    val nodeReadyFuture: CordaFuture<Unit> get() = networkMapCache.nodeReady.map { Unit }

    open val serializationWhitelists: List<SerializationWhitelist> by lazy {
        cordappLoader.cordapps.flatMap { it.serializationWhitelists }
    }

    /** Set to non-null once [start] has been successfully called. */
    open val started: S? get() = _started
    @Volatile
    private var _started: S? = null

    private val checkpointDumper = CheckpointDumperImpl(
            checkpointStorage,
            database,
            services,
            services.configuration.baseDirectory,
            services.configuration.cordappDirectories)

    private var notaryService: NotaryService? = null

    private val nodeServicesContext = object : NodeServicesContext {
        override val platformVersion = versionInfo.platformVersion
        override val configurationWithOptions = configuration.configurationWithOptions
        // Note: tokenizableServices passed by reference meaning that any subsequent modification to the content in the `AbstractNode` will
        // be reflected in the context as well. However, since context only has access to immutable collection it can only read (but not modify)
        // the content.
        override val tokenizableServices: List<SerializeAsToken> = this@AbstractNode.tokenizableServices!!
    }

    private val nodeLifecycleEventsDistributor = NodeLifecycleEventsDistributor().apply { add(checkpointDumper) }

    protected val keyStoreHandler = KeyStoreHandler(configuration, cryptoService)

    @Volatile
    private var nodeStatus = NodeStatus.WAITING_TO_START

    private fun <T : Any> T.tokenize(): T {
        tokenizableServices?.add(this as? SerializeAsToken ?:
            throw IllegalStateException("${this::class.java} is expected to be extending from SerializeAsToken"))
                ?: throw IllegalStateException("The tokenisable services list has already been finalised")
        return this
    }

    protected fun <T : AutoCloseable> T.closeOnStop(usesDatabase: Boolean = true): T {
        if (usesDatabase) {
            contextDatabase // Will throw if no database is available, since this would run after closing the database, yet claims it needs it.
            runOnStop += this::close
        } else {
            runOnStop += { withoutDatabaseAccess { this.close() } }
        }
        return this
    }

    /** The implementation of the [RPCOps] interfaces used by this node. */
    @Suppress("DEPRECATION")
    open fun makeRPCOps(cordappLoader: CordappLoader): List<RPCOps> {
        val cordaRPCOps = CordaRPCOpsImpl(services, smm, flowStarter) { shutdownExecutor.submit(::stop) }
        cordaRPCOps.closeOnStop()
        val flowManagerRPCOps = FlowManagerRPCOpsImpl(checkpointDumper)
        val attachmentTrustInfoRPCOps = AttachmentTrustInfoRPCOpsImpl(services.attachmentTrustCalculator)

        return listOf(
            CordaRPCOps::class.java to cordaRPCOps,
            FlowManagerRPCOps::class.java to flowManagerRPCOps,
            net.corda.core.internal.messaging.FlowManagerRPCOps::class.java to flowManagerRPCOps,
            AttachmentTrustInfoRPCOps::class.java to attachmentTrustInfoRPCOps
        ).map { (targetInterface, implementation) ->
            // Mind that order of proxies is important
            val stage1Proxy = AuthenticatedRpcOpsProxy.proxy(implementation, targetInterface)
            val stage2Proxy = ThreadContextAdjustingRpcOpsProxy.proxy(stage1Proxy, targetInterface, cordappLoader.appClassLoader)

            stage2Proxy
        }
    }

    private fun quasarExcludePackages(nodeConfiguration: NodeConfiguration) {
        val quasarInstrumentor = Retransform.getInstrumentor()

        nodeConfiguration.quasarExcludePackages.forEach { packageExclude ->
            quasarInstrumentor.addExcludedPackage(packageExclude)
        }
    }

    protected open fun initKeyStores() = keyStoreHandler.init()

    open fun generateAndSaveNodeInfo(): NodeInfo {
        check(started == null) { "Node has already been started" }
        log.info("Generating nodeInfo ...")
        val trustRoots = initKeyStores()
        startDatabase()
        identityService.start(trustRoots, keyStoreHandler.nodeIdentity, pkToIdCache = pkToIdCache)
        return database.use {
            it.transaction {
                val nodeInfoAndSigned = updateNodeInfo(publish = false)
                nodeInfoAndSigned.nodeInfo
            }
        }
    }

    open fun clearNetworkMapCache() {
        Node.printBasicNodeInfo("Clearing network map cache entries")
        log.info("Starting clearing of network map cache entries...")
        startDatabase()
        database.use {
            networkMapCache.clearNetworkMapCache()
        }
    }

    open fun runDatabaseMigrationScripts(
            updateCoreSchemas: Boolean,
            updateAppSchemas: Boolean,
            updateAppSchemasWithCheckpoints: Boolean
    ) {
        check(started == null) { "Node has already been started" }
        check(updateCoreSchemas || updateAppSchemas) { "Neither core nor app schema scripts were specified" }
        Node.printBasicNodeInfo("Running database schema migration scripts ...")
        val props = configuration.dataSourceProperties
        if (props.isEmpty) throw DatabaseConfigurationException("There must be a database configured.")
        var pendingAppChanges: Int = 0
        var pendingCoreChanges: Int = 0
        database.startHikariPool(props, metricRegistry) { dataSource, haveCheckpoints ->
            val schemaMigration = SchemaMigration(dataSource, cordappLoader, configuration.networkParametersPath, configuration.myLegalName)
            if(updateCoreSchemas) {
                schemaMigration.runMigration(haveCheckpoints, schemaService.internalSchemas, true)
            } else {
                pendingCoreChanges = schemaMigration.getPendingChangesCount(schemaService.internalSchemas, true)
            }
            if(updateAppSchemas) {
                schemaMigration.runMigration(!updateAppSchemasWithCheckpoints && haveCheckpoints, schemaService.appSchemas, !configuration.devMode)
            } else {
                pendingAppChanges = schemaMigration.getPendingChangesCount(schemaService.appSchemas, !configuration.devMode)
            }
        }
        // Now log the vendor string as this will also cause a connection to be tested eagerly.
        logVendorString(database, log)
        if (allowHibernateToManageAppSchema) {
            Node.printBasicNodeInfo("Initialising CorDapps to get schemas created by hibernate")
            val trustRoots = initKeyStores()
            networkMapClient?.start(trustRoots)
            val (netParams, signedNetParams) = NetworkParametersReader(trustRoots, networkMapClient, configuration.networkParametersPath).read()
            log.info("Loaded network parameters: $netParams")
            check(netParams.minimumPlatformVersion <= versionInfo.platformVersion) {
                "Node's platform version is lower than network's required minimumPlatformVersion"
            }
            networkMapCache.start(netParams.notaries)

            database.transaction {
                networkParametersStorage.setCurrentParameters(signedNetParams, trustRoots)
                cordappProvider.start()
            }
        }
        val updatedSchemas = listOfNotNull(
                ("core").takeIf { updateCoreSchemas },
                ("app").takeIf { updateAppSchemas }
        ).joinToString(separator = " and ");

        val pendingChanges = listOfNotNull(
                ("no outstanding").takeIf { pendingAppChanges == 0 && pendingCoreChanges == 0 },
                ("$pendingCoreChanges outstanding core").takeIf { !updateCoreSchemas && pendingCoreChanges > 0 },
                ("$pendingAppChanges outstanding app").takeIf { !updateAppSchemas && pendingAppChanges > 0 }
        ).joinToString(prefix = "There are ", postfix = " database changes.");

        Node.printBasicNodeInfo("Database migration scripts for $updatedSchemas schemas complete. $pendingChanges")
    }

    fun runSchemaSync() {
        check(started == null) { "Node has already been started" }
        Node.printBasicNodeInfo("Synchronising CorDapp schemas to the changelog ...")
        val hikariProperties = configuration.dataSourceProperties
        if (hikariProperties.isEmpty) throw DatabaseConfigurationException("There must be a database configured.")

        val dataSource = DataSourceFactory.createDataSource(hikariProperties, metricRegistry = metricRegistry)
        SchemaMigration(dataSource, cordappLoader, configuration.networkParametersPath, configuration.myLegalName)
                .synchroniseSchemas(schemaService.appSchemas, false)
        Node.printBasicNodeInfo("CorDapp schemas synchronised")
    }

    private fun setNodeStatus(st : NodeStatus) {
        log.info("Node status update: [$nodeStatus] -> [$st]")
        nodeStatus = st
    }


    @Suppress("ComplexMethod")
    open fun start(): S {
        check(started == null) { "Node has already been started" }

        if (configuration.devMode && System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation") == null) {
            System.setProperty("co.paralleluniverse.fibers.verifyInstrumentation", "true")
        }
        nodeLifecycleEventsDistributor.distributeEvent(NodeLifecycleEvent.BeforeNodeStart(nodeServicesContext))
        log.info("Node starting up ...")
        setNodeStatus(NodeStatus.STARTING)

        initialiseJolokia()
        monitoringService.metrics.register(MetricRegistry.name("Node", "Status"), Gauge { nodeStatus })

        val trustRoots = initKeyStores()

        schemaService.mappedSchemasWarnings().forEach {
            val warning = it.toWarning()
            log.warn(warning)
            Node.printWarning(warning)
        }

        installCoreFlows()
        registerCordappFlows()
        services.rpcFlows += cordappLoader.cordapps.flatMap { it.rpcFlows }
        startShell()
        networkMapClient?.start(trustRoots)

        val networkParametersReader = NetworkParametersReader(trustRoots, networkMapClient, configuration.networkParametersPath)
        val (netParams, signedNetParams) = networkParametersReader.read()
        log.info("Loaded network parameters: $netParams")
        check(netParams.minimumPlatformVersion <= versionInfo.platformVersion) {
            "Node's platform version is lower than network's required minimumPlatformVersion"
        }
        networkMapCache.start(netParams.notaries)

        startDatabase()
        // The following services need to be closed before the database, so need to be registered after it is started.
        networkMapUpdater.closeOnStop()
        schedulerService.closeOnStop()
        val rpcOps = makeRPCOps(cordappLoader)

        identityService.start(trustRoots, keyStoreHandler.nodeIdentity, netParams.notaries.map { it.identity }, pkToIdCache)

        val nodeInfoAndSigned = database.transaction {
            updateNodeInfo(publish = true, minimumPlatformVersion = netParams.minimumPlatformVersion)
        }

        val (nodeInfo, signedNodeInfo) = nodeInfoAndSigned
        services.start(nodeInfo, netParams)

        val networkParametersHotloader = if (networkMapClient == null) {
            null
        } else {
            NetworkParametersHotloader(networkMapClient, trustRoots, netParams, networkParametersReader, networkParametersStorage).also {
                it.addNotaryUpdateListener(networkMapCache)
                it.addNotaryUpdateListener(identityService)
                it.addNetworkParametersChangedListeners(services)
                it.addNetworkParametersChangedListeners(networkMapUpdater)
            }
        }

        networkMapUpdater.start(
                trustRoots,
                signedNetParams.raw.hash,
                signedNodeInfo,
                netParams,
                keyManagementService,
                configuration.networkParameterAcceptanceSettings!!,
                networkParametersHotloader)

        try {
            startMessagingService(rpcOps, nodeInfo, keyStoreHandler.notaryIdentity, netParams)
        } catch (e: Exception) {
            // Try to stop any started messaging services.
            stop()
            throw e
        }

        // Only execute futures/callbacks linked to [rootFuture] after the database transaction below is committed.
        // This ensures that the node is fully ready before starting flows.
        val rootFuture = openFuture<Void?>()

        // Do all of this in a database transaction so anything that might need a connection has one.
        val (resultingNodeInfo, readyFuture) = database.transaction(recoverableFailureTolerance = 0) {
            networkParametersStorage.setCurrentParameters(signedNetParams, trustRoots)
            identityService.loadIdentities(nodeInfo.legalIdentitiesAndCerts)
            attachments.start()
            cordappProvider.start()
            nodeProperties.start()
            // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
            // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
            // the identity key. But the infrastructure to make that easy isn't here yet.
            keyManagementService.start(keyStoreHandler.signingKeys.map { it.key to it.alias })
            installTelemetryComponents()
            installCordaServices()
            notaryService = maybeStartNotaryService(keyStoreHandler.notaryIdentity)
            contractUpgradeService.start()
            vaultService.start()
            ScheduledActivityObserver.install(vaultService, schedulerService, flowLogicRefFactory)

            val frozenTokenizableServices = tokenizableServices!!
            tokenizableServices = null

            verifyCheckpointsCompatible(frozenTokenizableServices)
            partyInfoCache.start()
            encryptionService.start(nodeInfo.legalIdentities[0])

            /* Note the .get() at the end of the distributeEvent call, below.
               This will block until all Corda Services have returned from processing the event, allowing a service to prevent the
               state machine manager from starting (just below this) until the service is ready.
             */
            nodeLifecycleEventsDistributor.distributeEvent(NodeLifecycleEvent.BeforeStateMachineStart(nodeServicesContext)).get()
            val callback = smm.start(frozenTokenizableServices)
            val smmStartedFuture = rootFuture.map { callback() }
            // Shut down the SMM so no Fibers are scheduled.
            runOnStop += { smm.stop(acceptableLiveFiberCountOnStop()) }
            val flowMonitor = FlowMonitor(
                    flowOperator,
                    configuration.flowMonitorPeriodMillis,
                    configuration.flowMonitorSuspensionLoggingThresholdMillis
            )
            runOnStop += flowMonitor::stop
            flowMonitor.start()
            schedulerService.start()

            val resultingNodeInfo = createStartedNode(nodeInfo, rpcOps, notaryService).also { _started = it }
            val readyFuture = smmStartedFuture.flatMap {
                log.debug("SMM ready")
                network.activeChange.filter { it }.toFuture()
            }
            resultingNodeInfo to readyFuture
        }

        rootFuture.captureLater(services.networkMapCache.nodeReady)

        readyFuture.map { ready ->
            if (ready) {
                // NB: Dispatch lifecycle events outside of transaction to ensure attachments and the like persisted into the DB
                log.debug("Distributing events")
                nodeLifecycleEventsDistributor.distributeEvent(NodeLifecycleEvent.AfterNodeStart(nodeServicesContext))
                nodeLifecycleEventsDistributor.distributeEvent(NodeLifecycleEvent.StateMachineStarted(nodeServicesContext))
            } else {
                log.warn("Not distributing events as NetworkMap is not ready")
            }
        }

        setNodeStatus(NodeStatus.STARTED)
        return resultingNodeInfo
    }

    /** Subclasses must override this to create a "started" node of the desired type, using the provided machinery. */
    abstract fun createStartedNode(nodeInfo: NodeInfo, rpcOps: List<RPCOps>, notaryService: NotaryService?): S

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
            val isShellStarted = InteractiveShell.startShellIfInstalled(configuration, cordappLoader)
            configuration.sshd?.port?.let {
                if (isShellStarted) {
                    Node.printBasicNodeInfo("SSH server listening on port", configuration.sshd!!.port.toString())
                    log.info("SSH server listening on port: $it.")
                } else {
                    Node.printBasicNodeInfo(
                        "SSH server not started. SSH port is defined but the corda-shell is not installed in node's drivers directory"
                    )
                    log.info("SSH server not started. SSH port is defined but the corda-shell is not installed in node's drivers directory")
                }
            }
        }
    }

    private fun updateNodeInfo(publish: Boolean, minimumPlatformVersion: Int = Int.MAX_VALUE): NodeInfoAndSigned {
        val potentialNodeInfo = NodeInfo(
                myAddresses(),
                setOf(keyStoreHandler.nodeIdentity, keyStoreHandler.notaryIdentity).filterNotNull(),
                versionInfo.platformVersion,
                serial = 0
        )

        val nodeInfoFromDb = getPreviousNodeInfoIfPresent(keyStoreHandler.nodeIdentity)

        val nodeInfo = if (potentialNodeInfo == nodeInfoFromDb?.copy(serial = 0)) {
            // The node info hasn't changed. We use the one from the database to preserve the serial.
            log.debug("Node-info hasn't changed")
            nodeInfoFromDb
        } else {
            log.info("Node-info has changed so submitting update. Old node-info was $nodeInfoFromDb")
            if (minimumPlatformVersion < PlatformVersionSwitches.CERTIFICATE_ROTATION && nodeInfoFromDb != null) {
                requireSameNodeIdentity(nodeInfoFromDb, potentialNodeInfo)
            }
            val newNodeInfo = potentialNodeInfo.copy(serial = platformClock.millis())
            networkMapCache.addOrUpdateNode(newNodeInfo)
            log.info("New node-info: $newNodeInfo")
            newNodeInfo
        }

        val nodeInfoAndSigned = NodeInfoAndSigned(nodeInfo) { publicKey, serialised ->
            val alias = keyStoreHandler.signingKeys.single { it.key == publicKey }.alias
            DigitalSignature(cryptoService.sign(alias, serialised.bytes))
        }

        // Write the node-info file even if nothing's changed, just in case the file has been deleted.
        NodeInfoWatcher.saveToFile(configuration.baseDirectory, nodeInfoAndSigned)
        NodeInfoWatcher.saveToFile(configuration.baseDirectory / NODE_INFO_DIRECTORY, nodeInfoAndSigned)

        // Always republish on startup, it's treated by network map server as a heartbeat.
        if (publish && networkMapClient != null) {
            tryPublishNodeInfoAsync(nodeInfoAndSigned.signed, networkMapClient)
        }

        return nodeInfoAndSigned
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

    private fun requireSameNodeIdentity(oldNodeInfo: NodeInfo, newNodeInfo: NodeInfo) {
        val oldIdentity = oldNodeInfo.legalIdentities.first()
        val newIdentity = newNodeInfo.legalIdentities.first()
        require(oldIdentity == newIdentity || oldIdentity.name != newIdentity.name) {
            "Failed to change node legal identity key from ${oldIdentity.owningKey.toStringShort()}"+
                    " to ${newIdentity.owningKey.toStringShort()}," +
                    " as it requires minimumPlatformVersion >= ${PlatformVersionSwitches.CERTIFICATE_ROTATION}."
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
        val executor = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory("NetworkMapPublisher"))
        executor.submit(object : Runnable {
            override fun run() {
                val republishInterval = try {
                    networkMapClient.publish(signedNodeInfo)
                    heartbeatInterval
                } catch (e: Exception) {
                    log.warn("Error encountered while publishing node info, will retry again", e)
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

    // Extracted into a function to allow overriding in subclasses.
    protected open fun makeFlowLogicRefFactoryImpl() = FlowLogicRefFactoryImpl(cordappLoader.appClassLoader)

    protected open fun makeNetworkMapUpdater() = NetworkMapUpdater(
            networkMapCache,
            NodeInfoWatcher(
                    configuration.baseDirectory,
                    @Suppress("LeakingThis")
                    rxIoScheduler,
                    Duration.ofMillis(configuration.additionalNodeInfoPollingFrequencyMsec)
            ),
            networkMapClient,
            configuration.baseDirectory,
            configuration.extraNetworkMapKeys,
            networkParametersStorage
    )

    protected open fun makeNodeSchedulerService() = NodeSchedulerService(
            platformClock,
            database,
            flowStarter,
            servicesForResolution,
            flowLogicRefFactory,
            nodeProperties,
            configuration.drainingModePollPeriod,
            unfinishedSchedules = busyNodeLatch
    ).tokenize()

    private fun makeCordappLoader(configuration: NodeConfiguration, versionInfo: VersionInfo, rotatedKeys: RotatedKeys): CordappLoader {
        val generatedCordapps = mutableListOf(VirtualCordapp.generateCore(versionInfo))
        notaryLoader?.builtInNotary?.let { notaryImpl ->
            generatedCordapps += notaryImpl
        }

        val blacklistedKeys = if (configuration.devMode) {
            emptyList()
        } else {
            parseSecureHashConfiguration(configuration.cordappSignerKeyFingerprintBlacklist) { "Error while adding key fingerprint $it to blacklistedAttachmentSigningKeys" }
        }
        return JarScanningCordappLoader.fromDirectories(
                configuration.cordappDirectories,
                versionInfo,
                extraCordapps = generatedCordapps,
                signerKeyFingerprintBlacklist = blacklistedKeys,
                rotatedKeys = rotatedKeys
        )
    }

    private fun parseSecureHashConfiguration(unparsedConfig: List<String>, errorMessage: (String) -> String): List<SecureHash> {
        return unparsedConfig.map {
            try {
                SecureHash.create(it)
            } catch (e: IllegalArgumentException) {
                log.error("${errorMessage(it)} due to - ${e.message}", e)
                throw e
            }
        }
    }

    private fun makeRotatedKeysService( configuration: NodeConfiguration ): RotatedKeys {
        return RotatedKeys(configuration.rotatedCordappSignerKeys.map { rotatedKeysConfiguration ->
            parseSecureHashConfiguration(rotatedKeysConfiguration.rotatedKeys) { "Error while parsing rotated keys $it"}
        }.toList())
    }

    private fun makeAttachmentTrustCalculator(
        configuration: NodeConfiguration,
        database: CordaPersistence,
        rotatedKeys: RotatedKeys
    ): AttachmentTrustCalculator {
        val blacklistedAttachmentSigningKeys: List<SecureHash> =
            parseSecureHashConfiguration(configuration.blacklistedAttachmentSigningKeys) { "Error while adding signing key $it to blacklistedAttachmentSigningKeys" }
        return NodeAttachmentTrustCalculator(
            attachmentStorage = attachments,
            database = database,
            cacheFactory = cacheFactory,
            blacklistedAttachmentSigningKeys = blacklistedAttachmentSigningKeys,
            rotatedKeys = rotatedKeys
        ).tokenize()
    }

    protected open fun createExternalOperationExecutor(numberOfThreads: Int): ExecutorService {
        when (numberOfThreads) {
            1 -> log.info("Flow external operation executor has $numberOfThreads thread")
            else -> log.info("Flow external operation executor has a max of $numberOfThreads threads")
        }
        // Start with 1 thread and scale up to the configured thread pool size if needed
        // Parameters of [ThreadPoolExecutor] based on [Executors.newFixedThreadPool]
        return namedThreadPoolExecutor(
                corePoolSize = 1,
                maxPoolSize = numberOfThreads,
                idleKeepAlive = 0.millis,
                poolName = "flow-external-operation-thread",
                daemonThreads = true
        )
    }

    private class ServiceInstantiationException(cause: Throwable?) : CordaException("Service Instantiation Error", cause)

    private fun installCordaServices() {
        val loadedServices = cordappLoader.cordapps.flatMap { it.services }

        // This sets the Cordapp classloader on the contextClassLoader of the current thread, prior to initializing services
        // Needed because of bug CORDA-2653 - some Corda services can utilise third-party libraries that require access to
        // the Thread context class loader
        val oldContextClassLoader: ClassLoader? = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = cordappLoader.appClassLoader

            loadedServices.forEach {
                try {
                    installCordaService(it)
                } catch (e: NoSuchMethodException) {
                    log.error("${it.name}, as a Corda service, must have a constructor with a single parameter of type " +
                            ServiceHub::class.java.name)
                    throw e
                } catch (e: ServiceInstantiationException) {
                    if (e.cause != null) {
                        log.error("Corda service ${it.name} failed to instantiate. Reason was: ${e.cause?.rootMessage}", e.cause)
                    } else {
                        log.error("Corda service ${it.name} failed to instantiate", e)
                    }
                    throw e
                } catch (e: Exception) {
                    log.error("Unable to install Corda service ${it.name}", e)
                    throw e
                }
            }
        } finally {
            Thread.currentThread().contextClassLoader = oldContextClassLoader
        }
    }

    fun <T : SerializeAsToken> installCordaService(serviceClass: Class<T>): T {
        serviceClass.requireAnnotation<CordaService>()

        val service = try {
            val serviceContext = AppServiceHubImpl<T>(services, flowStarter, transactionSupport, nodeLifecycleEventsDistributor)
            val extendedServiceConstructor = serviceClass.getDeclaredConstructor(AppServiceHub::class.java).apply { isAccessible = true }
            val service = extendedServiceConstructor.newInstance(serviceContext)
            serviceContext.serviceInstance = service
            service
        } catch (ex: NoSuchMethodException) {
            val constructor = serviceClass.getDeclaredConstructor(ServiceHub::class.java).apply { isAccessible = true }
            log.warn("${serviceClass.name} is using legacy CordaService constructor with ServiceHub parameter. " +
                    "Upgrade to an AppServiceHub parameter to enable updated API features.")
            constructor.newInstance(services)
        } catch (e: InvocationTargetException) {
            throw ServiceInstantiationException(e.cause)
        }

        cordappServices.putInstance(serviceClass, service)

        service.tokenize()
        log.info("Installed ${serviceClass.name} Corda service")

        return service
    }

    private class TelemetryComponentInstantiationException(cause: Throwable?) : CordaException("Service Instantiation Error", cause)

    @Suppress("ThrowsCount", "ComplexMethod", "NestedBlockDepth")
    private fun installTelemetryComponents() {
        val loadedTelemetryComponents: List<Class<out TelemetryComponent>> = cordappLoader.cordapps.flatMap { it.telemetryComponents }.filterNot {
                  it.name == OpenTelemetryComponent::class.java.name ||
                  it.name == SimpleLogTelemetryComponent::class.java.name }

        // This sets the Cordapp classloader on the contextClassLoader of the current thread, prior to initializing telemetry components
        // Needed because of bug CORDA-2653 - some telemetry components can utilise third-party libraries that require access to
        // the Thread context class loader. (Same as installCordaServices).
        val oldContextClassLoader: ClassLoader? = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = cordappLoader.appClassLoader

            loadedTelemetryComponents.forEach {
                try {
                    installTelemetryComponent(it)
                } catch (e: NoSuchMethodException) {
                    log.error("Missing no arg ctor for ${it.name}")
                    throw e
                } catch (e: TelemetryComponentInstantiationException) {
                    if (e.cause != null) {
                        log.error("Corda telemetry component ${it.name} failed to instantiate. Reason was: ${e.cause?.rootMessage}", e.cause)
                    } else {
                        log.error("Corda telemetry component ${it.name} failed to instantiate", e)
                    }
                    throw e
                } catch (e: Exception) {
                    log.error("Unable to install Corda telemetry component ${it.name}", e)
                    throw e
                }
            }
        } finally {
            Thread.currentThread().contextClassLoader = oldContextClassLoader
        }
    }

    private fun <T : TelemetryComponent> installTelemetryComponent(telemetryComponentClass: Class<T>) {
        val telemetryComponent = try {
            val extendedTelemetryComponentConstructor = telemetryComponentClass.getDeclaredConstructor().apply { isAccessible = true }
            val telemetryComponent = extendedTelemetryComponentConstructor.newInstance()
            telemetryComponent
        } catch (e: InvocationTargetException) {
            throw TelemetryComponentInstantiationException(e.cause)
        }
        cordappTelemetryComponents.putInstance(telemetryComponentClass, telemetryComponent)
        if (telemetryComponent.isEnabled()) {
            telemetryService.addTelemetryComponent(telemetryComponent)
            log.info("Installed ${telemetryComponentClass.name} Telemetry component")
        }
        else {
            log.info("${telemetryComponentClass.name} not enabled so not installing")
        }
    }

    private fun registerCordappFlows() {
        cordappLoader.cordapps.forEach { cordapp ->
            cordapp.initiatedFlows.groupBy { it.requireAnnotation<InitiatedBy>().value.java }.forEach { initiator, responders ->
                responders.forEach { responder ->
                    try {
                        flowManager.registerInitiatedFlow(initiator, responder)
                    } catch (e: NoSuchMethodException) {
                        log.error("${responder.name}, as an initiated flow, must have a constructor with a single parameter " +
                                "of type ${Party::class.java.name}")
                        throw e
                    }
                }
            }
        }
        flowManager.validateRegistrations()
    }

    private fun installCoreFlows() {
        installFinalityHandler()
        flowManager.registerInitiatedCoreFlowFactory(NotaryChangeFlow::class, NotaryChangeHandler::class, ::NotaryChangeHandler)
        flowManager.registerInitiatedCoreFlowFactory(ContractUpgradeFlow.Initiate::class, NotaryChangeHandler::class, ::ContractUpgradeHandler)
        flowManager.registerInitiatedCoreFlowFactory(SwapIdentitiesFlow::class, SwapIdentitiesHandler::class, ::SwapIdentitiesHandler)
    }

    // Ideally we should be disabling the FinalityHandler if it's not needed, to prevent any party from submitting transactions to us without
    // us checking. Previously this was gated on app target version and if there were no apps with target version <= 3 then the handler would
    // be disabled. However this prevents seemless rolling-upgrades and so it was removed until a better solution comes along.
    private fun installFinalityHandler() {
        flowManager.registerInitiatedCoreFlowFactory(FinalityFlow::class, FinalityHandler::class, ::FinalityHandler)
    }

    protected open fun makeTransactionStorage(transactionCacheSizeBytes: Long): WritableTransactionStorage {
        return DBTransactionStorageLedgerRecovery(database, cacheFactory, platformClock, encryptionService, partyInfoCache)
    }

    protected open fun makeNetworkParametersStorage(): NetworkParametersStorage {
        return DBNetworkParametersStorage(cacheFactory, database, networkMapClient).tokenize()
    }

    protected open fun makeCryptoService(): CryptoService {
        return BCCryptoService(configuration.myLegalName.x500Principal, configuration.signingCertificateStore)
    }

    @VisibleForTesting
    protected open fun acceptableLiveFiberCountOnStop(): Int = 0

    // Specific class so that MockNode can catch it.
    class DatabaseConfigurationException(message: String) : CordaException(message)

    protected open fun startDatabase() {
        val props = configuration.dataSourceProperties
        if (props.isEmpty) throw DatabaseConfigurationException("There must be a database configured.")
        startHikariPool()
        // Now log the vendor string as this will also cause a connection to be tested eagerly.
        logVendorString(database, log)
    }

    protected open fun startHikariPool() =
            database.startHikariPool(configuration.dataSourceProperties, metricRegistry) { dataSource, haveCheckpoints ->
        SchemaMigration(dataSource, cordappLoader, configuration.networkParametersPath, configuration.myLegalName)
                .checkOrUpdate(schemaService.internalSchemas, runMigrationScripts, haveCheckpoints, true)
                .checkOrUpdate(schemaService.appSchemas, runMigrationScripts, haveCheckpoints && !allowAppSchemaUpgradeWithCheckpoints, !configuration.devMode)
    }

    /** Loads and starts a notary service if it is configured. */
    private fun maybeStartNotaryService(myNotaryIdentity: PartyAndCertificate?): NotaryService? {
        return notaryLoader?.let { loader ->
            val service = loader.loadService(myNotaryIdentity, services, cordappLoader)

            service.run {
                tokenize()
                runOnStop += ::stop
                registerInitiatingFlows()
                start()
            }
            return service
        }
    }

    private fun NotaryService.registerInitiatingFlows() {
        if (configuration.notary?.enableOverridableFlows == true) {
            initiatingFlows.forEach { (flow, factory) ->
                flowManager.registerInitiatedCoreFlowFactory(flow, factory)
            }
        } else {
            flowManager.registerInitiatedCoreFlowFactory(NotaryFlow.Client::class, ::createServiceFlow)
        }
    }

    protected open fun makeKeyManagementService(identityService: PersistentIdentityService): KeyManagementServiceInternal {
        // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
        // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
        // the identity key. But the infrastructure to make that easy isn't here yet.
        return BasicHSMKeyManagementService(cacheFactory, identityService, database, cryptoService, telemetryService)
    }

    open fun stop() {

        setNodeStatus(NodeStatus.STOPPING)

        nodeLifecycleEventsDistributor.distributeEvent(NodeLifecycleEvent.StateMachineStopped(nodeServicesContext))
        nodeLifecycleEventsDistributor.distributeEvent(NodeLifecycleEvent.BeforeNodeStop(nodeServicesContext))

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
        nodeLifecycleEventsDistributor.distributeEvent(NodeLifecycleEvent.AfterNodeStop(nodeServicesContext)).then {
            nodeLifecycleEventsDistributor.close()
        }
    }

    protected abstract fun makeMessagingService(): MessagingService

    protected abstract fun startMessagingService(rpcOps: List<RPCOps>,
                                                 nodeInfo: NodeInfo,
                                                 myNotaryIdentity: PartyAndCertificate?,
                                                 networkParameters: NetworkParameters)

    protected open fun makeVaultService(keyManagementService: KeyManagementService,
                                        services: NodeServicesForResolution,
                                        database: CordaPersistence,
                                        cordappLoader: CordappLoader): VaultServiceInternal {
        return NodeVaultService(platformClock, keyManagementService, services, database, schemaService, cordappLoader.appClassLoader)
    }

    // JDK 11: switch to directly instantiating jolokia server (rather than indirectly via dynamically self attaching Java Agents,
    // which is no longer supported from JDK 9 onwards (https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8180425).
    // No longer need to use https://github.com/electronicarts/ea-agent-loader either (which is also deprecated)
    private fun initialiseJolokia() {
        configuration.jmxMonitoringHttpPort?.let { port ->
            val config = JolokiaServerConfig(mapOf("port" to port.toString()))
            val server = JolokiaServer(config, false)
            log.info("Starting Jolokia server on HTTP port: $port")
            server.start()
        }
    }

    inner class ServiceHubInternalImpl : SingletonSerializeAsToken(), ServiceHubInternal, ServicesForResolution by servicesForResolution, NetworkParameterUpdateListener {
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
        override val cacheFactory: NamedCacheFactory get() = this@AbstractNode.cacheFactory
        override val networkParametersService: NetworkParametersStorage get() = this@AbstractNode.networkParametersStorage
        override val attachmentTrustCalculator: AttachmentTrustCalculator get() = this@AbstractNode.attachmentTrustCalculator
        override val diagnosticsService: DiagnosticsService get() = this@AbstractNode.diagnosticsService
        override val externalOperationExecutor: ExecutorService get() = this@AbstractNode.externalOperationExecutor
        override val notaryService: NotaryService? get() = this@AbstractNode.notaryService
        override val rotatedKeys: RotatedKeys get() = this@AbstractNode.rotatedKeys
        override val telemetryService: TelemetryService get() = this@AbstractNode.telemetryService

        private lateinit var _myInfo: NodeInfo
        override val myInfo: NodeInfo get() = _myInfo

        override val attachmentsClassLoaderCache: AttachmentsClassLoaderCache get() = this@AbstractNode.attachmentsClassLoaderCache

        @Volatile
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

        override fun <T : TelemetryComponent> cordaTelemetryComponent(type: Class<T>): T {
            return cordappTelemetryComponents.getInstance(type)
                    ?: throw IllegalArgumentException("Corda telemetry component ${type.name} does not exist")
        }

        override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
            return flowManager.getFlowFactoryForInitiatingFlow(initiatingFlowClass)
        }

        /**
         * Exposes the database connection as a [RestrictedConnection] to the users.
         */
        override fun jdbcSession(): Connection = RestrictedConnection(database.createSession(), services)

        @Suppress("TooGenericExceptionCaught")
        override fun <T : Any?> withEntityManager(block: EntityManager.() -> T): T {
            return database.transaction(useErrorHandler = false) {
                session.flush()
                val manager = entityManager
                withSavePoint { savepoint ->
                    // Restrict what entity manager they can use inside the block
                    try {
                        block(RestrictedEntityManager(manager, services)).also {
                            if (!manager.transaction.rollbackOnly) {
                                manager.flush()
                            } else {
                                connection.rollback(savepoint)
                            }
                        }
                    } catch (e: Exception) {
                        if (manager.transaction.rollbackOnly) {
                            connection.rollback(savepoint)
                        }
                        throw e
                    } finally {
                        manager.close()
                    }
                }
            }
        }

        private fun <T: Any?> DatabaseTransaction.withSavePoint(block: (savepoint: Savepoint) -> T) : T {
            val savepoint = connection.setSavepoint()
            return try {
                block(savepoint)
            } finally {
                // Release the save point even if we occur an error
                if (savepoint.supportsReleasing()) {
                    connection.releaseSavepoint(savepoint)
                }
            }
        }

        /**
         * Not all databases support releasing of savepoints.
         * The type of savepoints are referenced by string names since we do not have access to the JDBC drivers
         * at compile time.
         */
        private fun Savepoint.supportsReleasing(): Boolean {
            return this::class.simpleName != "SQLServerSavepoint" && this::class.simpleName != "OracleSavepoint"
        }

        override fun withEntityManager(block: Consumer<EntityManager>) {
            withEntityManager {
                block.accept(this)
            }
        }

        // allows services to register handlers to be informed when the node stop method is called
        override fun registerUnloadHandler(runOnStop: () -> Unit) {
            this@AbstractNode.runOnStop += runOnStop
        }

        override fun specialise(ltx: LedgerTransaction): LedgerTransaction {
            return servicesForResolution.specialise(ltx)
        }

        override fun onNewNetworkParameters(networkParameters: NetworkParameters) {
            this._networkParameters = networkParameters
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
class FlowStarterImpl(
    private val smm: StateMachineManager,
    private val flowLogicRefFactory: FlowLogicRefFactory,
    private val maxClientIdLength: Int
) : FlowStarter {
    override fun <T> startFlow(event: ExternalEvent.ExternalStartFlowEvent<T>): CordaFuture<out FlowStateMachineHandle<T>> {
        val clientId = event.context.clientId
        if (clientId != null && clientId.length > maxClientIdLength) {
            throw IllegalArgumentException("clientId cannot be longer than $maxClientIdLength characters")
        } else {
            smm.deliverExternalEvent(event)
        }
        return event.future
    }

    override fun <T> startFlow(logic: FlowLogic<T>, context: InvocationContext): CordaFuture<out FlowStateMachineHandle<T>> {
        val startFlowEvent = object : ExternalEvent.ExternalStartFlowEvent<T>, DeduplicationHandler {
            override fun insideDatabaseTransaction() {}

            override fun afterDatabaseTransaction() {}

            override val externalCause: ExternalEvent
                get() = this
            override val deduplicationHandler: DeduplicationHandler
                get() = this

            override val flowId: StateMachineRunId = StateMachineRunId.createRandom()
            override val flowLogic: FlowLogic<T>
                get() = logic
            override val context: InvocationContext
                get() = context

            override fun wireUpFuture(flowFuture: CordaFuture<out FlowStateMachineHandle<T>>) {
                _future.captureLater(flowFuture)
            }

            private val _future = openFuture<FlowStateMachineHandle<T>>()
            override val future: CordaFuture<FlowStateMachineHandle<T>>
                get() = _future
        }
        return startFlow(startFlowEvent)
    }

    override fun <T> invokeFlowAsync(
            logicType: Class<out FlowLogic<T>>,
            context: InvocationContext,
            vararg args: Any?): CordaFuture<out FlowStateMachineHandle<T>> {
        val logicRef = flowLogicRefFactory.createForRPC(logicType, *args)
        val logic: FlowLogic<T> = uncheckedCast(flowLogicRefFactory.toFlowLogic(logicRef))
        return startFlow(logic, context)
    }
}

class ConfigurationException(message: String) : CordaException(message)

@Suppress("LongParameterList")
fun createCordaPersistence(databaseConfig: DatabaseConfig,
                           wellKnownPartyFromX500Name: (CordaX500Name) -> Party?,
                           wellKnownPartyFromAnonymous: (AbstractParty) -> Party?,
                           schemaService: SchemaService,
                           hikariProperties: Properties,
                           cacheFactory: NamedCacheFactory,
                           customClassLoader: ClassLoader?,
                           allowHibernateToManageAppSchema: Boolean = false): CordaPersistence {
    // Register the AbstractPartyDescriptor so Hibernate doesn't warn when encountering AbstractParty. Unfortunately
    // Hibernate warns about not being able to find a descriptor if we don't provide one, but won't use it by default
    // so we end up providing both descriptor and converter. We should re-examine this in later versions to see if
    // either Hibernate can be convinced to stop warning, use the descriptor by default, or something else.
    @Suppress("DEPRECATION")
    org.hibernate.type.descriptor.java.JavaTypeDescriptorRegistry.INSTANCE.addDescriptor(AbstractPartyDescriptor(wellKnownPartyFromX500Name, wellKnownPartyFromAnonymous))
    val attributeConverters = listOf(PublicKeyToTextConverter(), AbstractPartyToX500NameAsStringConverter(wellKnownPartyFromX500Name, wellKnownPartyFromAnonymous))

    val jdbcUrl = hikariProperties.getProperty("dataSource.url", "")
    return CordaPersistence(
            databaseConfig.exportHibernateJMXStatistics,
            schemaService.schemas,
            jdbcUrl,
            cacheFactory,
            attributeConverters, customClassLoader,
            errorHandler = { e ->
                // "corrupting" a DatabaseTransaction only inside a flow state machine execution
                FlowStateMachineImpl.currentStateMachine()?.let {
                    // register only the very first exception thrown throughout a chain of logical transactions
                    setException(e)
                }
            },
            allowHibernateToManageAppSchema = allowHibernateToManageAppSchema)
}

@Suppress("ThrowsCount")
fun CordaPersistence.startHikariPool(
        hikariProperties: Properties,
        metricRegistry: MetricRegistry? = null,
        schemaMigration: (DataSource, Boolean) -> Unit) {
    try {
        val dataSource = DataSourceFactory.createDataSource(hikariProperties, metricRegistry = metricRegistry)
        val haveCheckpoints = dataSource.connection.use { DBCheckpointStorage.getCheckpointCount(it) != 0L }

        schemaMigration(dataSource, haveCheckpoints)
        start(dataSource)
    } catch (ex: Exception) {
        when {
            ex is HikariPool.PoolInitializationException -> throw CouldNotCreateDataSourceException(
                    "Could not connect to the database. Please check your JDBC connection URL, or the connectivity to the database.",
                    NodeDatabaseErrors.COULD_NOT_CONNECT,
                    cause = ex)
            ex.cause is ClassNotFoundException -> throw CouldNotCreateDataSourceException(
                    "Could not find the database driver class. Please add it to the 'drivers' folder.",
                    NodeDatabaseErrors.MISSING_DRIVER)
            ex is OutstandingDatabaseChangesException -> throw (DatabaseIncompatibleException(ex.message))
            else -> {
                val msg = ex.message ?: ex::class.java.canonicalName
                throw CouldNotCreateDataSourceException(
                        "Could not create the DataSource: ${ex.message}",
                        NodeDatabaseErrors.FAILED_STARTUP,
                        cause = ex,
                        parameters = listOf(msg))
            }
        }
    }
}

fun SchemaMigration.checkOrUpdate(schemas: Set<MappedSchema>, update: Boolean, haveCheckpoints: Boolean, forceThrowOnMissingMigration: Boolean): SchemaMigration {
    if (update)
        this.runMigration(haveCheckpoints, schemas, forceThrowOnMissingMigration)
    else
        this.checkState(schemas, forceThrowOnMissingMigration)
    return this
}

fun clientSslOptionsCompatibleWith(nodeRpcOptions: NodeRpcOptions): ClientRpcSslOptions? {

    if (!nodeRpcOptions.useSsl || nodeRpcOptions.sslConfig == null) {
        return null
    }
    // Here we're using the node's RPC key store as the RPC client's trust store.
    return ClientRpcSslOptions(trustStorePath = nodeRpcOptions.sslConfig!!.keyStorePath, trustStorePassword = nodeRpcOptions.sslConfig!!.keyStorePassword)
}
