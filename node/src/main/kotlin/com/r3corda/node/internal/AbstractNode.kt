package com.r3corda.node.internal

import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.JdkFutureAdapters
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.RunOnCallerThread
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.runOnNextMessage
import com.r3corda.core.node.CityDatabase
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.PhysicalLocation
import com.r3corda.core.node.services.*
import com.r3corda.core.random63BitValue
import com.r3corda.core.seconds
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.node.api.APIServer
import com.r3corda.node.services.api.AcceptsFileUpload
import com.r3corda.node.services.api.CheckpointStorage
import com.r3corda.node.services.api.MonitoringService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.clientapi.NodeInterestRates
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.identity.InMemoryIdentityService
import com.r3corda.node.services.keys.E2ETestKeyManagementService
import com.r3corda.node.services.network.InMemoryNetworkMapCache
import com.r3corda.node.services.network.InMemoryNetworkMapService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.network.NodeRegistration
import com.r3corda.node.services.persistence.*
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.node.services.transactions.InMemoryUniquenessProvider
import com.r3corda.node.services.transactions.NotaryService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.node.services.transactions.ValidatingNotaryService
import com.r3corda.node.services.wallet.CashBalanceAsMetricsObserver
import com.r3corda.node.services.wallet.NodeWalletService
import com.r3corda.node.utilities.ANSIProgressObserver
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.node.utilities.AffinityExecutor
import org.slf4j.Logger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * A base node implementation that can be customised either for production (with real implementations that do real
 * I/O), or a mock implementation suitable for unit test environments.
 */
