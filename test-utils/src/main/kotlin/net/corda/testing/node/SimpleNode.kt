package net.corda.testing.node

import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.crypto.commonName
import net.corda.core.crypto.generateKeyPair
import net.corda.core.messaging.RPCOps
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.RPCUserServiceImpl
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.freeLocalHostAndPort
import java.security.KeyPair
import java.security.cert.X509Certificate
import kotlin.concurrent.thread

/**
 * This is a bare-bones node which can only send and receive messages. It doesn't register with a network map service or
 * any other such task that would make it functional in a network and thus left to the user to do so manually.
 */
class SimpleNode(val config: NodeConfiguration, val address: NetworkHostAndPort = freeLocalHostAndPort(),
                 rpcAddress: NetworkHostAndPort = freeLocalHostAndPort(),
                 trustRoot: X509Certificate) : AutoCloseable {

    val database: CordaPersistence = configureDatabase(config.dataSourceProperties)
    val userService = RPCUserServiceImpl(config.rpcUsers)
    val monitoringService = MonitoringService(MetricRegistry())
    val identity: KeyPair = generateKeyPair()
    val identityService: IdentityService = InMemoryIdentityService(trustRoot = trustRoot)
    val keyService: KeyManagementService = E2ETestKeyManagementService(identityService, setOf(identity))
    val executor = ServiceAffinityExecutor(config.myLegalName.commonName, 1)
    // TODO: We should have a dummy service hub rather than change behaviour in tests
    val broker = ArtemisMessagingServer(config, address.port, rpcAddress.port, InMemoryNetworkMapCache(serviceHub = null), userService)
    val networkMapRegistrationFuture: SettableFuture<Unit> = SettableFuture.create<Unit>()
    val network = database.transaction {
        NodeMessagingClient(
                config,
                MOCK_VERSION_INFO,
                address,
                identity.public,
                executor,
                database,
                networkMapRegistrationFuture,
                monitoringService)
    }

    fun start() {
        broker.start()
        network.start(
                object : RPCOps {
                    override val protocolVersion = 0
                },
                userService)
        thread(name = config.myLegalName.commonName) {
            network.run(broker.serverControl)
        }
    }

    override fun close() {
        network.stop()
        broker.stop()
        database.close()
        executor.shutdownNow()
    }
}
