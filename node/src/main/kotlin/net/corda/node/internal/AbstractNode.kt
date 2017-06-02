package net.corda.node.internal

import com.codahale.metrics.MetricRegistry
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.MutableClassToInstanceMap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import net.corda.core.*
import net.corda.core.crypto.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.*
import net.corda.core.node.services.*
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.debug
import net.corda.flows.*
import net.corda.node.services.*
import net.corda.node.services.api.*
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.events.ScheduledActivityObserver
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.network.NetworkMapService.RegistrationResponse
import net.corda.node.services.network.NodeRegistration
import net.corda.node.services.network.PersistentNetworkMapService
import net.corda.node.services.persistence.*
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.statemachine.flowVersionAndInitiatingClass
import net.corda.node.services.transactions.*
import net.corda.node.services.vault.CashBalanceAsMetricsObserver
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.services.vault.VaultSoftLockManager
import net.corda.node.utilities.AddOrRemove.ADD
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.transaction
import org.apache.activemq.artemis.utils.ReusableLatch
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.jetbrains.exposed.sql.Database
import org.slf4j.Logger
import rx.Observable
import java.io.IOException
import java.lang.reflect.Modifier.*
import java.net.JarURLConnection
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.KeyStoreException
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit.SECONDS
import java.util.stream.Collectors.toList
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import net.corda.core.crypto.generateKeyPair as cryptoGenerateKeyPair

/**
 * A base node implementation that can be customised either for production (with real implementations that do real
 * I/O), or a mock implementation suitable for unit test environments.
 *
 * Marked as SingletonSerializeAsToken to prevent the invisible reference to AbstractNode in the ServiceHub accidentally
 * sweeping up the Node into the Kryo checkpoint serialization via any flows holding a reference to ServiceHub.
 */
