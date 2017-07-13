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
import net.corda.core.crypto.composite.CompositeKey
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
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.debug
import net.corda.flows.*
import net.corda.node.services.*
import net.corda.node.services.api.*
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.events.ScheduledActivityObserver
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.network.NetworkMapService.RegistrationRequest
import net.corda.node.services.network.NetworkMapService.RegistrationResponse
import net.corda.node.services.network.NodeRegistration
import net.corda.node.services.network.PersistentNetworkMapService
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.persistence.DBTransactionMappingStorage
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.statemachine.flowVersionAndInitiatingClass
import net.corda.node.services.transactions.*
import net.corda.node.services.vault.CashBalanceAsMetricsObserver
import net.corda.node.services.vault.HibernateVaultQueryImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.services.vault.VaultSoftLockManager
import net.corda.node.utilities.AddOrRemove.ADD
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.transaction
import org.apache.activemq.artemis.utils.ReusableLatch
import org.bouncycastle.asn1.x500.X500Name
import org.jetbrains.exposed.sql.Database
import org.slf4j.Logger
import rx.Observable
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier.*
import java.math.BigInteger
import java.net.JarURLConnection
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.*
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

    val services: ServiceHubInternal get() = _services

    private lateinit var _services: ServiceHubInternalImpl
    lateinit var info: NodeInfo
    lateinit var checkpointStorage: CheckpointStorage
    lateinit var smm: StateMachineManager
    lateinit var attachments: NodeAttachmentService
    var inNodeNetworkMapService: NetworkMapService? = null
    lateinit var network: MessagingService
    protected val runOnStop = ArrayList<() -> Any?>()
    lateinit var database: Database
    protected var dbCloser: (() -> Any?)? = null

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

    open fun findMyLocation(): WorldMapLocation? {
        return configuration.myLegalName.locationOrNull?.let { CityDatabase[it] }
    }

    open fun start(): AbstractNode {
        require(!started) { "Node has already been started" }

        if (configuration.devMode) {
            log.warn("Corda node is running in dev mode.")
            configuration.configureWithDevSSLCertificate()
        }
        validateKeystore()

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
                runOnStop += {
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
                findRPCFlows(scanResult)
            }

            // TODO Remove this once the cash stuff is in its own CorDapp
            registerInitiatedFlow(IssuerFlow.Issuer::class.java)

            initUploaders()

            runOnStop += network::stop
            _networkMapRegistrationFuture.setFuture(registerWithNetworkMapIfConfigured())
            smm.start()
            // Shut down the SMM so no Fibers are scheduled.
            runOnStop += { smm.stop(acceptableLiveFiberCountOnStop()) }
            _services.schedulerService.start()
        }
        started = true
        return this
    }

    private class ServiceInstantiationException(cause: Throwable?) : Exception(cause)

    private fun installCordaServices(scanResult: ScanResult) {
        fun getServiceType(clazz: Class<*>): ServiceType? {
            return try {
                clazz.getField("type").get(null) as ServiceType
            } catch (e: NoSuchFieldException) {
                log.warn("${clazz.name} does not have a type field, optimistically proceeding with install.")
                null
            }
        }

        scanResult.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
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
                    } catch (e: ServiceInstantiationException) {
                        log.error("Corda service ${it.name} failed to instantiate", e.cause)
                    } catch (e: Exception) {
                        log.error("Unable to install Corda service ${it.name}", e)
                    }
                }
    }

    /**
     * Use this method to install your Corda services in your tests. This is automatically done by the node when it
     * starts up for all classes it finds which are annotated with [CordaService].
     */
    fun <T : SerializeAsToken> installCordaService(serviceClass: Class<T>): T {
        serviceClass.requireAnnotation<CordaService>()
        val constructor = serviceClass.getDeclaredConstructor(PluginServiceHub::class.java).apply { isAccessible = true }
        val service = try {
            constructor.newInstance(services)
        } catch (e: InvocationTargetException) {
            throw ServiceInstantiationException(e.cause)
        }
        cordappServices.putInstance(serviceClass, service)
        smm.tokenizableServices += service

        if (service is NotaryService) handleCustomNotaryService(service)

        log.info("Installed ${serviceClass.name} Corda service")
        return service
    }

    private fun handleCustomNotaryService(service: NotaryService) {
        runOnStop += service::stop
        service.start()
        installCoreFlow(NotaryFlow.Client::class, { party: Party, version: Int -> service.createServiceFlow(party, version) })
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

    private fun findRPCFlows(scanResult: ScanResult) {
        fun Class<out FlowLogic<*>>.isUserInvokable(): Boolean {
            return isPublic(modifiers) && !isLocalClass && !isAnonymousClass && (!isMemberClass || isStatic(modifiers))
        }

        _services.rpcFlows += scanResult
                .getClassesWithAnnotation(FlowLogic::class, StartableByRPC::class)
                .filter { it.isUserInvokable() } +
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
        installCoreFlow(TransactionKeyFlow::class) { otherParty, _ -> TransactionKeyHandler(otherParty) }
    }

    /**
     * Builds node internal, advertised, and plugin services.
     * Returns a list of tokenizable services to be added to the serialisation context.
     */
    private fun makeServices(): MutableList<Any> {
        checkpointStorage = DBCheckpointStorage()
        _services = ServiceHubInternalImpl()
        attachments = createAttachmentStorage()
        network = makeMessagingService()
        info = makeInfo()

        val tokenizableServices = mutableListOf(attachments, network, services.vaultService, services.vaultQueryService,
                services.keyManagementService, services.identityService, platformClock, services.schedulerService)
        makeAdvertisedServices(tokenizableServices)
        return tokenizableServices
    }

    protected open fun makeTransactionStorage(): WritableTransactionStorage = DBTransactionStorage()

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
        _services.uploaders += attachments
        cordappServices.values.filterIsInstanceTo(_services.uploaders, AcceptsFileUpload::class.java)
    }

    private fun makeVaultObservers() {
        VaultSoftLockManager(services.vaultService, smm)
        CashBalanceAsMetricsObserver(services, database)
        ScheduledActivityObserver(services)
        HibernateObserver(services.vaultService.rawUpdates, HibernateConfiguration(services.schemaService))
    }

    private fun makeInfo(): NodeInfo {
        val advertisedServiceEntries = makeServiceEntries()
        val legalIdentity = obtainLegalIdentity()
        val allIdentitiesSet = advertisedServiceEntries.map { it.identity }.toSet() + legalIdentity
        val addresses = myAddresses() // TODO There is no support for multiple IP addresses yet.
        return NodeInfo(addresses, legalIdentity, allIdentitiesSet, platformVersion, advertisedServiceEntries, findMyLocation())
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

    private fun validateKeystore() {
        val containCorrectKeys = try {
            // This will throw IOException if key file not found or KeyStoreException if keystore password is incorrect.
            val sslKeystore = KeyStoreUtilities.loadKeyStore(configuration.sslKeystore, configuration.keyStorePassword)
            val identitiesKeystore = KeyStoreUtilities.loadKeyStore(configuration.nodeKeystore, configuration.keyStorePassword)
            sslKeystore.containsAlias(X509Utilities.CORDA_CLIENT_TLS) && identitiesKeystore.containsAlias(X509Utilities.CORDA_CLIENT_CA)
        } catch (e: KeyStoreException) {
            log.warn("Certificate key store found but key store password does not match configuration.")
            false
        } catch (e: IOException) {
            false
        }
        require(containCorrectKeys) {
            "Identity certificate not found. " +
                    "Please either copy your existing identity key and certificate from another node, " +
                    "or if you don't have one yet, fill out the config file and run corda.jar --initial-registration. " +
                    "Read more at: https://docs.corda.net/permissioning.html"
        }
        val identitiesKeystore = KeyStoreUtilities.loadKeyStore(configuration.sslKeystore, configuration.keyStorePassword)
        val tlsIdentity = identitiesKeystore.getX509Certificate(X509Utilities.CORDA_CLIENT_TLS).subject

        require(tlsIdentity == configuration.myLegalName) {
            "Expected '${configuration.myLegalName}' but got '$tlsIdentity' from the keystore."
        }
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
            toClose::close.let {
                dbCloser = it
                runOnStop += it
            }
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
        configuration.baseDirectory.createDirectories()
        return this
    }

    private fun makeAdvertisedServices(tokenizableServices: MutableList<Any>) {
        val serviceTypes = info.advertisedServices.map { it.info.type }
        if (NetworkMapService.type in serviceTypes) makeNetworkMapService()

        val notaryServiceType = serviceTypes.singleOrNull { it.isNotary() }
        if (notaryServiceType != null) {
            makeCoreNotaryService(notaryServiceType, tokenizableServices)
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
        val address: SingleMessageRecipient = networkMapAddress ?:
                network.getAddressOfParty(PartyInfo.Node(info)) as SingleMessageRecipient
        // Register for updates, even if we're the one running the network map.
        return sendNetworkMapRegistration(address).flatMap { (error) ->
            check(error == null) { "Unable to register with the network map service: $error" }
            // The future returned addMapService will complete on the same executor as sendNetworkMapRegistration, namely the one used by net
            services.networkMapCache.addMapService(network, address, true, null)
        }
    }

    private fun sendNetworkMapRegistration(networkMapAddress: SingleMessageRecipient): ListenableFuture<RegistrationResponse> {
        // Register this node against the network
        val instant = platformClock.instant()
        val expires = instant + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val reg = NodeRegistration(info, instant.toEpochMilli(), ADD, expires)
        val legalIdentityKey = obtainLegalIdentityKey()
        val request = RegistrationRequest(reg.toWire(services.keyManagementService, legalIdentityKey.public), network.myAddress)
        return network.sendRequest(NetworkMapService.REGISTER_TOPIC, request, networkMapAddress)
    }

    /** Return list of node's addresses. It's overridden in MockNetwork as we don't have real addresses for MockNodes. */
    protected abstract fun myAddresses(): List<NetworkHostAndPort>

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

    open protected fun makeCoreNotaryService(type: ServiceType, tokenizableServices: MutableList<Any>) {
        val service: NotaryService = when (type) {
            SimpleNotaryService.type -> SimpleNotaryService(services)
            ValidatingNotaryService.type -> ValidatingNotaryService(services)
            RaftNonValidatingNotaryService.type -> RaftNonValidatingNotaryService(services)
            RaftValidatingNotaryService.type -> RaftValidatingNotaryService(services)
            BFTNonValidatingNotaryService.type -> BFTNonValidatingNotaryService(services)
            else -> {
                log.info("Notary type ${type.id} does not match any built-in notary types. " +
                        "It is expected to be loaded via a CorDapp")
                return
            }
        }
        service.apply {
            tokenizableServices.add(this)
            runOnStop += this::stop
            start()
        }
        installCoreFlow(NotaryFlow.Client::class, { party: Party, version: Int -> service.createServiceFlow(party, version) })
    }

    protected open fun makeIdentityService(trustRoot: X509Certificate,
                                           clientCa: CertificateAndKeyPair?,
                                           legalIdentity: PartyAndCertificate): IdentityService {
        val caCertificates: Array<X509Certificate> = listOf(legalIdentity.certificate.cert, clientCa?.certificate?.cert)
                .filterNotNull()
                .toTypedArray()
        val service = InMemoryIdentityService(setOf(info.legalIdentityAndCert), trustRoot = trustRoot, caCertificates = *caCertificates)
        services.networkMapCache.partyNodes.forEach { service.registerIdentity(it.legalIdentityAndCert) }
        services.networkMapCache.changed.subscribe { mapChange ->
            // TODO how should we handle network map removal
            if (mapChange is MapChange.Added) {
                service.registerIdentity(mapChange.node.legalIdentityAndCert)
            }
        }
        return service
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
    }

    protected abstract fun makeMessagingService(): MessagingService

    protected abstract fun startMessagingService(rpcOps: RPCOps)

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
        val certFactory = CertificateFactory.getInstance("X509")
        val keyStore = KeyStoreWrapper(configuration.nodeKeystore, configuration.keyStorePassword)
        val privateKeyAlias = "$serviceId-private-key"
        val privKeyFile = configuration.baseDirectory / privateKeyAlias
        val pubIdentityFile = configuration.baseDirectory / "$serviceId-public"
        val certificateAndKeyPair = keyStore.certificateAndKeyPair(privateKeyAlias)
        val identityCertPathAndKey: Pair<PartyAndCertificate, KeyPair> = if (certificateAndKeyPair != null) {
            val clientCertPath = keyStore.keyStore.getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
            val (cert, keyPair) = certificateAndKeyPair
            // Get keys from keystore.
            val loadedServiceName = cert.subject
            if (loadedServiceName != serviceName) {
                throw ConfigurationException("The legal name in the config file doesn't match the stored identity keystore:" +
                        "$serviceName vs $loadedServiceName")
            }
            val certPath = certFactory.generateCertPath(listOf(cert.cert) + clientCertPath)
            Pair(PartyAndCertificate(loadedServiceName, keyPair.public, cert, certPath), keyPair)
        } else if (privKeyFile.exists()) {
            // Get keys from key file.
            // TODO: this is here to smooth out the key storage transition, remove this in future release.
            // Check that the identity in the config file matches the identity file we have stored to disk.
            // This is just a sanity check. It shouldn't fail unless the admin has fiddled with the files and messed
            // things up for us.
            val myIdentity = pubIdentityFile.readAll().deserialize<Party>()
            if (myIdentity.name != serviceName)
                throw ConfigurationException("The legal name in the config file doesn't match the stored identity file:" +
                        "$serviceName vs ${myIdentity.name}")
            // Load the private key.
            val keyPair = privKeyFile.readAll().deserialize<KeyPair>()
            if (myIdentity.owningKey !is CompositeKey) { // TODO: Support case where owningKey is a composite key.
                keyStore.save(serviceName, privateKeyAlias, keyPair)
            }
            val dummyCaKey = entropyToKeyPair(BigInteger.valueOf(111))
            val dummyCa = CertificateAndKeyPair(
                    X509Utilities.createSelfSignedCACertificate(X500Name("CN=Dummy CA,OU=Corda,O=R3 Ltd,L=London,C=GB"), dummyCaKey),
                    dummyCaKey)
            val partyAndCertificate = getTestPartyAndCertificate(myIdentity, dummyCa)
            // Sanity check the certificate and path
            val validatorParameters = PKIXParameters(setOf(TrustAnchor(dummyCa.certificate.cert, null)))
            val validator = CertPathValidator.getInstance("PKIX")
            validatorParameters.isRevocationEnabled = false
            validator.validate(partyAndCertificate.certPath, validatorParameters) as PKIXCertPathValidatorResult
            Pair(partyAndCertificate, keyPair)
        } else {
            val clientCertPath = keyStore.keyStore.getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
            val clientCA = keyStore.certificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA)!!
            // Create new keys and store in keystore.
            log.info("Identity key not found, generating fresh key!")
            val keyPair: KeyPair = generateKeyPair()
            val cert = X509Utilities.createCertificate(CertificateType.IDENTITY, clientCA.certificate, clientCA.keyPair, serviceName, keyPair.public)
            val certPath = certFactory.generateCertPath(listOf(cert.cert) + clientCertPath)
            keyStore.save(serviceName, privateKeyAlias, keyPair)
            require(certPath.certificates.isNotEmpty()) { "Certificate path cannot be empty" }
            Pair(PartyAndCertificate(serviceName, keyPair.public, cert, certPath), keyPair)
        }
        partyKeys += identityCertPathAndKey.second
        return identityCertPathAndKey
    }

    private fun getTestPartyAndCertificate(party: Party, trustRoot: CertificateAndKeyPair): PartyAndCertificate {
        val certFactory = CertificateFactory.getInstance("X509")
        val certHolder = X509Utilities.createCertificate(CertificateType.IDENTITY, trustRoot.certificate, trustRoot.keyPair, party.name, party.owningKey)
        val certPath = certFactory.generateCertPath(listOf(certHolder.cert, trustRoot.certificate.cert))
        return PartyAndCertificate(party, certHolder, certPath)
    }

    protected open fun generateKeyPair() = cryptoGenerateKeyPair()

    private fun createAttachmentStorage(): NodeAttachmentService {
        val attachmentsDir = (configuration.baseDirectory / "attachments").createDirectories()
        return NodeAttachmentService(attachmentsDir, configuration.dataSourceProperties, services.monitoringService.metrics)
    }

    private inner class ServiceHubInternalImpl : ServiceHubInternal, SingletonSerializeAsToken() {
        override val rpcFlows = ArrayList<Class<out FlowLogic<*>>>()
        override val uploaders = ArrayList<FileUploader>()
        override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage()
        override val auditService = DummyAuditService()
        override val monitoringService = MonitoringService(MetricRegistry())
        override val validatedTransactions = makeTransactionStorage()
        override val transactionVerifierService by lazy { makeTransactionVerifierService() }
        override val networkMapCache by lazy { InMemoryNetworkMapCache(this) }
        override val vaultService by lazy { NodeVaultService(this, configuration.dataSourceProperties) }
        override val vaultQueryService by lazy {
            HibernateVaultQueryImpl(HibernateConfiguration(schemaService), vaultService.updatesPublisher)
        }
        // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
        // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
        // the identity key. But the infrastructure to make that easy isn't here yet.
        override val keyManagementService by lazy { makeKeyManagementService(identityService) }
        override val schedulerService by lazy { NodeSchedulerService(this, unfinishedSchedules = busyNodeLatch) }
        override val identityService by lazy {
            val keyStoreWrapper = KeyStoreWrapper(configuration.trustStoreFile, configuration.trustStorePassword)
            makeIdentityService(
                    keyStoreWrapper.keyStore.getCertificate(X509Utilities.CORDA_ROOT_CA)!! as X509Certificate,
                    keyStoreWrapper.certificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA),
                    info.legalIdentityAndCert)
        }
        override val attachments: AttachmentStorage get() = this@AbstractNode.attachments
        override val networkService: MessagingService get() = network
        override val clock: Clock get() = platformClock
        override val myInfo: NodeInfo get() = info
        override val schemaService by lazy { NodeSchemaService(pluginRegistries.flatMap { it.requiredSchemas }.toSet()) }
        override val database: Database get() = this@AbstractNode.database
        override val configuration: NodeConfiguration get() = this@AbstractNode.configuration

        override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
            require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
            return cordappServices.getInstance(type) ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
        }

        override fun <T> startFlow(logic: FlowLogic<T>, flowInitiator: FlowInitiator): FlowStateMachineImpl<T> {
            return serverThread.fetchFrom { smm.add(logic, flowInitiator) }
        }

        override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
            return flowFactories[initiatingFlowClass]
        }

        override fun recordTransactions(txs: Iterable<SignedTransaction>) {
            database.transaction {
                super.recordTransactions(txs)
            }
        }
    }

}

private class KeyStoreWrapper(val keyStore: KeyStore, val storePath: Path, private val storePassword: String) {
    constructor(storePath: Path, storePassword: String) : this(KeyStoreUtilities.loadKeyStore(storePath, storePassword), storePath, storePassword)

    fun certificateAndKeyPair(alias: String): CertificateAndKeyPair? {
        return if (keyStore.containsAlias(alias)) keyStore.getCertificateAndKeyPair(alias, storePassword) else null
    }

    fun save(serviceName: X500Name, privateKeyAlias: String, keyPair: KeyPair) {
        val clientCA = keyStore.getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, storePassword)
        val cert = X509Utilities.createCertificate(CertificateType.IDENTITY, clientCA.certificate, clientCA.keyPair, serviceName, keyPair.public).cert
        keyStore.addOrReplaceKey(privateKeyAlias, keyPair.private, storePassword.toCharArray(), arrayOf(cert, *keyStore.getCertificateChain(X509Utilities.CORDA_CLIENT_CA)))
        keyStore.save(storePath, storePassword)
    }
}
