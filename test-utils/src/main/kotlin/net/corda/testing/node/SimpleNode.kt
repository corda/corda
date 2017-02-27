package net.corda.testing.node

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.crypto.composite
import net.corda.core.crypto.generateKeyPair
import net.corda.core.messaging.RPCOps
import net.corda.node.services.RPCUserServiceImpl
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.freeLocalHostAndPort
import org.jetbrains.exposed.sql.Database
import java.io.Closeable
import java.security.KeyPair
import kotlin.concurrent.thread

/**
 * This is a bare-bones node which can only send and receive messages. It doesn't register with a network map service or
 * any other such task that would make it functionable in a network and thus left to the user to do so manually.
 */
class SimpleNode(val config: NodeConfiguration, val address: HostAndPort = freeLocalHostAndPort(), rpcAddress: HostAndPort = freeLocalHostAndPort()) : AutoCloseable {

    private val databaseWithCloseable: Pair<Closeable, Database> = configureDatabase(config.dataSourceProperties)
    val database: Database get() = databaseWithCloseable.second
    val userService = RPCUserServiceImpl(config)
    val identity: KeyPair = generateKeyPair()
    val executor = ServiceAffinityExecutor(config.myLegalName, 1)
    val broker = ArtemisMessagingServer(config, address, rpcAddress, InMemoryNetworkMapCache(), userService)
    val networkMapRegistrationFuture: SettableFuture<Unit> = SettableFuture.create<Unit>()
    val net = databaseTransaction(database) {
        NodeMessagingClient(
                config,
                address,
                identity.public.composite,
                executor,
                database,
                networkMapRegistrationFuture)
    }

    fun start() {
        broker.start()
        net.start(
                object : RPCOps { override val protocolVersion = 0 },
                userService)
        thread(name = config.myLegalName) {
            net.run()
        }
    }

    override fun close() {
        net.stop()
        broker.stop()
        databaseWithCloseable.first.close()
        executor.shutdownNow()
    }
}