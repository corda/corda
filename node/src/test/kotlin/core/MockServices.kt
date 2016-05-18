package core

import com.codahale.metrics.MetricRegistry
import core.contracts.Attachment
import core.crypto.SecureHash
import core.crypto.generateKeyPair
import core.crypto.sha256
import core.messaging.MessagingService
import core.node.ServiceHub
import core.node.storage.Checkpoint
import core.node.storage.CheckpointStorage
import core.node.subsystems.*
import core.node.services.AttachmentStorage
import core.node.services.IdentityService
import core.node.services.NetworkMapService
import core.testing.MockNetworkMapCache
import core.testutils.MockIdentityService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Clock
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe

class MockKeyManagementService(vararg initialKeys: KeyPair) : KeyManagementService {
    override val keys: MutableMap<PublicKey, PrivateKey>

    init {
        keys = initialKeys.map { it.public to it.private }.toMap(HashMap())
    }

    val nextKeys = LinkedList<KeyPair>()

    override fun freshKey(): KeyPair {
        val k = nextKeys.poll() ?: generateKeyPair()
        keys[k.public] = k.private
        return k
    }
}

class MockAttachmentStorage : AttachmentStorage {
    val files = HashMap<SecureHash, ByteArray>()

    override fun openAttachment(id: SecureHash): Attachment? {
        val f = files[id] ?: return null
        return object : Attachment {
            override fun open(): InputStream = ByteArrayInputStream(f)
            override val id: SecureHash = id
        }
    }

    override fun importAttachment(jar: InputStream): SecureHash {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = run {
            val s = ByteArrayOutputStream()
            jar.copyTo(s)
            s.close()
            s.toByteArray()
        }
        val sha256 = bytes.sha256()
        if (files.containsKey(sha256))
            throw FileAlreadyExistsException(File("!! MOCK FILE NAME"))
        files[sha256] = bytes
        return sha256
    }
}


class MockCheckpointStorage : CheckpointStorage {

    private val _checkpoints = ConcurrentLinkedQueue<Checkpoint>()
    override val checkpoints: Iterable<Checkpoint>
        get() = _checkpoints.toList()

    override fun addCheckpoint(checkpoint: Checkpoint) {
        _checkpoints.add(checkpoint)
    }

    override fun removeCheckpoint(checkpoint: Checkpoint) {
        require(_checkpoints.remove(checkpoint))
    }
}


@ThreadSafe
class MockStorageService : StorageServiceImpl(MockAttachmentStorage(), MockCheckpointStorage(), generateKeyPair())

class MockServices(
        customWallet: WalletService? = null,
        val keyManagement: KeyManagementService? = null,
        val net: MessagingService? = null,
        val identity: IdentityService? = MockIdentityService,
        val storage: StorageService? = MockStorageService(),
        val mapCache: NetworkMapCache? = MockNetworkMapCache(),
        val mapService: NetworkMapService? = null,
        val overrideClock: Clock? = Clock.systemUTC()
) : ServiceHub {
    override val walletService: WalletService = customWallet ?: NodeWalletService(this)

    override val keyManagementService: KeyManagementService
        get() = keyManagement ?: throw UnsupportedOperationException()
    override val identityService: IdentityService
        get() = identity ?: throw UnsupportedOperationException()
    override val networkService: MessagingService
        get() = net ?: throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCache
        get() = mapCache ?: throw UnsupportedOperationException()
    override val storageService: StorageService
        get() = storage ?: throw UnsupportedOperationException()
    override val clock: Clock
        get() = overrideClock ?: throw UnsupportedOperationException()

    override val monitoringService: MonitoringService = MonitoringService(MetricRegistry())

    init {
        if (net != null && storage != null) {
            // Creating this class is sufficient, we don't have to store it anywhere, because it registers a listener
            // on the networking service, so that will keep it from being collected.
            DataVendingService(net, storage)
        }
    }
}