// TODO: Where this node is the initial network map service, currently no networkMapService is provided.
// In theory the NodeInfo for the node should be passed in, instead, however currently this is constructed by the
// AbstractNode. It should be possible to generate the NodeInfo outside of AbstractNode, so it can be passed in.
abstract class AbstractNode(val dir: Path, val configuration: NodeConfiguration, val networkMapService: NodeInfo?,
                            val advertisedServices: Set<ServiceType>, val platformClock: Clock) {
    companion object {
        val PRIVATE_KEY_FILE_NAME = "identity-private-key"
        val PUBLIC_IDENTITY_FILE_NAME = "identity-public"
    }

    // TODO: Persist this, as well as whether the node is registered.
    /**
     * Sequence number of changes sent to the network map service, when registering/de-registering this node
     */
    var networkMapSeq: Long = 1

    protected abstract val log: Logger

    // We will run as much stuff in this single thread as possible to keep the risk of thread safety bugs low during the
    // low-performance prototyping period.
    protected abstract val serverThread: AffinityExecutor

    // Objects in this list will be scanned by the DataUploadServlet and can be handed new data via HTTP.
    // Don't mutate this after startup.
    protected val _servicesThatAcceptUploads = ArrayList<AcceptsFileUpload>()
    val servicesThatAcceptUploads: List<AcceptsFileUpload> = _servicesThatAcceptUploads

    val services = object : ServiceHubInternal {
        override val networkService: MessagingService get() = net
        override val networkMapCache: NetworkMapCache = InMemoryNetworkMapCache()
        override val storageService: StorageService get() = storage
        override val walletService: WalletService get() = wallet
        override val keyManagementService: KeyManagementService get() = keyManagement
        override val identityService: IdentityService get() = identity
        override val monitoringService: MonitoringService = MonitoringService(MetricRegistry())
        override val clock: Clock = platformClock
    }

    val info: NodeInfo by lazy {
        NodeInfo(net.myAddress, storage.myLegalIdentity, advertisedServices, findMyLocation())
    }

    open fun findMyLocation(): PhysicalLocation? = CityDatabase[configuration.nearestCity]

    lateinit var storage: StorageService
    lateinit var checkpointStorage: CheckpointStorage
    lateinit var smm: StateMachineManager
    lateinit var wallet: WalletService
    lateinit var keyManagement: E2ETestKeyManagementService
    var inNodeNetworkMapService: NetworkMapService? = null
    var inNodeNotaryService: NotaryService? = null
    lateinit var identity: IdentityService
    lateinit var net: MessagingService
    lateinit var api: APIServer
    var isPreviousCheckpointsPresent = false
        private set

    /** Completes once the node has successfully registered with the network map service */
    private val _networkMapRegistrationFuture: SettableFuture<Unit> = SettableFuture.create()
    val networkMapRegistrationFuture: ListenableFuture<Unit>
        get() = _networkMapRegistrationFuture

    /** Set to true once [start] has been successfully called. */
    @Volatile var started = false
        private set

    open fun start(): AbstractNode {
        require(!started) { "Node has already been started" }
        log.info("Node starting up ...")

        val storageServices = initialiseStorageService(dir)
        storage = storageServices.first
        checkpointStorage = storageServices.second
        net = makeMessagingService()
        wallet = NodeWalletService(services)
        keyManagement = E2ETestKeyManagementService()
        makeInterestRatesOracleService()
        identity = makeIdentityService()
        api = APIServerImpl(this)
        smm = StateMachineManager(services, listOf(storage, net, wallet, keyManagement, identity, platformClock), checkpointStorage, serverThread)

        // This object doesn't need to be referenced from this class because it registers handlers on the network
        // service and so that keeps it from being collected.
        DataVendingService(net, storage)

        buildAdvertisedServices()

        // TODO: this model might change but for now it provides some de-coupling
        // Add SMM observers
        ANSIProgressObserver(smm)
        // Add wallet observers
        CashBalanceAsMetricsObserver(services)

        startMessagingService()
        _networkMapRegistrationFuture.setFuture(registerWithNetworkMap())
        isPreviousCheckpointsPresent = checkpointStorage.checkpoints.any()
        smm.start()
        started = true
        return this
    }

    private fun buildAdvertisedServices() {
        val serviceTypes = info.advertisedServices
        if (NetworkMapService.Type in serviceTypes) makeNetworkMapService()

        val notaryServiceType = serviceTypes.singleOrNull { it.isSubTypeOf(NotaryService.Type) }
        if (notaryServiceType != null) makeNotaryService(notaryServiceType)
    }

    /**
     * Register this node with the network map cache, and load network map from a remote service (and register for
     * updates) if one has been supplied.
     */
    private fun registerWithNetworkMap(): ListenableFuture<Unit> {
        require(networkMapService == null || NetworkMapService.Type in networkMapService.advertisedServices) {
            "Initial network map address must indicate a node that provides a network map service"
        }
        services.networkMapCache.addNode(info)
        if (networkMapService != null && networkMapService != info) {
            // Only register if we are pointed at a network map service and it's not us.
            // TODO: Return a future so the caller knows these operations may not have completed yet, and can monitor if needed
            updateRegistration(networkMapService, AddOrRemove.ADD)
            return services.networkMapCache.addMapService(net, networkMapService, true, null)
        }
        // In the unit test environment, we may run without any network map service sometimes.
        if (inNodeNetworkMapService == null)
            return noNetworkMapConfigured()
        // Register for updates, even if we're the one running the network map.
        return services.networkMapCache.addMapService(net, info, true, null)
    }

    /** This is overriden by the mock node implementation to enable operation without any network map service */
    protected open fun noNetworkMapConfigured(): ListenableFuture<Unit> {
        // TODO: There should be a consistent approach to configuration error exceptions.
        throw IllegalStateException("Configuration error: this node isn't being asked to act as the network map, nor " +
                "has any other map node been configured.")
    }

    private fun updateRegistration(serviceInfo: NodeInfo, type: AddOrRemove): ListenableFuture<NetworkMapService.RegistrationResponse> {
        // Register this node against the network
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val reg = NodeRegistration(info, networkMapSeq++, type, expires)
        val sessionID = random63BitValue()
        val request = NetworkMapService.RegistrationRequest(reg.toWire(storage.myLegalIdentityKey.private), net.myAddress, sessionID)
        val message = net.createMessage(NetworkMapService.REGISTER_PROTOCOL_TOPIC + ".0", request.serialize().bits)
        val future = SettableFuture.create<NetworkMapService.RegistrationResponse>()
        val topic = NetworkMapService.REGISTER_PROTOCOL_TOPIC + "." + sessionID

        net.runOnNextMessage(topic, RunOnCallerThread) { message ->
            future.set(message.data.deserialize())
        }
        net.send(message, serviceInfo.address)

        return future
    }

    open protected fun makeNetworkMapService() {
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val reg = NodeRegistration(info, Long.MAX_VALUE, AddOrRemove.ADD, expires)
        inNodeNetworkMapService = InMemoryNetworkMapService(net, reg, services.networkMapCache)
    }

    open protected fun makeNotaryService(type: ServiceType) {
        val uniquenessProvider = InMemoryUniquenessProvider()
        val timestampChecker = TimestampChecker(platformClock, 30.seconds)

        inNodeNotaryService = when (type) {
            is SimpleNotaryService.Type -> SimpleNotaryService(smm, net, timestampChecker, uniquenessProvider)
            is ValidatingNotaryService.Type -> ValidatingNotaryService(smm, net, timestampChecker, uniquenessProvider)
            else -> null
        }
    }

    lateinit var interestRatesService: NodeInterestRates.Service

    open protected fun makeInterestRatesOracleService() {
        // TODO: Once the service has data, automatically register with the network map service (once built).
        interestRatesService = NodeInterestRates.Service(this)
        _servicesThatAcceptUploads += interestRatesService
    }

    protected open fun makeIdentityService(): IdentityService {
        val service = InMemoryIdentityService()
        if (networkMapService != null)
            service.registerIdentity(networkMapService.identity)
        service.registerIdentity(storage.myLegalIdentity)

        services.networkMapCache.partyNodes.forEach { service.registerIdentity(it.identity) }

        // TODO: Subscribe to updates to the network map cache

        return service
    }

    open fun stop() {
        // TODO: We need a good way of handling "nice to have" shutdown events, especially those that deal with the
        // network, including unsubscribing from updates from remote services. Possibly some sort of parameter to stop()
        // to indicate "Please shut down gracefully" vs "Shut down now".
        // Meanwhile, we let the remote service send us updates until the acknowledgment buffer overflows and it
        // unsubscribes us forcibly, rather than blocking the shutdown process.

        net.stop()
    }

    protected abstract fun makeMessagingService(): MessagingService

    protected abstract fun startMessagingService()

    protected open fun initialiseStorageService(dir: Path): Pair<StorageService, CheckpointStorage> {
        val attachments = makeAttachmentStorage(dir)
        val checkpointStorage = PerFileCheckpointStorage(dir.resolve("checkpoints"))
        val transactionStorage = PerFileTransactionStorage(dir.resolve("transactions"))
        _servicesThatAcceptUploads += attachments
        val (identity, keypair) = obtainKeyPair(dir)
        return Pair(constructStorageService(attachments, transactionStorage, keypair, identity),checkpointStorage)
    }

    protected open fun constructStorageService(attachments: NodeAttachmentService,
                                               transactionStorage: TransactionStorage,
                                               keypair: KeyPair,
                                               identity: Party) =
            StorageServiceImpl(attachments, transactionStorage, keypair, identity)

    private fun obtainKeyPair(dir: Path): Pair<Party, KeyPair> {
        // Load the private identity key, creating it if necessary. The identity key is a long term well known key that
        // is distributed to other peers and we use it (or a key signed by it) when we need to do something
        // "permissioned". The identity file is what gets distributed and contains the node's legal name along with
        // the public key. Obviously in a real system this would need to be a certificate chain of some kind to ensure
        // the legal name is actually validated in some way.
        val privKeyFile = dir.resolve(PRIVATE_KEY_FILE_NAME)
        val pubIdentityFile = dir.resolve(PUBLIC_IDENTITY_FILE_NAME)

        return if (!Files.exists(privKeyFile)) {
            log.info("Identity key not found, generating fresh key!")
            val keypair: KeyPair = generateKeyPair()
            keypair.serialize().writeToFile(privKeyFile)
            val myIdentity = Party(configuration.myLegalName, keypair.public)
            // We include the Party class with the file here to help catch mixups when admins provide files of the
            // wrong type by mistake.
            myIdentity.serialize().writeToFile(pubIdentityFile)
            Pair(myIdentity, keypair)
        } else {
            // Check that the identity in the config file matches the identity file we have stored to disk.
            // This is just a sanity check. It shouldn't fail unless the admin has fiddled with the files and messed
            // things up for us.
            val myIdentity = Files.readAllBytes(pubIdentityFile).deserialize<Party>()
            if (myIdentity.name != configuration.myLegalName)
                throw ConfigurationException("The legal name in the config file doesn't match the stored identity file:" +
                        "${configuration.myLegalName} vs ${myIdentity.name}")
            // Load the private key.
            val keypair = Files.readAllBytes(privKeyFile).deserialize<KeyPair>()
            Pair(myIdentity, keypair)
        }
    }

    protected open fun generateKeyPair() = com.r3corda.core.crypto.generateKeyPair()

    protected fun makeAttachmentStorage(dir: Path): NodeAttachmentService {
        val attachmentsDir = dir.resolve("attachments")
        try {
            Files.createDirectory(attachmentsDir)
        } catch (e: FileAlreadyExistsException) {
        }
        return NodeAttachmentService(attachmentsDir, services.monitoringService.metrics)
    }
}
