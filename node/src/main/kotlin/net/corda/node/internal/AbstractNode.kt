package net.corda.node.internal

import com.codahale.metrics.MetricRegistry
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.X509Utilities
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.FlowStateMachine
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.*
import net.corda.core.node.services.*
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.sendRequest
import net.corda.node.api.APIServer
import net.corda.node.services.api.*
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.events.ScheduledActivityObserver
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.network.NetworkMapService.Companion.REGISTER_FLOW_TOPIC
import net.corda.node.services.network.NetworkMapService.RegistrationResponse
import net.corda.node.services.network.NodeRegistration
import net.corda.node.services.network.PersistentNetworkMapService
import net.corda.node.services.persistence.*
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.transactions.*
import net.corda.node.services.vault.CashBalanceAsMetricsObserver
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.AddOrRemove.ADD
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import org.apache.activemq.artemis.utils.ReusableLatch
import org.jetbrains.exposed.sql.Database
import org.slf4j.Logger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.security.KeyPair
import java.time.Clock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
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
    companion object {
        val PRIVATE_KEY_FILE_NAME = "identity-private-key"
        val PUBLIC_IDENTITY_FILE_NAME = "identity-public"

        val defaultFlowWhiteList: Map<Class<out FlowLogic<*>>, Set<Class<*>>> = mapOf(
                CashFlow::class.java to setOf(
                        CashCommand.IssueCash::class.java,
                        CashCommand.PayCash::class.java,
                        CashCommand.ExitCash::class.java
                ),
                FinalityFlow::class.java to emptySet()
        )
    }

    // TODO: Persist this, as well as whether the node is registered.
    /**
     * Sequence number of changes sent to the network map service, when registering/de-registering this node.
     */
    var networkMapSeq: Long = 1

    protected abstract val log: Logger
    protected abstract val networkMapAddress: SingleMessageRecipient?

    // We will run as much stuff in this single thread as possible to keep the risk of thread safety bugs low during the
    // low-performance prototyping period.
    protected abstract val serverThread: AffinityExecutor

    // Objects in this list will be scanned by the DataUploadServlet and can be handed new data via HTTP.
    // Don't mutate this after startup.
    protected val _servicesThatAcceptUploads = ArrayList<AcceptsFileUpload>()
    val servicesThatAcceptUploads: List<AcceptsFileUpload> = _servicesThatAcceptUploads

    private val flowFactories = ConcurrentHashMap<Class<*>, (Party) -> FlowLogic<*>>()
    protected val partyKeys = mutableSetOf<KeyPair>()

    val services = object : ServiceHubInternal() {
        override val networkService: MessagingServiceInternal get() = net
        override val networkMapCache: NetworkMapCache get() = netMapCache
        override val storageService: TxWritableStorageService get() = storage
        override val vaultService: VaultService get() = vault
        override val keyManagementService: KeyManagementService get() = keyManagement
        override val identityService: IdentityService get() = identity
        override val schedulerService: SchedulerService get() = scheduler
        override val clock: Clock = platformClock
        override val myInfo: NodeInfo get() = info
        override val schemaService: SchemaService get() = schemas

        // Internal only
        override val monitoringService: MonitoringService = MonitoringService(MetricRegistry())
        override val flowLogicRefFactory: FlowLogicRefFactory get() = flowLogicFactory

        override fun <T> startFlow(logic: FlowLogic<T>): FlowStateMachine<T> {
            return serverThread.fetchFrom { smm.add(logic) }
        }

        override fun registerFlowInitiator(markerClass: KClass<*>, flowFactory: (Party) -> FlowLogic<*>) {
            require(markerClass !in flowFactories) { "${markerClass.java.name} has already been used to register a flow" }
            log.info("Registering flow ${markerClass.java.name}")
            flowFactories[markerClass.java] = flowFactory
        }

        override fun getFlowFactory(markerClass: Class<*>): ((Party) -> FlowLogic<*>)? {
            return flowFactories[markerClass]
        }

        override fun recordTransactions(txs: Iterable<SignedTransaction>) {
            databaseTransaction(database) {
                recordTransactionsInternal(storage, txs)
            }
        }
    }

    open fun findMyLocation(): PhysicalLocation? = CityDatabase[configuration.nearestCity]

    lateinit var info: NodeInfo
    lateinit var storage: TxWritableStorageService
    lateinit var checkpointStorage: CheckpointStorage
    lateinit var smm: StateMachineManager
    lateinit var vault: VaultService
    lateinit var keyManagement: KeyManagementService
    var inNodeNetworkMapService: NetworkMapService? = null
    var inNodeNotaryService: NotaryService? = null
    var uniquenessProvider: UniquenessProvider? = null
    lateinit var identity: IdentityService
    lateinit var net: MessagingServiceInternal
    lateinit var netMapCache: NetworkMapCache
    lateinit var api: APIServer
    lateinit var scheduler: NodeSchedulerService
    lateinit var flowLogicFactory: FlowLogicRefFactory
    lateinit var schemas: SchemaService
    val customServices: ArrayList<Any> = ArrayList()
    protected val runOnStop: ArrayList<Runnable> = ArrayList()
    lateinit var database: Database
    protected var dbCloser: Runnable? = null

    /** Locates and returns a service of the given type if loaded, or throws an exception if not found. */
    inline fun <reified T : Any> findService() = customServices.filterIsInstance<T>().single()

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

    open fun start(): AbstractNode {
        require(!started) { "Node has already been started" }

        if (configuration.devMode) {
            log.warn("Corda node is running in dev mode.")
            configuration.configureWithDevSSLCertificate()
        }
        require(hasSSLCertificates()) { "SSL certificates not found." }

        log.info("Node starting up ...")

        // Do all of this in a database transaction so anything that might need a connection has one.
        initialiseDatabasePersistence {
            val storageServices = initialiseStorageService(configuration.baseDirectory)
            storage = storageServices.first
            checkpointStorage = storageServices.second
            netMapCache = InMemoryNetworkMapCache()
            net = makeMessagingService()
            schemas = makeSchemaService()
            vault = makeVaultService()

            info = makeInfo()
            identity = makeIdentityService()
            // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
            // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
            // the identity key. But the infrastructure to make that easy isn't here yet.
            keyManagement = makeKeyManagementService()
            api = APIServerImpl(this@AbstractNode)
            flowLogicFactory = initialiseFlowLogicFactory()
            scheduler = NodeSchedulerService(database, services, flowLogicFactory, unfinishedSchedulesLatch = busyNodeLatch)

            val tokenizableServices = mutableListOf(storage, net, vault, keyManagement, identity, platformClock, scheduler)

            customServices.clear()
            customServices.addAll(buildPluginServices(tokenizableServices))

            // TODO: uniquenessProvider creation should be inside makeNotaryService(), but notary service initialisation
            //       depends on smm, while smm depends on tokenizableServices, which uniquenessProvider is part of
            advertisedServices.singleOrNull { it.type.isNotary() }?.let {
                uniquenessProvider = makeUniquenessProvider(it.type)
                tokenizableServices.add(uniquenessProvider!!)
            }

            smm = StateMachineManager(services,
                    listOf(tokenizableServices),
                    checkpointStorage,
                    serverThread,
                    database,
                    busyNodeLatch)
            if (serverThread is ExecutorService) {
                runOnStop += Runnable {
                    // We wait here, even though any in-flight messages should have been drained away because the
                    // server thread can potentially have other non-messaging tasks scheduled onto it. The timeout value is
                    // arbitrary and might be inappropriate.
                    MoreExecutors.shutdownAndAwaitTermination(serverThread as ExecutorService, 50, TimeUnit.SECONDS)
                }
            }

            buildAdvertisedServices()

            // TODO: this model might change but for now it provides some de-coupling
            // Add vault observers
            CashBalanceAsMetricsObserver(services, database)
            ScheduledActivityObserver(services)
            HibernateObserver(services)

            checkpointStorage.forEach {
                isPreviousCheckpointsPresent = true
                false
            }
            startMessagingService(CordaRPCOpsImpl(services, smm, database))
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

    private fun makeInfo(): NodeInfo {
        val services = makeServiceEntries()
        val legalIdentity = obtainLegalIdentity()
        return NodeInfo(net.myAddress, legalIdentity, services, findMyLocation())
    }

    /**
     * A service entry contains the advertised [ServiceInfo] along with the service identity. The identity *name* is
     * taken from the configuration or, if non specified, generated by combining the node's legal name and the service id.
     */
    protected fun makeServiceEntries(): List<ServiceEntry> {
        return advertisedServices.map {
            val serviceId = it.type.id
            val serviceName = it.name ?: "$serviceId|${configuration.myLegalName}"
            val identity = obtainKeyPair(configuration.baseDirectory, serviceId + "-private-key", serviceId + "-public", serviceName).first
            ServiceEntry(it, identity)
        }
    }

    @VisibleForTesting
    protected open fun acceptableLiveFiberCountOnStop(): Int = 0

    private fun hasSSLCertificates(): Boolean {
        val keyStore = try {
            // This will throw exception if key file not found or keystore password is incorrect.
            X509Utilities.loadKeyStore(configuration.keyStorePath, configuration.keyStorePassword)
        } catch (e: Exception) {
            null
        }
        return keyStore?.containsAlias(X509Utilities.CORDA_CLIENT_CA) ?: false
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
            databaseTransaction(database) {
                insideTransaction()
            }
        } else {
            throw DatabaseConfigurationException("There must be a database configured.")
        }
    }

    private fun initialiseFlowLogicFactory(): FlowLogicRefFactory {
        val flowWhitelist = HashMap<String, Set<String>>()

        for ((flowClass, extraArgumentTypes) in defaultFlowWhiteList) {
            val argumentWhitelistClassNames = HashSet(extraArgumentTypes.map { it.name })
            flowClass.constructors.forEach {
                it.parameters.mapTo(argumentWhitelistClassNames) { it.type.name }
            }
            flowWhitelist.merge(flowClass.name, argumentWhitelistClassNames, { x, y -> x + y })
        }

        for (plugin in pluginRegistries) {
            for ((className, classWhitelist) in plugin.requiredFlows) {
                flowWhitelist.merge(className, classWhitelist, { x, y -> x + y })
            }
        }

        return FlowLogicRefFactory(flowWhitelist)
    }

    private fun buildPluginServices(tokenizableServices: MutableList<Any>): List<Any> {
        val pluginServices = pluginRegistries.flatMap { x -> x.servicePlugins }
        val serviceList = mutableListOf<Any>()
        for (serviceConstructor in pluginServices) {
            val service = serviceConstructor.apply(services)
            serviceList.add(service)
            tokenizableServices.add(service)
            if (service is AcceptsFileUpload) {
                _servicesThatAcceptUploads += service
            }
        }
        return serviceList
    }

    /**
     * Run any tasks that are needed to ensure the node is in a correct state before running start().
     */
    open fun setup(): AbstractNode {
        createNodeDir()
        return this
    }

    private fun buildAdvertisedServices() {
        val serviceTypes = info.advertisedServices.map { it.info.type }
        if (NetworkMapService.type in serviceTypes) makeNetworkMapService()

        val notaryServiceType = serviceTypes.singleOrNull { it.isNotary() }
        if (notaryServiceType != null) {
            inNodeNotaryService = makeNotaryService(notaryServiceType)
        }
    }

    private fun registerWithNetworkMapIfConfigured(): ListenableFuture<Unit> {
        require(networkMapAddress != null || NetworkMapService.type in advertisedServices.map { it.type }) {
            "Initial network map address must indicate a node that provides a network map service"
        }
        services.networkMapCache.addNode(info)
        // In the unit test environment, we may run without any network map service sometimes.
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
        val address = networkMapAddress ?: info.address
        // Register for updates, even if we're the one running the network map.
        return sendNetworkMapRegistration(address).flatMap { response ->
            check(response.success) { "The network map service rejected our registration request" }
            // This Future will complete on the same executor as sendNetworkMapRegistration, namely the one used by net
            services.networkMapCache.addMapService(net, address, true, null)
        }
    }

    private fun sendNetworkMapRegistration(networkMapAddress: SingleMessageRecipient): ListenableFuture<RegistrationResponse> {
        // Register this node against the network
        val instant = platformClock.instant()
        val expires = instant + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val reg = NodeRegistration(info, instant.toEpochMilli(), ADD, expires)
        val legalIdentityKey = obtainLegalIdentityKey()
        val request = NetworkMapService.RegistrationRequest(reg.toWire(legalIdentityKey.private), net.myAddress)
        return net.sendRequest(REGISTER_FLOW_TOPIC, request, networkMapAddress)
    }

    /** This is overriden by the mock node implementation to enable operation without any network map service */
    protected open fun noNetworkMapConfigured(): ListenableFuture<Unit> {
        // TODO: There should be a consistent approach to configuration error exceptions.
        throw IllegalStateException("Configuration error: this node isn't being asked to act as the network map, nor " +
                "has any other map node been configured.")
    }

    protected open fun makeKeyManagementService(): KeyManagementService = PersistentKeyManagementService(partyKeys)

    open protected fun makeNetworkMapService() {
        inNodeNetworkMapService = PersistentNetworkMapService(services)
    }

    open protected fun makeNotaryService(type: ServiceType): NotaryService {
        val timestampChecker = TimestampChecker(platformClock, 30.seconds)

        return when (type) {
            SimpleNotaryService.type -> SimpleNotaryService(services, timestampChecker, uniquenessProvider!!)
            ValidatingNotaryService.type -> ValidatingNotaryService(services, timestampChecker, uniquenessProvider!!)
            RaftValidatingNotaryService.type -> RaftValidatingNotaryService(services, timestampChecker, uniquenessProvider!! as RaftUniquenessProvider)
            else -> {
                throw IllegalArgumentException("Notary type ${type.id} is not handled by makeNotaryService.")
            }
        }
    }

    protected abstract fun makeUniquenessProvider(type: ServiceType): UniquenessProvider

    protected open fun makeIdentityService(): IdentityService {
        val service = InMemoryIdentityService()
        service.registerIdentity(info.legalIdentity)
        services.networkMapCache.partyNodes.forEach { service.registerIdentity(it.legalIdentity) }
        netMapCache.changed.subscribe { mapChange ->
            // TODO how should we handle network map removal
            if (mapChange is MapChange.Added) {
                service.registerIdentity(mapChange.node.legalIdentity)
            }
        }
        return service
    }

    // TODO: sort out ordering of open & protected modifiers of functions in this class.
    protected open fun makeVaultService(): VaultService = NodeVaultService(services)

    protected open fun makeSchemaService(): SchemaService = NodeSchemaService()

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

    protected abstract fun makeMessagingService(): MessagingServiceInternal

    protected abstract fun startMessagingService(rpcOps: RPCOps)

    protected open fun initialiseStorageService(dir: Path): Pair<TxWritableStorageService, CheckpointStorage> {
        val attachments = makeAttachmentStorage(dir)
        val checkpointStorage = DBCheckpointStorage()
        val transactionStorage = DBTransactionStorage()
        _servicesThatAcceptUploads += attachments
        val stateMachineTransactionMappingStorage = DBTransactionMappingStorage()
        return Pair(
                constructStorageService(attachments, transactionStorage, stateMachineTransactionMappingStorage),
                checkpointStorage
        )
    }

    protected open fun constructStorageService(attachments: NodeAttachmentService,
                                               transactionStorage: TransactionStorage,
                                               stateMachineRecordedTransactionMappingStorage: StateMachineRecordedTransactionMappingStorage) =
            StorageServiceImpl(attachments, transactionStorage, stateMachineRecordedTransactionMappingStorage)

    protected fun obtainLegalIdentity(): Party = obtainKeyPair(configuration.baseDirectory, PRIVATE_KEY_FILE_NAME, PUBLIC_IDENTITY_FILE_NAME).first
    protected fun obtainLegalIdentityKey(): KeyPair = obtainKeyPair(configuration.baseDirectory, PRIVATE_KEY_FILE_NAME, PUBLIC_IDENTITY_FILE_NAME).second

    private fun obtainKeyPair(dir: Path, privateKeyFileName: String, publicKeyFileName: String, serviceName: String? = null): Pair<Party, KeyPair> {
        // Load the private identity key, creating it if necessary. The identity key is a long term well known key that
        // is distributed to other peers and we use it (or a key signed by it) when we need to do something
        // "permissioned". The identity file is what gets distributed and contains the node's legal name along with
        // the public key. Obviously in a real system this would need to be a certificate chain of some kind to ensure
        // the legal name is actually validated in some way.
        val privKeyFile = dir / privateKeyFileName
        val pubIdentityFile = dir / publicKeyFileName
        val identityName = serviceName ?: configuration.myLegalName

        val identityAndKey = if (!privKeyFile.exists()) {
            log.info("Identity key not found, generating fresh key!")
            val keyPair: KeyPair = generateKeyPair()
            keyPair.serialize().writeToFile(privKeyFile)
            val myIdentity = Party(identityName, keyPair.public)
            // We include the Party class with the file here to help catch mixups when admins provide files of the
            // wrong type by mistake.
            myIdentity.serialize().writeToFile(pubIdentityFile)
            Pair(myIdentity, keyPair)
        } else {
            // Check that the identity in the config file matches the identity file we have stored to disk.
            // This is just a sanity check. It shouldn't fail unless the admin has fiddled with the files and messed
            // things up for us.
            val myIdentity = pubIdentityFile.readAll().deserialize<Party>()
            if (myIdentity.name != identityName)
                throw ConfigurationException("The legal name in the config file doesn't match the stored identity file:" +
                        "$identityName vs ${myIdentity.name}")
            // Load the private key.
            val keyPair = privKeyFile.readAll().deserialize<KeyPair>()
            Pair(myIdentity, keyPair)
        }
        partyKeys += identityAndKey.second
        return identityAndKey
    }

    protected open fun generateKeyPair() = cryptoGenerateKeyPair()

    protected fun makeAttachmentStorage(dir: Path): NodeAttachmentService {
        val attachmentsDir = dir / "attachments"
        try {
            attachmentsDir.createDirectory()
        } catch (e: FileAlreadyExistsException) {
        }
        return NodeAttachmentService(attachmentsDir, services.monitoringService.metrics)
    }

    protected fun createNodeDir() {
        configuration.baseDirectory.createDirectories()
    }
}