// TODO: Where this node is the initial network map service, currently no networkMapService is provided.
// In theory the NodeInfo for the node should be passed in, instead, however currently this is constructed by the
// AbstractNode. It should be possible to generate the NodeInfo outside of AbstractNode, so it can be passed in.
abstract class AbstractNode(open val configuration: NodeConfiguration,
                            val advertisedServices: Set<ServiceInfo>,
                            val platformClock: Clock,
                            @VisibleForTesting val busyNodeLatch: ReusableLatch = ReusableLatch()) : SingletonSerializeAsToken() {

    // TODO: Persist this, as well as whether the node is registered.
    /**
     * Sequence number of changes sent to the network map service, when registering/de-registering this node.
     */
    var networkMapSeq: Long = 1

    protected abstract val log: Logger
    protected abstract val networkMapAddress: SingleMessageRecipient?
    protected abstract val platformVersion: Int

    // We will run as much stuff in this single thread as possible to keep the risk of thread safety bugs low during the
    // low-performance prototyping period.
    protected abstract val serverThread: AffinityExecutor

    private val cordappServices = MutableClassToInstanceMap.create<SerializeAsToken>()
    private val flowFactories = ConcurrentHashMap<Class<out FlowLogic<*>>, InitiatedFlowFactory<*>>()
    protected val partyKeys = mutableSetOf<KeyPair>()

    val services = object : ServiceHubInternal() {
        override val networkService: MessagingService get() = net
        override val networkMapCache: NetworkMapCacheInternal get() = netMapCache
        override val storageService: TxWritableStorageService get() = storage
        override val vaultService: VaultService get() = vault
        override val keyManagementService: KeyManagementService get() = keyManagement
        override val identityService: IdentityService get() = identity
        override val schedulerService: SchedulerService get() = scheduler
        override val clock: Clock get() = platformClock
        override val myInfo: NodeInfo get() = info
        override val schemaService: SchemaService get() = schemas
        override val transactionVerifierService: TransactionVerifierService get() = txVerifierService
        override val auditService: AuditService get() = auditService

        override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
            require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
            return cordappServices.getInstance(type) ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
        }

        override val rpcFlows: List<Class<out FlowLogic<*>>> get() = this@AbstractNode.rpcFlows

        // Internal only
        override val monitoringService: MonitoringService = MonitoringService(MetricRegistry())

        override fun <T> startFlow(logic: FlowLogic<T>, flowInitiator: FlowInitiator): FlowStateMachineImpl<T> {
            return serverThread.fetchFrom { smm.add(logic, flowInitiator) }
        }

        override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
            return flowFactories[initiatingFlowClass]
        }

        override fun recordTransactions(txs: Iterable<SignedTransaction>) {
            database.transaction {
                recordTransactionsInternal(storage, txs)
            }
        }
    }

    open fun findMyLocation(): PhysicalLocation? {
        return configuration.myLegalName.locationOrNull?.let { CityDatabase[it] }
    }

    lateinit var info: NodeInfo
    lateinit var storage: TxWritableStorageService
    lateinit var checkpointStorage: CheckpointStorage
    lateinit var smm: StateMachineManager
    lateinit var vault: VaultService
    lateinit var keyManagement: KeyManagementService
    var inNodeNetworkMapService: NetworkMapService? = null
    lateinit var txVerifierService: TransactionVerifierService
    lateinit var identity: IdentityService
    lateinit var net: MessagingService
    lateinit var netMapCache: NetworkMapCacheInternal
    lateinit var scheduler: NodeSchedulerService
    lateinit var schemas: SchemaService
    lateinit var auditService: AuditService
    protected val runOnStop: ArrayList<Runnable> = ArrayList()
    lateinit var database: Database
    protected var dbCloser: Runnable? = null
    private lateinit var rpcFlows: List<Class<out FlowLogic<*>>>

    var isPreviousCheckpointsPresent = false
        private set

    protected val _networkMapRegistrationFuture: SettableFuture<Unit> = SettableFuture.create()
    /** Completes once the node has successfully registered with the network map service */
    val networkMapRegistrationFuture: ListenableFuture<Unit>
        get() = _networkMapRegistrationFuture

    /** Fetch CordaPluginRegistry classes registered in META-INF/services/net.corda.core.node.CordaPluginRegistry files that exist in the classpath */
    open val pluginRegistries: List<CordaPluginRegistry> by lazy {
        ServiceLoader.load(CordaPluginRegistry::class.java).toList()
    }

    /** Set to true once [start] has been successfully called. */
    @Volatile var started = false
        private set

    /** The implementation of the [CordaRPCOps] interface used by this node. */
    open val rpcOps: CordaRPCOps by lazy { CordaRPCOpsImpl(services, smm, database) }   // Lazy to avoid init ordering issue with the SMM.

    open fun start(): AbstractNode {
        require(!started) { "Node has already been started" }

        if (configuration.devMode) {
            log.warn("Corda node is running in dev mode.")
            configuration.configureWithDevSSLCertificate()
        }
        require(hasSSLCertificates()) {
            "Identity certificate not found. " +
                    "Please either copy your existing identity key and certificate from another node, " +
                    "or if you don't have one yet, fill out the config file and run corda.jar --initial-registration. " +
                    "Read more at: https://docs.corda.net/permissioning.html"
        }

        log.info("Node starting up ...")

        // Do all of this in a database transaction so anything that might need a connection has one.
        initialiseDatabasePersistence {
            val tokenizableServices = makeServices()

            smm = StateMachineManager(services,
                    checkpointStorage,
                    serverThread,
                    database,
                    busyNodeLatch)

            smm.tokenizableServices.addAll(tokenizableServices)

            if (serverThread is ExecutorService) {
                runOnStop += Runnable {
                    // We wait here, even though any in-flight messages should have been drained away because the
                    // server thread can potentially have other non-messaging tasks scheduled onto it. The timeout value is
                    // arbitrary and might be inappropriate.
                    MoreExecutors.shutdownAndAwaitTermination(serverThread as ExecutorService, 50, SECONDS)
                }
            }

            makeVaultObservers()

            checkpointStorage.forEach {
                isPreviousCheckpointsPresent = true
                false
            }
            startMessagingService(rpcOps)
            installCoreFlows()

            val scanResult = scanCordapps()
            if (scanResult != null) {
                installCordaServices(scanResult)
                registerInitiatedFlows(scanResult)
                rpcFlows = findRPCFlows(scanResult)
            } else {
                rpcFlows = emptyList()
            }

            // TODO Remove this once the cash stuff is in its own CorDapp
            registerInitiatedFlow(IssuerFlow.Issuer::class.java)

            initUploaders()

            runOnStop += Runnable { net.stop() }
            _networkMapRegistrationFuture.setFuture(registerWithNetworkMapIfConfigured())
            smm.start()
            // Shut down the SMM so no Fibers are scheduled.
            runOnStop += Runnable { smm.stop(acceptableLiveFiberCountOnStop()) }
            scheduler.start()
        }
        started = true
        return this
    }

    private fun installCordaServices(scanResult: ScanResult) {
        fun getServiceType(clazz: Class<*>): ServiceType? {
            return try {
                clazz.getField("type").get(null) as ServiceType
            } catch (e: NoSuchFieldException) {
                log.warn("${clazz.name} does not have a type field, optimistically proceeding with install.")
                null
            }
        }

        return scanResult.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
                .filter {
                    val serviceType = getServiceType(it)
                    if (serviceType != null && info.serviceIdentities(serviceType).isEmpty()) {
                        log.debug { "Ignoring ${it.name} as a Corda service since $serviceType is not one of our " +
                                "advertised services" }
                        false
                    } else {
                        true
                    }
                }
                .forEach {
                    try {
                        installCordaService(it)
                    } catch (e: NoSuchMethodException) {
                        log.error("${it.name}, as a Corda service, must have a constructor with a single parameter " +
                                "of type ${PluginServiceHub::class.java.name}")
                    } catch (e: Exception) {
                        log.error("Unable to install Corda service ${it.name}", e)
                    }
                }
    }

    /**
     * Use this method to install your Corda services in your tests. This is automatically done by the node when it
     * starts up for all classes it finds which are annotated with [CordaService].
     */
    fun <T : SerializeAsToken> installCordaService(clazz: Class<T>): T {
        clazz.requireAnnotation<CordaService>()
        val ctor = clazz.getDeclaredConstructor(PluginServiceHub::class.java).apply { isAccessible = true }
        val service = ctor.newInstance(services)
        cordappServices.putInstance(clazz, service)
        smm.tokenizableServices += service
        log.info("Installed ${clazz.name} Corda service")
        return service
    }

    private inline fun <reified A : Annotation> Class<*>.requireAnnotation(): A {
        return requireNotNull(getDeclaredAnnotation(A::class.java)) { "$name needs to be annotated with ${A::class.java.name}" }
    }

    private fun registerInitiatedFlows(scanResult: ScanResult) {
        scanResult
                .getClassesWithAnnotation(FlowLogic::class, InitiatedBy::class)
                // First group by the initiating flow class in case there are multiple mappings
                .groupBy { it.requireAnnotation<InitiatedBy>().value.java }
                .map { (initiatingFlow, initiatedFlows) ->
                    val sorted = initiatedFlows.sortedWith(FlowTypeHierarchyComparator(initiatingFlow))
                    if (sorted.size > 1) {
                        log.warn("${initiatingFlow.name} has been specified as the inititating flow by multiple flows " +
                                "in the same type hierarchy: ${sorted.joinToString { it.name }}. Choosing the most " +
                                "specific sub-type for registration: ${sorted[0].name}.")
                    }
                    sorted[0]
                }
                .forEach {
                    try {
                        registerInitiatedFlowInternal(it, track = false)
                    } catch (e: NoSuchMethodException) {
                        log.error("${it.name}, as an initiated flow, must have a constructor with a single parameter " +
                                "of type ${Party::class.java.name}")
                    } catch (e: Exception) {
                        log.error("Unable to register initiated flow ${it.name}", e)
                    }
                }
    }

    private class FlowTypeHierarchyComparator(val initiatingFlow: Class<out FlowLogic<*>>) : Comparator<Class<out FlowLogic<*>>> {
        override fun compare(o1: Class<out FlowLogic<*>>, o2: Class<out FlowLogic<*>>): Int {
            return if (o1 == o2) {
                0
            } else if (o1.isAssignableFrom(o2)) {
                1
            } else if (o2.isAssignableFrom(o1)) {
                -1
            } else {
                throw IllegalArgumentException("${initiatingFlow.name} has been specified as the initiating flow by " +
                        "both ${o1.name} and ${o2.name}")
            }
        }
    }

    /**
     * Use this method to register your initiated flows in your tests. This is automatically done by the node when it
     * starts up for all [FlowLogic] classes it finds which are annotated with [InitiatedBy].
     * @return An [Observable] of the initiated flows started by counter-parties.
     */
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>): Observable<T> {
        return registerInitiatedFlowInternal(initiatedFlowClass, track = true)
    }

    private fun <F : FlowLogic<*>> registerInitiatedFlowInternal(initiatedFlow: Class<F>, track: Boolean): Observable<F> {
        val ctor = initiatedFlow.getDeclaredConstructor(Party::class.java).apply { isAccessible = true }
        val initiatingFlow = initiatedFlow.requireAnnotation<InitiatedBy>().value.java
        val (version, classWithAnnotation) = initiatingFlow.flowVersionAndInitiatingClass
        require(classWithAnnotation == initiatingFlow) {
            "${InitiatingFlow::class.java.name} must be annotated on ${initiatingFlow.name} and not on a super-type"
        }
        val flowFactory = InitiatedFlowFactory.CorDapp(version, { ctor.newInstance(it) })
        val observable = internalRegisterFlowFactory(initiatingFlow, flowFactory, initiatedFlow, track)
        log.info("Registered ${initiatingFlow.name} to initiate ${initiatedFlow.name} (version $version)")
        return observable
    }

    @VisibleForTesting
    fun <F : FlowLogic<*>> internalRegisterFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>,
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

    private fun findRPCFlows(scanResult: ScanResult): List<Class<out FlowLogic<*>>> {
        fun Class<out FlowLogic<*>>.isUserInvokable(): Boolean {
            return isPublic(modifiers) && !isLocalClass && !isAnonymousClass && (!isMemberClass || isStatic(modifiers))
        }

        return scanResult.getClassesWithAnnotation(FlowLogic::class, StartableByRPC::class).filter { it.isUserInvokable() } +
                // Add any core flows here
                listOf(
                        ContractUpgradeFlow::class.java,
                        // TODO Remove all Cash flows from default list once they are split into separate CorDapp.
                        CashIssueFlow::class.java,
                        CashExitFlow::class.java,
                        CashPaymentFlow::class.java)
    }

    /**
     * Installs a flow that's core to the Corda platform. Unlike CorDapp flows which are versioned individually using
     * [InitiatingFlow.version], core flows have the same version as the node's platform version. To cater for backwards
     * compatibility [flowFactory] provides a second parameter which is the platform version of the initiating party.
     * @suppress
     */
    @VisibleForTesting
    fun installCoreFlow(clientFlowClass: KClass<out FlowLogic<*>>, flowFactory: (Party, Int) -> FlowLogic<*>) {
        require(clientFlowClass.java.flowVersionAndInitiatingClass.first == 1) {
            "${InitiatingFlow::class.java.name}.version not applicable for core flows; their version is the node's platform version"
        }
        flowFactories[clientFlowClass.java] = InitiatedFlowFactory.Core(flowFactory)
        log.debug { "Installed core flow ${clientFlowClass.java.name}" }
    }

    private fun installCoreFlows() {
        installCoreFlow(FetchTransactionsFlow::class) { otherParty, _ -> FetchTransactionsHandler(otherParty) }
        installCoreFlow(FetchAttachmentsFlow::class) { otherParty, _ -> FetchAttachmentsHandler(otherParty) }
        installCoreFlow(BroadcastTransactionFlow::class) { otherParty, _ -> NotifyTransactionHandler(otherParty) }
        installCoreFlow(NotaryChangeFlow::class) { otherParty, _ -> NotaryChangeHandler(otherParty) }
        installCoreFlow(ContractUpgradeFlow::class) { otherParty, _ -> ContractUpgradeHandler(otherParty) }
    }

    /**
     * Builds node internal, advertised, and plugin services.
     * Returns a list of tokenizable services to be added to the serialisation context.
     */
    private fun makeServices(): MutableList<Any> {
        val storageServices = initialiseStorageService(configuration.baseDirectory)
        storage = storageServices.first
        checkpointStorage = storageServices.second
        netMapCache = InMemoryNetworkMapCache()
        net = makeMessagingService()
        schemas = makeSchemaService()
        vault = makeVaultService(configuration.dataSourceProperties)
        txVerifierService = makeTransactionVerifierService()
        auditService = DummyAuditService()

        info = makeInfo()
        identity = makeIdentityService()
        // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
        // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
        // the identity key. But the infrastructure to make that easy isn't here yet.
        keyManagement = makeKeyManagementService(identity)
        scheduler = NodeSchedulerService(services, database, unfinishedSchedules = busyNodeLatch)

        val tokenizableServices = mutableListOf(storage, net, vault, keyManagement, identity, platformClock, scheduler)
        makeAdvertisedServices(tokenizableServices)
        return tokenizableServices
    }

    private fun scanCordapps(): ScanResult? {
        val scanPackage = System.getProperty("net.corda.node.cordapp.scan.package")
        val paths = if (scanPackage != null) {
            // Rather than looking in the plugins directory, figure out the classpath for the given package and scan that
            // instead. This is used in tests where we avoid having to package stuff up in jars and then having to move
            // them to the plugins directory for each node.
            check(configuration.devMode) { "Package scanning can only occur in dev mode" }
            val resource = scanPackage.replace('.', '/')
            javaClass.classLoader.getResources(resource)
                    .asSequence()
                    .map {
                        val uri = if (it.protocol == "jar") {
                            (it.openConnection() as JarURLConnection).jarFileURL.toURI()
                        } else {
                            URI(it.toExternalForm().removeSuffix(resource))
                        }
                        Paths.get(uri)
                    }
                    .toList()
        } else {
            val pluginsDir = configuration.baseDirectory / "plugins"
            if (!pluginsDir.exists()) return null
            pluginsDir.list {
                it.filter { it.isRegularFile() && it.toString().endsWith(".jar") }.collect(toList())
            }
        }

        log.info("Scanning CorDapps in $paths")

        // This will only scan the plugin jars and nothing else
        return if (paths.isNotEmpty()) FastClasspathScanner().overrideClasspath(paths).scan() else null
    }

    private fun <T : Any> ScanResult.getClassesWithAnnotation(type: KClass<T>, annotation: KClass<out Annotation>): List<Class<out T>> {
        fun loadClass(className: String): Class<out T>? {
            return try {
                // TODO Make sure this is loaded by the correct class loader
                Class.forName(className, false, javaClass.classLoader).asSubclass(type.java)
            } catch (e: ClassCastException) {
                log.warn("As $className is annotated with ${annotation.qualifiedName} it must be a sub-type of ${type.java.name}")
                null
            } catch (e: Exception) {
                log.warn("Unable to load class $className", e)
                null
            }
        }

        return getNamesOfClassesWithAnnotation(annotation.java)
                .mapNotNull { loadClass(it) }
                .filterNot { isAbstract(it.modifiers) }
    }

    private fun initUploaders() {
        val uploaders: List<FileUploader> = listOf(storage.attachments as NodeAttachmentService) +
            cordappServices.values.filterIsInstance(AcceptsFileUpload::class.java)
        (storage as StorageServiceImpl).initUploaders(uploaders)
    }

    private fun makeVaultObservers() {
        VaultSoftLockManager(vault, smm)
        CashBalanceAsMetricsObserver(services, database)
        ScheduledActivityObserver(services)
        HibernateObserver(vault.rawUpdates, schemas)
    }

    private fun makeInfo(): NodeInfo {
        val advertisedServiceEntries = makeServiceEntries()
        val legalIdentity = obtainLegalIdentity()
        return NodeInfo(net.myAddress, legalIdentity, platformVersion, advertisedServiceEntries, findMyLocation())
    }

    /**
     * A service entry contains the advertised [ServiceInfo] along with the service identity. The identity *name* is
     * taken from the configuration or, if non specified, generated by combining the node's legal name and the service id.
     */
    protected open fun makeServiceEntries(): List<ServiceEntry> {
        return advertisedServices.map {
            val serviceId = it.type.id
            val serviceName = it.name ?: X500Name("${configuration.myLegalName},OU=$serviceId")
            val identity = obtainKeyPair(serviceId, serviceName).first
            ServiceEntry(it, identity)
        }
    }

    @VisibleForTesting
    protected open fun acceptableLiveFiberCountOnStop(): Int = 0

    private fun hasSSLCertificates(): Boolean {
        val (sslKeystore, keystore) = try {
            // This will throw IOException if key file not found or KeyStoreException if keystore password is incorrect.
            Pair(
                    KeyStoreUtilities.loadKeyStore(configuration.sslKeystore, configuration.keyStorePassword),
                    KeyStoreUtilities.loadKeyStore(configuration.nodeKeystore, configuration.keyStorePassword))
        } catch (e: IOException) {
            return false
        } catch (e: KeyStoreException) {
            log.warn("Certificate key store found but key store password does not match configuration.")
            return false
        }
        return sslKeystore.containsAlias(X509Utilities.CORDA_CLIENT_TLS) && keystore.containsAlias(X509Utilities.CORDA_CLIENT_CA)
    }

    // Specific class so that MockNode can catch it.
    class DatabaseConfigurationException(msg: String) : Exception(msg)

    protected open fun initialiseDatabasePersistence(insideTransaction: () -> Unit) {
        val props = configuration.dataSourceProperties
        if (props.isNotEmpty()) {
            val (toClose, database) = configureDatabase(props)
            this.database = database
            // Now log the vendor string as this will also cause a connection to be tested eagerly.
            log.info("Connected to ${database.vendor} database.")
            dbCloser = Runnable { toClose.close() }
            runOnStop += dbCloser!!
            database.transaction {
                insideTransaction()
            }
        } else {
            throw DatabaseConfigurationException("There must be a database configured.")
        }
    }

    /**
     * Run any tasks that are needed to ensure the node is in a correct state before running start().
     */
    open fun setup(): AbstractNode {
        createNodeDir()
        return this
    }

    private fun makeAdvertisedServices(tokenizableServices: MutableList<Any>) {
        val serviceTypes = info.advertisedServices.map { it.info.type }
        if (NetworkMapService.type in serviceTypes) makeNetworkMapService()

        val notaryServiceType = serviceTypes.singleOrNull { it.isNotary() }
        if (notaryServiceType != null) {
            makeNotaryService(notaryServiceType, tokenizableServices)
        }
    }

    private fun registerWithNetworkMapIfConfigured(): ListenableFuture<Unit> {
        services.networkMapCache.addNode(info)
        // In the unit test environment, we may sometimes run without any network map service
        return if (networkMapAddress == null && inNodeNetworkMapService == null) {
            services.networkMapCache.runWithoutMapService()
            noNetworkMapConfigured()  // TODO This method isn't needed as runWithoutMapService sets the Future in the cache
        } else {
            registerWithNetworkMap()
        }
    }

    /**
     * Register this node with the network map cache, and load network map from a remote service (and register for
     * updates) if one has been supplied.
     */
    protected open fun registerWithNetworkMap(): ListenableFuture<Unit> {
        require(networkMapAddress != null || NetworkMapService.type in advertisedServices.map { it.type }) {
            "Initial network map address must indicate a node that provides a network map service"
        }
        val address = networkMapAddress ?: info.address
        // Register for updates, even if we're the one running the network map.
        return sendNetworkMapRegistration(address).flatMap { (error) ->
            check(error == null) { "Unable to register with the network map service: $error" }
            // The future returned addMapService will complete on the same executor as sendNetworkMapRegistration, namely the one used by net
            services.networkMapCache.addMapService(net, address, true, null)
        }
    }

    private fun sendNetworkMapRegistration(networkMapAddress: SingleMessageRecipient): ListenableFuture<RegistrationResponse> {
        // Register this node against the network
        val instant = platformClock.instant()
        val expires = instant + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val reg = NodeRegistration(info, instant.toEpochMilli(), ADD, expires)
        val legalIdentityKey = obtainLegalIdentityKey()
        val request = NetworkMapService.RegistrationRequest(reg.toWire(keyManagement, legalIdentityKey.public), net.myAddress)
        return net.sendRequest(NetworkMapService.REGISTER_TOPIC, request, networkMapAddress)
    }

    /** This is overriden by the mock node implementation to enable operation without any network map service */
    protected open fun noNetworkMapConfigured(): ListenableFuture<Unit> {
        // TODO: There should be a consistent approach to configuration error exceptions.
        throw IllegalStateException("Configuration error: this node isn't being asked to act as the network map, nor " +
                "has any other map node been configured.")
    }

    protected open fun makeKeyManagementService(identityService: IdentityService): KeyManagementService {
        return PersistentKeyManagementService(identityService, partyKeys)
    }

    open protected fun makeNetworkMapService() {
        inNodeNetworkMapService = PersistentNetworkMapService(services, configuration.minimumPlatformVersion)
    }

    open protected fun makeNotaryService(type: ServiceType, tokenizableServices: MutableList<Any>) {
        val timeWindowChecker = TimeWindowChecker(platformClock, 30.seconds)
        val uniquenessProvider = makeUniquenessProvider(type)
        tokenizableServices.add(uniquenessProvider)

        val notaryService = when (type) {
            SimpleNotaryService.type -> SimpleNotaryService(timeWindowChecker, uniquenessProvider)
            ValidatingNotaryService.type -> ValidatingNotaryService(timeWindowChecker, uniquenessProvider)
            RaftNonValidatingNotaryService.type -> RaftNonValidatingNotaryService(timeWindowChecker, uniquenessProvider as RaftUniquenessProvider)
            RaftValidatingNotaryService.type -> RaftValidatingNotaryService(timeWindowChecker, uniquenessProvider as RaftUniquenessProvider)
            BFTNonValidatingNotaryService.type -> with(configuration as FullNodeConfiguration) {
                val replicaId = bftReplicaId ?: throw IllegalArgumentException("bftReplicaId value must be specified in the configuration")
                BFTSMaRtConfig(notaryClusterAddresses).use { config ->
                    val client = BFTSMaRt.Client(config, replicaId).also { tokenizableServices += it } // (Ab)use replicaId for clientId.
                    BFTNonValidatingNotaryService(config, services, timeWindowChecker, replicaId, database, client)
                }
            }
            else -> {
                throw IllegalArgumentException("Notary type ${type.id} is not handled by makeNotaryService.")
            }
        }

        installCoreFlow(NotaryFlow.Client::class, notaryService.serviceFlowFactory)
    }

    protected abstract fun makeUniquenessProvider(type: ServiceType): UniquenessProvider

    protected open fun makeIdentityService(): IdentityService {
        val keyStore = KeyStoreUtilities.loadKeyStore(configuration.trustStoreFile, configuration.trustStorePassword)
        val trustRoot = keyStore.getCertificate(X509Utilities.CORDA_ROOT_CA) as? X509Certificate
        val service = InMemoryIdentityService(setOf(info.legalIdentityAndCert), trustRoot = trustRoot)
        services.networkMapCache.partyNodes.forEach { service.registerIdentity(it.legalIdentityAndCert) }
        netMapCache.changed.subscribe { mapChange ->
            // TODO how should we handle network map removal
            if (mapChange is MapChange.Added) {
                service.registerIdentity(mapChange.node.legalIdentityAndCert)
            }
        }
        return service
    }

    // TODO: sort out ordering of open & protected modifiers of functions in this class.
    protected open fun makeVaultService(dataSourceProperties: Properties): VaultService = NodeVaultService(services, dataSourceProperties)

    protected open fun makeSchemaService(): SchemaService = NodeSchemaService()

    protected abstract fun makeTransactionVerifierService(): TransactionVerifierService

    open fun stop() {
        // TODO: We need a good way of handling "nice to have" shutdown events, especially those that deal with the
        // network, including unsubscribing from updates from remote services. Possibly some sort of parameter to stop()
        // to indicate "Please shut down gracefully" vs "Shut down now".
        // Meanwhile, we let the remote service send us updates until the acknowledgment buffer overflows and it
        // unsubscribes us forcibly, rather than blocking the shutdown process.

        // Run shutdown hooks in opposite order to starting
        for (toRun in runOnStop.reversed()) {
            toRun.run()
        }
        runOnStop.clear()
    }

    protected abstract fun makeMessagingService(): MessagingService

    protected abstract fun startMessagingService(rpcOps: RPCOps)

    protected open fun initialiseStorageService(dir: Path): Pair<TxWritableStorageService, CheckpointStorage> {
        val attachments = makeAttachmentStorage(dir)
        val checkpointStorage = DBCheckpointStorage()
        val transactionStorage = DBTransactionStorage()
        val stateMachineTransactionMappingStorage = DBTransactionMappingStorage()
        return Pair(
                constructStorageService(attachments, transactionStorage, stateMachineTransactionMappingStorage),
                checkpointStorage
        )
    }

    protected open fun constructStorageService(attachments: AttachmentStorage,
                                               transactionStorage: TransactionStorage,
                                               stateMachineRecordedTransactionMappingStorage: StateMachineRecordedTransactionMappingStorage) =
            StorageServiceImpl(attachments, transactionStorage, stateMachineRecordedTransactionMappingStorage)

    protected fun obtainLegalIdentity(): PartyAndCertificate = identityKeyPair.first
    protected fun obtainLegalIdentityKey(): KeyPair = identityKeyPair.second
    private val identityKeyPair by lazy { obtainKeyPair("identity", configuration.myLegalName) }

    private fun obtainKeyPair(serviceId: String, serviceName: X500Name): Pair<PartyAndCertificate, KeyPair> {
        // Load the private identity key, creating it if necessary. The identity key is a long term well known key that
        // is distributed to other peers and we use it (or a key signed by it) when we need to do something
        // "permissioned". The identity file is what gets distributed and contains the node's legal name along with
        // the public key. Obviously in a real system this would need to be a certificate chain of some kind to ensure
        // the legal name is actually validated in some way.

        // TODO: Integrate with Key management service?
        val keyStore = KeyStoreWrapper(configuration.nodeKeystore, configuration.keyStorePassword)
        val privateKeyAlias = "$serviceId-private-key"
        val privKeyFile = configuration.baseDirectory / privateKeyAlias
        val pubIdentityFile = configuration.baseDirectory / "$serviceId-public"
        val certificateAndKeyPair = keyStore.certificateAndKeyPair(privateKeyAlias)
        val identityCertPathAndKey: Pair<PartyAndCertificate, KeyPair> = if (certificateAndKeyPair != null) {
            val (cert, keyPair) = certificateAndKeyPair
            // Get keys from keystore.
            val loadedServiceName = X509CertificateHolder(cert.encoded).subject
            if (loadedServiceName != serviceName) {
                throw ConfigurationException("The legal name in the config file doesn't match the stored identity keystore:" +
                        "$serviceName vs $loadedServiceName")
            }
            val certPath = X509Utilities.createCertificatePath(cert, cert, revocationEnabled = false)
            Pair(PartyAndCertificate(loadedServiceName, keyPair.public, cert, certPath), keyPair)
        } else if (privKeyFile.exists()) {
            // Get keys from key file.
            // TODO: this is here to smooth out the key storage transition, remove this in future release.
            // Check that the identity in the config file matches the identity file we have stored to disk.
            // This is just a sanity check. It shouldn't fail unless the admin has fiddled with the files and messed
            // things up for us.
            val myIdentity = pubIdentityFile.readAll().deserialize<PartyAndCertificate>()
            if (myIdentity.name != serviceName)
                throw ConfigurationException("The legal name in the config file doesn't match the stored identity file:" +
                        "$serviceName vs ${myIdentity.name}")
            // Load the private key.
            val keyPair = privKeyFile.readAll().deserialize<KeyPair>()
            if (myIdentity.owningKey !is CompositeKey) { // TODO: Support case where owningKey is a composite key.
                keyStore.save(serviceName, privateKeyAlias, keyPair)
            }
            Pair(myIdentity, keyPair)
        } else {
            val clientCA = keyStore.certificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA)!!
            // Create new keys and store in keystore.
            log.info("Identity key not found, generating fresh key!")
            val keyPair: KeyPair = generateKeyPair()
            val cert = X509Utilities.createCertificate(CertificateType.IDENTITY, clientCA.certificate, clientCA.keyPair, serviceName, keyPair.public)
            val certPath = X509Utilities.createCertificatePath(cert, cert, revocationEnabled = false)
            keyStore.save(serviceName, privateKeyAlias, keyPair)
            require(certPath.certificates.isNotEmpty()) { "Certificate path cannot be empty" }
            Pair(PartyAndCertificate(serviceName, keyPair.public, cert, certPath), keyPair)
        }
        partyKeys += identityCertPathAndKey.second
        return identityCertPathAndKey
    }

    protected open fun generateKeyPair() = cryptoGenerateKeyPair()

    protected fun makeAttachmentStorage(dir: Path): AttachmentStorage {
        val attachmentsDir = dir / "attachments"
        try {
            attachmentsDir.createDirectory()
        } catch (e: FileAlreadyExistsException) {
        }
        return NodeAttachmentService(attachmentsDir, configuration.dataSourceProperties, services.monitoringService.metrics)
    }

    protected fun createNodeDir() {
        configuration.baseDirectory.createDirectories()
    }
}

private class KeyStoreWrapper(private val storePath: Path, private val storePassword: String) {
    private val keyStore = KeyStoreUtilities.loadKeyStore(storePath, storePassword)

    fun certificateAndKeyPair(alias: String): CertificateAndKeyPair? {
        return if (keyStore.containsAlias(alias)) keyStore.getCertificateAndKeyPair(alias, storePassword) else null
    }

    fun save(serviceName: X500Name, privateKeyAlias: String, keyPair: KeyPair) {
        val converter = JcaX509CertificateConverter()
        val clientCA = keyStore.getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, storePassword)
        val cert = converter.getCertificate(X509Utilities.createCertificate(CertificateType.IDENTITY, clientCA.certificate, clientCA.keyPair, serviceName, keyPair.public))
        keyStore.addOrReplaceKey(privateKeyAlias, keyPair.private, storePassword.toCharArray(), arrayOf(cert, *keyStore.getCertificateChain(X509Utilities.CORDA_CLIENT_CA)))
        keyStore.save(storePath, storePassword)
    }
}
