package com.r3corda.node.services

import com.codahale.metrics.MetricRegistry
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.services.*
import com.r3corda.core.node.services.testing.MockStorageService
import com.r3corda.core.testing.MOCK_IDENTITY_SERVICE
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.api.MonitoringService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.network.MockNetworkMapCache
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.persistence.DataVendingService
import com.r3corda.node.services.wallet.NodeWalletService
import java.time.Clock

class MockServices(
        customWallet: WalletService? = null,
        val keyManagement: KeyManagementService? = null,
        val net: MessagingService? = null,
        val identity: IdentityService? = MOCK_IDENTITY_SERVICE,
        val storage: StorageService? = MockStorageService(),
        val mapCache: NetworkMapCache? = MockNetworkMapCache(),
        val mapService: NetworkMapService? = null,
        val overrideClock: Clock? = NodeClock()
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
