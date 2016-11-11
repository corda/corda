package net.corda.node.services

import com.google.common.net.HostAndPort
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.tree
import net.corda.core.messaging.Message
import net.corda.core.messaging.createMessage
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.utilities.LogHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.services.messaging.RPCOps
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.freeLocalHostAndPort
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtemisMessagingTests {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    val hostAndPort = freeLocalHostAndPort()
    val topic = "platform.self"
    val identity = generateKeyPair()

    lateinit var config: NodeConfiguration
    lateinit var dataSource: Closeable
    lateinit var database: Database
    lateinit var userService: RPCUserService


    var messagingClient: NodeMessagingClient? = null
    var messagingServer: ArtemisMessagingServer? = null


    val networkMapCache = InMemoryNetworkMapCache()

    val rpcOps = object : RPCOps {
        override val protocolVersion: Int get() = throw UnsupportedOperationException()
    }

    @Before
    fun setUp() {
        userService = PropertiesFileRPCUserService(temporaryFolder.newFile().toPath())
        // TODO: create a base class that provides a default implementation
        config = object : NodeConfiguration {
            override val basedir: Path = temporaryFolder.newFolder().toPath()
            override val myLegalName: String = "me"
            override val nearestCity: String = "London"
            override val emailAddress: String = ""
            override val devMode: Boolean = true
            override val exportJMXto: String = ""
            override val keyStorePassword: String = "testpass"
            override val trustStorePassword: String = "trustpass"
        }
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
    }

    @After
    fun cleanUp() {
        messagingClient?.stop()
        messagingServer?.stop()
        dataSource.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `server starting with the port already bound should throw`() {
        ServerSocket(hostAndPort.port).use {
            val messagingServer = createMessagingServer()
            assertThatThrownBy { messagingServer.start() }
        }
    }

    @Test
    fun `client should connect to remote server`() {
        val remoteServerAddress = freeLocalHostAndPort()

        createMessagingServer(remoteServerAddress).start()
        createMessagingClient(server = remoteServerAddress).start(rpcOps, userService)
    }

    @Test
    fun `client should throw if remote server not found`() {
        val serverAddress = freeLocalHostAndPort()
        val invalidServerAddress = freeLocalHostAndPort()

        createMessagingServer(serverAddress).start()

        messagingClient = createMessagingClient(server = invalidServerAddress)
        assertThatThrownBy { messagingClient!!.start(rpcOps, userService) }
        messagingClient = null
    }

    @Test
    fun `client should connect to local server`() {
        createMessagingServer().start()
        createMessagingClient().start(rpcOps, userService)
    }

    @Test
    fun `client should be able to send message to itself`() {
        val receivedMessages = LinkedBlockingQueue<Message>()

        createMessagingServer().start()

        val messagingClient = createMessagingClient()
        messagingClient.start(rpcOps, userService)
        thread { messagingClient.run() }

        messagingClient.addMessageHandler(topic) { message, r ->
            receivedMessages.add(message)
        }

        val message = messagingClient.createMessage(topic, DEFAULT_SESSION_ID, "first msg".toByteArray())
        messagingClient.send(message, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertEquals("first msg", String(actual.data))
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    private fun createMessagingClient(server: HostAndPort = hostAndPort): NodeMessagingClient {
        return databaseTransaction(database) {
            NodeMessagingClient(config, server, identity.public.tree, AffinityExecutor.ServiceAffinityExecutor("ArtemisMessagingTests", 1), database).apply {
                configureWithDevSSLCertificate()
                messagingClient = this
            }
        }
    }

    private fun createMessagingServer(local: HostAndPort = hostAndPort): ArtemisMessagingServer {
        return ArtemisMessagingServer(config, local, networkMapCache, userService).apply {
            configureWithDevSSLCertificate()
            messagingServer = this
        }
    }
}
