package core

import com.codahale.metrics.MetricRegistry
import core.crypto.*
import core.messaging.MessagingService
import core.node.services.*
import core.serialization.SerializedBytes
import core.serialization.deserialize
import core.testing.MockNetworkMapCache
import core.testutils.MockIdentityService
import core.testutils.TEST_PROGRAM_MAP
import core.testutils.TEST_TX_TIME
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.*
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe

/**
 * A test/mock timestamping service that doesn't use any signatures or security. It timestamps with
 * the provided clock which defaults to [TEST_TX_TIME], an arbitrary point on the timeline.
 */
class DummyTimestamper(var clock: Clock = Clock.fixed(TEST_TX_TIME, ZoneId.systemDefault()),
                       val tolerance: Duration = 30.seconds) : TimestamperService {
    override val identity = DummyTimestampingAuthority.identity

    override fun timestamp(wtxBytes: SerializedBytes<WireTransaction>): DigitalSignature.LegallyIdentifiable {
        val wtx = wtxBytes.deserialize()
        val timestamp = wtx.commands.mapNotNull { it.data as? TimestampCommand }.single()
        if (timestamp.before!! until clock.instant() > tolerance)
            throw TimestampingError.NotOnTimeException()
        return DummyTimestampingAuthority.key.signWithECDSA(wtxBytes.bits, identity)
    }
}

val DUMMY_TIMESTAMPER = DummyTimestamper()

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

class MockWalletService(val states: List<StateAndRef<OwnableState>>) : WalletService {
    override val linearHeads: Map<SecureHash, StateAndRef<LinearState>>
        get() = TODO("Use NodeWalletService instead")
    override val cashBalances: Map<Currency, Amount>
        get() = TODO("Use NodeWalletService instead")

    override fun notifyAll(txns: Iterable<WireTransaction>): Wallet {
        TODO("Use NodeWalletService instead")
    }

    override val currentWallet = Wallet(states)
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

@ThreadSafe
class MockStorageService : StorageServiceImpl(MockAttachmentStorage(), generateKeyPair()) {
}

class MockServices(
        val wallet: WalletService? = null,
        val keyManagement: KeyManagementService? = null,
        val net: MessagingService? = null,
        val identity: IdentityService? = MockIdentityService,
        val storage: StorageService? = MockStorageService(),
        val networkMap: NetworkMapCache? = MockNetworkMapCache(),
        val overrideClock: Clock? = Clock.systemUTC()
) : ServiceHub {
    override val walletService: WalletService
        get() = wallet ?: throw UnsupportedOperationException()
    override val keyManagementService: KeyManagementService
        get() = keyManagement ?: throw UnsupportedOperationException()
    override val identityService: IdentityService
        get() = identity ?: throw UnsupportedOperationException()
    override val networkService: MessagingService
        get() = net ?: throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCache
        get() = networkMap ?: throw UnsupportedOperationException()
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
