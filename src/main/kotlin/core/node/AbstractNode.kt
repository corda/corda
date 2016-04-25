package core.node

import api.APIServer
import api.APIServerImpl
import com.codahale.metrics.MetricRegistry
import core.Party
import core.crypto.generateKeyPair
import core.messaging.MessagingService
import core.messaging.StateMachineManager
import core.node.services.*
import core.serialization.deserialize
import core.serialization.serialize
import core.testing.MockNetworkMapCache
import core.utilities.AffinityExecutor
import org.slf4j.Logger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.time.Clock
import java.util.*

/**
 * A base node implementation that can be customised either for production (with real implementations that do real
 * I/O), or a mock implementation suitable for unit test environments.
 */
abstract class AbstractNode(val dir: Path, val configuration: NodeConfiguration, val timestamperAddress: NodeInfo?, val platformClock: Clock) {
    companion object {
        val PRIVATE_KEY_FILE_NAME = "identity-private-key"
        val PUBLIC_IDENTITY_FILE_NAME = "identity-public"
    }

    protected abstract val log: Logger

    // We will run as much stuff in this single thread as possible to keep the risk of thread safety bugs low during the
    // low-performance prototyping period.
    protected open val serverThread: AffinityExecutor = AffinityExecutor.ServiceAffinityExecutor("Node thread", 1)

    // Objects in this list will be scanned by the DataUploadServlet and can be handed new data via HTTP.
    // Don't mutate this after startup.
    protected val _servicesThatAcceptUploads = ArrayList<AcceptsFileUpload>()
    val servicesThatAcceptUploads: List<AcceptsFileUpload> = _servicesThatAcceptUploads

    val services = object : ServiceHub {
        override val networkService: MessagingService get() = net
        override val networkMapCache: NetworkMapCache = MockNetworkMapCache()
        override val storageService: StorageService get() = storage
        override val walletService: WalletService get() = wallet
        override val keyManagementService: KeyManagementService get() = keyManagement
        override val identityService: IdentityService get() = identity
        override val monitoringService: MonitoringService = MonitoringService(MetricRegistry())
        override val clock: Clock get() = platformClock
    }

    val info: NodeInfo by lazy {
        NodeInfo(net.myAddress, storage.myLegalIdentity, emptySet(), findMyLocation())
    }

    protected open fun findMyLocation(): PhysicalLocation? = CityDatabase[configuration.nearestCity]

    lateinit var storage: StorageService
    lateinit var smm: StateMachineManager
    lateinit var wallet: WalletService
    lateinit var keyManagement: E2ETestKeyManagementService
    var inNodeTimestampingService: NodeTimestamperService? = null
    lateinit var identity: IdentityService
    lateinit var net: MessagingService
    lateinit var api: APIServer

    open fun start(): AbstractNode {
        log.info("Node starting up ...")

        storage = initialiseStorageService(dir)
        net = makeMessagingService()
        smm = StateMachineManager(services, serverThread)
        wallet = NodeWalletService(services)
        keyManagement = E2ETestKeyManagementService()
        makeInterestRatesOracleService()
        api = APIServerImpl(this)
        makeTimestampingService(timestamperAddress)
        identity = makeIdentityService()

        // This object doesn't need to be referenced from this class because it registers handlers on the network
        // service and so that keeps it from being collected.
        DataVendingService(net, storage)

        return this
    }

    private fun makeTimestampingService(timestamperAddress: NodeInfo?) {
        // Insert a network map entry for the timestamper: this is all temp scaffolding and will go away. If we are
        // given the details, the timestamping node is somewhere else. Otherwise, we do our own timestamping.
        val tsid = if (timestamperAddress != null) {
            inNodeTimestampingService = null
            require(TimestamperService.Type in timestamperAddress.advertisedServices) {
                "Timestamper address must indicate a node that provides timestamping services, actually " +
                        "has ${timestamperAddress.advertisedServices}"
            }
            timestamperAddress
        } else {
            inNodeTimestampingService = NodeTimestamperService(net, storage.myLegalIdentity, storage.myLegalIdentityKey, platformClock)
            NodeInfo(net.myAddress, storage.myLegalIdentity, setOf(TimestamperService.Type))
        }
        (services.networkMapCache as MockNetworkMapCache).timestampingNodes.add(tsid)
    }

    lateinit var interestRatesService: NodeInterestRates.Service

    open protected fun makeInterestRatesOracleService() {
        // TODO: Once the service has data, automatically register with the network map service (once built).
        interestRatesService = NodeInterestRates.Service(this)
        _servicesThatAcceptUploads += interestRatesService
    }

    protected open fun makeIdentityService(): IdentityService {
        // We don't have any identity infrastructure right now, so we just throw together the only identities we
        // know about: our own, the identity of the remote timestamper node (if any), plus whatever is in the
        // network map.
        //
        val service = InMemoryIdentityService()
        if (timestamperAddress != null)
            service.registerIdentity(timestamperAddress.identity)
        service.registerIdentity(storage.myLegalIdentity)

        services.networkMapCache.partyNodes.forEach { service.registerIdentity(it.identity) }

        return service
    }

    open fun stop() {
        net.stop()
        serverThread.shutdownNow()
    }

    protected abstract fun makeMessagingService(): MessagingService

    protected open fun initialiseStorageService(dir: Path): StorageService {
        val attachments = makeAttachmentStorage(dir)
        _servicesThatAcceptUploads += attachments
        val (identity, keypair) = obtainKeyPair(dir)
        return constructStorageService(attachments, keypair, identity)
    }

    protected open fun constructStorageService(attachments: NodeAttachmentService, keypair: KeyPair, identity: Party) =
            StorageServiceImpl(attachments, keypair, identity)

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

    private fun makeAttachmentStorage(dir: Path): NodeAttachmentService {
        val attachmentsDir = dir.resolve("attachments")
        try {
            Files.createDirectory(attachmentsDir)
        } catch (e: FileAlreadyExistsException) {
        }
        return NodeAttachmentService(attachmentsDir, services.monitoringService.metrics)
    }
}

