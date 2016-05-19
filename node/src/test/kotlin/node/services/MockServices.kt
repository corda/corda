package node.services

import com.codahale.metrics.MetricRegistry
import core.messaging.MessagingService
import core.node.services.*
import core.node.services.testing.MockStorageService
import core.testing.MOCK_IDENTITY_SERVICE
import node.services.api.Checkpoint
import node.services.api.CheckpointStorage
import node.services.api.MonitoringService
import node.services.api.ServiceHubInternal
import node.services.network.MockNetworkMapCache
import node.services.network.NetworkMapService
import node.services.persistence.DataVendingService
import node.services.wallet.NodeWalletService
import java.time.Clock
import java.util.concurrent.ConcurrentLinkedQueue

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


class MockServices(
        customWallet: WalletService? = null,
        val keyManagement: KeyManagementService? = null,
        val net: MessagingService? = null,
        val identity: IdentityService? = MOCK_IDENTITY_SERVICE,
        val storage: StorageService? = MockStorageService(),
        val mapCache: NetworkMapCache? = MockNetworkMapCache(),
        val mapService: NetworkMapService? = null,
        val overrideClock: Clock? = Clock.systemUTC()
) : ServiceHubInternal {
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
