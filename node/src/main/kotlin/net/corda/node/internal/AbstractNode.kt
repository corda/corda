package net.corda.node.internal

import com.codahale.metrics.MetricRegistry
import com.google.common.collect.Lists
import com.google.common.collect.MutableClassToInstanceMap
import com.google.common.util.concurrent.MoreExecutors
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.confidential.SwapIdentitiesHandler
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.cert
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.toX509CertHolder
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.*
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.debug
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.services.ContractUpgradeHandler
import net.corda.node.services.FinalityHandler
import net.corda.node.services.NotaryChangeHandler
import net.corda.node.services.api.*
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.events.NodeSchedulerService
import net.corda.node.services.events.ScheduledActivityObserver
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.*
import net.corda.node.services.network.NetworkMapService.RegistrationRequest
import net.corda.node.services.network.NetworkMapService.RegistrationResponse
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.persistence.DBTransactionMappingStorage
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.statemachine.*
import net.corda.node.services.transactions.*
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.services.vault.VaultSoftLockManager
import net.corda.node.utilities.*
import net.corda.node.utilities.AddOrRemove.ADD
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.nodeapi.internal.ServiceType
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.Logger
import rx.Observable
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.security.KeyPair
import java.security.KeyStoreException
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.sql.Connection
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
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
// TODO: Where this node is the initial network map service, currently no networkMapService is provided.
// In theory the NodeInfo for the node should be passed in, instead, however currently this is constructed by the
// AbstractNode. It should be possible to generate the NodeInfo outside of AbstractNode, so it can be passed in.
abstract class AbstractNode(open val configuration: NodeConfiguration,
                            val advertisedServices: Set<ServiceInfo>,
                            val platformClock: Clock,
                            @VisibleForTesting val busyNodeLatch: ReusableLatch = ReusableLatch()) : SingletonSerializeAsToken() {
    private class StartedNodeImpl<out N : AbstractNode>(
            override val internals: N,
            override val services: ServiceHubInternalImpl,
            override val info: NodeInfo,
            override val checkpointStorage: CheckpointStorage,
            override val smm: StateMachineManager,
            override val attachments: NodeAttachmentService,
            override val inNodeNetworkMapService: NetworkMapService,
            override val network: MessagingService,
            override val database: CordaPersistence,
            override val rpcOps: CordaRPCOps) : StartedNode<N>

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

    protected val services: ServiceHubInternal get() = _services
    private lateinit var _services: ServiceHubInternalImpl
    protected lateinit var legalIdentity: PartyAndCertificate
    protected lateinit var info: NodeInfo
    protected var myNotaryIdentity: PartyAndCertificate? = null
    protected lateinit var checkpointStorage: CheckpointStorage
    protected lateinit var smm: StateMachineManager
    protected lateinit var attachments: NodeAttachmentService
    protected lateinit var inNodeNetworkMapService: NetworkMapService
    protected lateinit var network: MessagingService
    protected val runOnStop = ArrayList<() -> Any?>()
    protected lateinit var database: CordaPersistence
    lateinit var cordappProvider: CordappProviderImpl
    protected val cordappLoader by lazy { makeCordappLoader() }

    protected val _nodeReadyFuture = openFuture<Unit>()
    /** Completes once the node has successfully registered with the network map service
     * or has loaded network map data from local database */
    val nodeReadyFuture: CordaFuture<Unit>
        get() = _nodeReadyFuture
    /** A [CordaX500Name] with null common name. */
    protected val myLegalName: CordaX500Name by lazy {
        val cert = loadKeyStore(configuration.nodeKeystore, configuration.keyStorePassword).getX509Certificate(X509Utilities.CORDA_CLIENT_CA)
        CordaX500Name.build(cert.subjectX500Principal).copy(commonName = null)
    }

    open val serializationWhitelists: List<SerializationWhitelist> by lazy {
        cordappProvider.cordapps.flatMap { it.serializationWhitelists }
    }

    /** Set to non-null once [start] has been successfully called. */
    open val started get() = _started
    @Volatile private var _started: StartedNode<AbstractNode>? = null

    /** The implementation of the [CordaRPCOps] interface used by this node. */
    open fun makeRPCOps(): CordaRPCOps {
        return CordaRPCOpsImpl(services, smm, database)
    }

    open fun start(): StartedNode<AbstractNode> {
        require(started == null) { "Node has already been started" }
        if (configuration.devMode) {
            log.warn("Corda node is running in dev mode.")
            configuration.configureWithDevSSLCertificate()
        }
        validateKeystore()

        log.info("Node starting up ...")

        // Do all of this in a database transaction so anything that might need a connection has one.
        val startedImpl = initialiseDatabasePersistence {
            val tokenizableServices = makeServices()

            smm = StateMachineManager(services,
                    checkpointStorage,
                    serverThread,
                    database,
                    busyNodeLatch,
                    cordappLoader.appClassLoader)

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

            val rpcOps = makeRPCOps()
            startMessagingService(rpcOps)
            installCoreFlows()

            installCordaServices()
            registerCordappFlows()
            _services.rpcFlows += cordappProvider.cordapps.flatMap { it.rpcFlows }
            registerCustomSchemas(cordappProvider.cordapps.flatMap { it.customSchemas }.toSet())
            FlowLogicRefFactoryImpl.classloader = cordappLoader.appClassLoader

            runOnStop += network::stop
            StartedNodeImpl(this, _services, info, checkpointStorage, smm, attachments, inNodeNetworkMapService, network, database, rpcOps)
        }
        // If we successfully  loaded network data from database, we set this future to Unit.
        _nodeReadyFuture.captureLater(registerWithNetworkMapIfConfigured())
        return startedImpl.apply {
            database.transaction {
                smm.start()
                // Shut down the SMM so no Fibers are scheduled.
                runOnStop += { smm.stop(acceptableLiveFiberCountOnStop()) }
                services.schedulerService.start()
            }
            _started = this
        }
    }

    private class ServiceInstantiationException(cause: Throwable?) : CordaException("Service Instantiation Error", cause)

    private fun installCordaServices() {
        cordappProvider.cordapps.flatMap { it.services }.forEach {
            try {
                installCordaService(it)
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

    /**
     * Use this method to install your Corda services in your tests. This is automatically done by the node when it
     * starts up for all classes it finds which are annotated with [CordaService].
     */
    fun <T : SerializeAsToken> installCordaService(serviceClass: Class<T>): T {
        serviceClass.requireAnnotation<CordaService>()
        val service = try {
            if (NotaryService::class.java.isAssignableFrom(serviceClass)) {
                check(myNotaryIdentity != null) { "Trying to install a notary service but no notary identity specified" }
                val constructor = serviceClass.getDeclaredConstructor(ServiceHub::class.java, PublicKey::class.java).apply { isAccessible = true }
                constructor.newInstance(services, myNotaryIdentity!!.owningKey)
            } else {
                val constructor = serviceClass.getDeclaredConstructor(ServiceHub::class.java).apply { isAccessible = true }
                constructor.newInstance(services)
            }
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
        installCoreFlow(NotaryFlow.Client::class, service::createServiceFlow)
    }

    private fun registerCordappFlows() {
        cordappProvider.cordapps.flatMap { it.initiatedFlows }
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

    /**
     * Use this method to register your initiated flows in your tests. This is automatically done by the node when it
     * starts up for all [FlowLogic] classes it finds which are annotated with [InitiatedBy].
     * @return An [Observable] of the initiated flows started by counter-parties.
     */
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>): Observable<T> {
        return registerInitiatedFlowInternal(initiatedFlowClass, track = true)
    }

    // TODO remove once not needed
    private fun deprecatedFlowConstructorMessage(flowClass: Class<*>): String {
        return "Installing flow factory for $flowClass accepting a ${Party::class.java.simpleName}, which is deprecated. " +
                "It should accept a ${FlowSession::class.java.simpleName} instead"
    }

    private fun <F : FlowLogic<*>> registerInitiatedFlowInternal(initiatedFlow: Class<F>, track: Boolean): Observable<F> {
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

    /**
     * Installs a flow that's core to the Corda platform. Unlike CorDapp flows which are versioned individually using
     * [InitiatingFlow.version], core flows have the same version as the node's platform version. To cater for backwards
     * compatibility [flowFactory] provides a second parameter which is the platform version of the initiating party.
     * @suppress
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
    private fun makeServices(): MutableList<Any> {
        checkpointStorage = DBCheckpointStorage()
        cordappProvider = CordappProviderImpl(cordappLoader)
        _services = ServiceHubInternalImpl()
        attachments = NodeAttachmentService(services.monitoringService.metrics)
        cordappProvider.start(attachments)
        legalIdentity = obtainIdentity()
        network = makeMessagingService(legalIdentity)
        info = makeInfo(legalIdentity)

        val tokenizableServices = mutableListOf(attachments, network, services.vaultService,
                services.keyManagementService, services.identityService, platformClock, services.schedulerService,
                services.auditService, services.monitoringService, services.networkMapCache, services.schemaService,
                services.transactionVerifierService, services.validatedTransactions, services.contractUpgradeService,
                services, cordappProvider, this)
        makeNetworkServices(tokenizableServices)
        return tokenizableServices
    }

    private fun makeCordappLoader(): CordappLoader {
        val scanPackages = System.getProperty("net.corda.node.cordapp.scan.packages")
        return if (CordappLoader.testPackages.isNotEmpty()) {
            check(configuration.devMode) { "Package scanning can only occur in dev mode" }
            CordappLoader.createDefaultWithTestPackages(configuration.baseDirectory, CordappLoader.testPackages)
        } else if (scanPackages != null) {
            check(configuration.devMode) { "Package scanning can only occur in dev mode" }
            CordappLoader.createDefaultWithTestPackages(configuration.baseDirectory, scanPackages.split(","))
        } else {
            CordappLoader.createDefault(configuration.baseDirectory)
        }
    }

    protected open fun makeTransactionStorage(): WritableTransactionStorage = DBTransactionStorage()

    private fun makeVaultObservers() {
        VaultSoftLockManager(services.vaultService, smm)
        ScheduledActivityObserver(services)
        HibernateObserver(services.vaultService.rawUpdates, services.database.hibernateConfig)
    }

    private fun makeInfo(legalIdentity: PartyAndCertificate): NodeInfo {
        // TODO  We keep only notary identity as additional legalIdentity if we run it on a node . Multiple identities need more design thinking.
        myNotaryIdentity = getNotaryIdentity()
        val allIdentitiesList = mutableListOf(legalIdentity)
        myNotaryIdentity?.let { allIdentitiesList.add(it) }
        val addresses = myAddresses() // TODO There is no support for multiple IP addresses yet.
        return NodeInfo(addresses, allIdentitiesList, platformVersion, platformClock.instant().toEpochMilli())
    }

    /**
     * A service entry contains the advertised [ServiceInfo] along with the service identity. The identity *name* is
     * taken from the configuration or, if non specified, generated by combining the node's legal name and the service id.
     * Used only for notary identities.
     */
    protected open fun getNotaryIdentity(): PartyAndCertificate? {
        return advertisedServices.singleOrNull { it.type.isNotary() }?.let {
            it.name?.let {
                require(it.commonName != null) {"Common name in '$it' must not be null for notary service, use service type id as common name."}
                require(ServiceType.parse(it.commonName!!).isNotary()) {"Common name for notary service in '$it' must be the notary service type id."}
            }
            obtainIdentity(it)
        }
    }

    @VisibleForTesting
    protected open fun acceptableLiveFiberCountOnStop(): Int = 0

    private fun validateKeystore() {
        val containCorrectKeys = try {
            // This will throw IOException if key file not found or KeyStoreException if keystore password is incorrect.
            val sslKeystore = loadKeyStore(configuration.sslKeystore, configuration.keyStorePassword)
            val identitiesKeystore = loadKeyStore(configuration.nodeKeystore, configuration.keyStorePassword)
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
    }

    // Specific class so that MockNode can catch it.
    class DatabaseConfigurationException(msg: String) : CordaException(msg)

    protected open fun <T> initialiseDatabasePersistence(insideTransaction: () -> T): T {
        val props = configuration.dataSourceProperties
        if (props.isNotEmpty()) {
            this.database = configureDatabase(props, configuration.database, { _services.schemaService }, createIdentityService = { _services.identityService })
            // Now log the vendor string as this will also cause a connection to be tested eagerly.
            database.transaction {
                log.info("Connected to ${database.dataSource.connection.metaData.databaseProductName} database.")
            }
            runOnStop += database::close
            return database.transaction {
                insideTransaction()
            }
        } else {
            throw DatabaseConfigurationException("There must be a database configured.")
        }
    }

    private fun makeNetworkServices(tokenizableServices: MutableList<Any>) {
        val serviceTypes = advertisedServices.map { it.type }
        inNodeNetworkMapService = if (NetworkMapService.type in serviceTypes) makeNetworkMapService() else NullNetworkMapService
        val notaryServiceType = serviceTypes.singleOrNull { it.isNotary() }
        if (notaryServiceType != null) {
            val service = makeCoreNotaryService(notaryServiceType)
            if (service != null) {
                service.apply {
                    tokenizableServices.add(this)
                    runOnStop += this::stop
                    start()
                }
                installCoreFlow(NotaryFlow.Client::class, service::createServiceFlow)
            } else {
                log.info("Notary type ${notaryServiceType.id} does not match any built-in notary types. " +
                        "It is expected to be loaded via a CorDapp")
            }
        }
    }

    private fun registerWithNetworkMapIfConfigured(): CordaFuture<Unit> {
        services.networkMapCache.addNode(info)
        // In the unit test environment, we may sometimes run without any network map service
        return if (networkMapAddress == null && inNodeNetworkMapService == NullNetworkMapService) {
            services.networkMapCache.runWithoutMapService()
            noNetworkMapConfigured()  // TODO This method isn't needed as runWithoutMapService sets the Future in the cache
        } else {
            val netMapRegistration = registerWithNetworkMap()
            // We may want to start node immediately with database data and not wait for network map registration (but send it either way).
            // So we are ready to go.
            if (services.networkMapCache.loadDBSuccess) {
                log.info("Node successfully loaded network map data from the database.")
                doneFuture(Unit)
            } else {
                netMapRegistration
            }
        }
    }

    /**
     * Register this node with the network map cache, and load network map from a remote service (and register for
     * updates) if one has been supplied.
     */
    protected open fun registerWithNetworkMap(): CordaFuture<Unit> {
        require(networkMapAddress != null || NetworkMapService.type in advertisedServices.map { it.type }) {
            "Initial network map address must indicate a node that provides a network map service"
        }
        val address: SingleMessageRecipient = networkMapAddress ?:
                network.getAddressOfParty(PartyInfo.SingleNode(services.myInfo.legalIdentitiesAndCerts.first().party, info.addresses)) as SingleMessageRecipient
        // Register for updates, even if we're the one running the network map.
        return sendNetworkMapRegistration(address).flatMap { (error) ->
            check(error == null) { "Unable to register with the network map service: $error" }
            // The future returned addMapService will complete on the same executor as sendNetworkMapRegistration, namely the one used by net
            services.networkMapCache.addMapService(network, address, true, null)
        }
    }

    private fun sendNetworkMapRegistration(networkMapAddress: SingleMessageRecipient): CordaFuture<RegistrationResponse> {
        // Register this node against the network
        val instant = platformClock.instant()
        val expires = instant + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val reg = NodeRegistration(info, info.serial, ADD, expires)
        val request = RegistrationRequest(reg.toWire(services.keyManagementService, info.legalIdentitiesAndCerts.first().owningKey), network.myAddress)
        return network.sendRequest(NetworkMapService.REGISTER_TOPIC, request, networkMapAddress)
    }

    /** Return list of node's addresses. It's overridden in MockNetwork as we don't have real addresses for MockNodes. */
    protected abstract fun myAddresses(): List<NetworkHostAndPort>

    /** This is overriden by the mock node implementation to enable operation without any network map service */
    protected open fun noNetworkMapConfigured(): CordaFuture<Unit> {
        if (services.networkMapCache.loadDBSuccess) {
            return doneFuture(Unit)
        } else {
            // TODO: There should be a consistent approach to configuration error exceptions.
            throw IllegalStateException("Configuration error: this node isn't being asked to act as the network map, nor " +
                    "has any other map node been configured.")
        }
    }

    protected open fun makeKeyManagementService(identityService: IdentityService): KeyManagementService {
        return PersistentKeyManagementService(identityService, partyKeys)
    }

    open protected fun makeNetworkMapService(): NetworkMapService {
        return PersistentNetworkMapService(services, configuration.minimumPlatformVersion)
    }

    open protected fun makeCoreNotaryService(type: ServiceType): NotaryService? {
        check(myNotaryIdentity != null) { "No notary identity initialized when creating a notary service" }
        return when (type) {
            SimpleNotaryService.type -> SimpleNotaryService(services, myNotaryIdentity!!.owningKey)
            ValidatingNotaryService.type -> ValidatingNotaryService(services, myNotaryIdentity!!.owningKey)
            RaftNonValidatingNotaryService.type -> RaftNonValidatingNotaryService(services, myNotaryIdentity!!.owningKey)
            RaftValidatingNotaryService.type -> RaftValidatingNotaryService(services, myNotaryIdentity!!.owningKey)
            BFTNonValidatingNotaryService.type -> BFTNonValidatingNotaryService(services, myNotaryIdentity!!.owningKey)
            else -> null
        }
    }

    protected open fun makeIdentityService(trustRoot: X509Certificate,
                                           clientCa: CertificateAndKeyPair?,
                                           legalIdentity: PartyAndCertificate): IdentityService {
        val caCertificates: Array<X509Certificate> = listOf(legalIdentity.certificate, clientCa?.certificate?.cert)
                .filterNotNull()
                .toTypedArray()
        val service = PersistentIdentityService(info.legalIdentitiesAndCerts, trustRoot = trustRoot, caCertificates = *caCertificates)
        services.networkMapCache.allNodes.forEach { it.legalIdentitiesAndCerts.forEach { service.verifyAndRegisterIdentity(it) } }
        services.networkMapCache.changed.subscribe { mapChange ->
            // TODO how should we handle network map removal
            if (mapChange is MapChange.Added) {
                mapChange.node.legalIdentitiesAndCerts.forEach {
                    service.verifyAndRegisterIdentity(it)
                }
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

    protected abstract fun makeMessagingService(legalIdentity: PartyAndCertificate): MessagingService

    protected abstract fun startMessagingService(rpcOps: RPCOps)

    private fun obtainIdentity(serviceInfo: ServiceInfo? = null): PartyAndCertificate {
        // Load the private identity key, creating it if necessary. The identity key is a long term well known key that
        // is distributed to other peers and we use it (or a key signed by it) when we need to do something
        // "permissioned". The identity file is what gets distributed and contains the node's legal name along with
        // the public key. Obviously in a real system this would need to be a certificate chain of some kind to ensure
        // the legal name is actually validated in some way.
        val keyStore = KeyStoreWrapper(configuration.nodeKeystore, configuration.keyStorePassword)

        val (id, name) = if (serviceInfo == null) {
            // Create node identity if service info = null
            Pair("identity", myLegalName)
        } else {
            val name = serviceInfo.name ?: myLegalName.copy(commonName = serviceInfo.type.id)
            Pair(serviceInfo.type.id, name)
        }

        // TODO: Integrate with Key management service?
        val privateKeyAlias = "$id-private-key"

        if (!keyStore.containsAlias(privateKeyAlias)) {
            // TODO: Remove use of [ServiceIdentityGenerator.generateToDisk].
            log.info("$privateKeyAlias not found in key store ${configuration.nodeKeystore}, generating fresh key!")
            keyStore.signAndSaveNewKeyPair(name, privateKeyAlias, generateKeyPair())
        }

        val (x509Cert, keys) = keyStore.certificateAndKeyPair(privateKeyAlias)

        // TODO: Use configuration to indicate composite key should be used instead of public key for the identity.
        val compositeKeyAlias = "$id-composite-key"
        val certificates = if (keyStore.containsAlias(compositeKeyAlias)) {
            // Use composite key instead if it exists
            val certificate = keyStore.getCertificate(compositeKeyAlias)
            // We have to create the certificate chain for the composite key manually, this is because we don't have a keystore
            // provider that understand compositeKey-privateKey combo. The cert chain is created using the composite key certificate +
            // the tail of the private key certificates, as they are both signed by the same certificate chain.
            Lists.asList(certificate, keyStore.getCertificateChain(privateKeyAlias).drop(1).toTypedArray())
        } else {
            keyStore.getCertificateChain(privateKeyAlias).let {
                check(it[0].toX509CertHolder() == x509Cert) { "Certificates from key store do not line up!" }
                it.asList()
            }
        }

        val nodeCert = certificates[0] as? X509Certificate ?: throw ConfigurationException("Node certificate must be an X.509 certificate")
        val subject = CordaX500Name.build(nodeCert.subjectX500Principal)
        if (subject != name)
            throw ConfigurationException("The name '$name' for $id doesn't match what's in the key store: $subject")

        partyKeys += keys
        return PartyAndCertificate(CertificateFactory.getInstance("X509").generateCertPath(certificates))
    }

    protected open fun generateKeyPair() = cryptoGenerateKeyPair()

    private inner class ServiceHubInternalImpl : ServiceHubInternal, SingletonSerializeAsToken() {

        override val rpcFlows = ArrayList<Class<out FlowLogic<*>>>()
        override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage()
        override val auditService = DummyAuditService()
        override val monitoringService = MonitoringService(MetricRegistry())
        override val validatedTransactions = makeTransactionStorage()
        override val transactionVerifierService by lazy { makeTransactionVerifierService() }
        override val schemaService by lazy { NodeSchemaService() }
        override val networkMapCache by lazy { PersistentNetworkMapCache(this) }
        override val vaultService by lazy { NodeVaultService(this, database.hibernateConfig) }
        override val contractUpgradeService by lazy { ContractUpgradeServiceImpl() }

        // Place the long term identity key in the KMS. Eventually, this is likely going to be separated again because
        // the KMS is meant for derived temporary keys used in transactions, and we're not supposed to sign things with
        // the identity key. But the infrastructure to make that easy isn't here yet.
        override val keyManagementService by lazy { makeKeyManagementService(identityService) }
        override val schedulerService by lazy { NodeSchedulerService(this, unfinishedSchedules = busyNodeLatch, serverThread = serverThread) }
        override val identityService by lazy {
            val trustStore = KeyStoreWrapper(configuration.trustStoreFile, configuration.trustStorePassword)
            val caKeyStore = KeyStoreWrapper(configuration.nodeKeystore, configuration.keyStorePassword)
            makeIdentityService(
                    trustStore.getX509Certificate(X509Utilities.CORDA_ROOT_CA),
                    caKeyStore.certificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA),
                    legalIdentity)
        }
        override val attachments: AttachmentStorage get() = this@AbstractNode.attachments
        override val networkService: MessagingService get() = network
        override val clock: Clock get() = platformClock
        override val myInfo: NodeInfo get() = info
        override val database: CordaPersistence get() = this@AbstractNode.database
        override val configuration: NodeConfiguration get() = this@AbstractNode.configuration
        override val cordappProvider: CordappProvider = this@AbstractNode.cordappProvider

        override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
            require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
            return cordappServices.getInstance(type) ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
        }

        override fun <T> startFlow(logic: FlowLogic<T>, flowInitiator: FlowInitiator, ourIdentity: Party?): FlowStateMachineImpl<T> {
            return serverThread.fetchFrom { smm.add(logic, flowInitiator, ourIdentity) }
        }

        override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
            return flowFactories[initiatingFlowClass]
        }

        override fun recordTransactions(notifyVault: Boolean, txs: Iterable<SignedTransaction>) {
            database.transaction {
                super.recordTransactions(notifyVault, txs)
            }
        }

        override fun jdbcSession(): Connection = database.createSession()
    }

    fun registerCustomSchemas(schemas: Set<MappedSchema>) {
        database.hibernateConfig.schemaService.registerCustomSchemas(schemas)
    }

}
