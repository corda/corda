package net.corda.node.services

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.composite
import net.corda.core.crypto.generateKeyPair
import net.corda.core.messaging.Message
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.createMessage
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.utilities.LogHelper
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.TestNodeConfiguration
import net.corda.testing.freeLocalHostAndPort
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull

//TODO This needs to be merged into P2PMessagingTest as that creates a more realistic environment
class ArtemisMessagingTests {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    val hostAndPort = freeLocalHostAndPort()
    val topic = "platform.self"
    val identity = generateKeyPair()

    lateinit var config: NodeConfiguration
    lateinit var dataSource: Closeable
    lateinit var database: Database
    lateinit var userService: RPCUserService
    lateinit var networkMapRegistrationFuture: ListenableFuture<Unit>

    var messagingClient: NodeMessagingClient? = null
    var messagingServer: ArtemisMessagingServer? = null

    val networkMapCache = InMemoryNetworkMapCache()

    val rpcOps = object : RPCOps {
        override val protocolVersion: Int get() = throw UnsupportedOperationException()
    }

    @Before
    fun setUp() {
        userService = RPCUserServiceImpl(FullNodeConfiguration(ConfigFactory.empty()))
        config = TestNodeConfiguration(
                basedir = temporaryFolder.newFolder().toPath(),
                myLegalName = "me",
                networkMapService = null)
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        networkMapRegistrationFuture = Futures.immediateFuture(Unit)
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
        createMessagingClient(server = remoteServerAddress)
        startNodeMessagingClient()
    }

    @Test
    fun `client should throw if remote server not found`() {
        val serverAddress = freeLocalHostAndPort()
        val invalidServerAddress = freeLocalHostAndPort()

        createMessagingServer(serverAddress).start()

        messagingClient = createMessagingClient(server = invalidServerAddress)
        assertThatThrownBy { startNodeMessagingClient() }
        messagingClient = null
    }

    @Test
    fun `client should connect to local server`() {
        createMessagingServer().start()
        createMessagingClient()
        startNodeMessagingClient()
    }

    @Test
    fun `client should be able to send message to itself`() {
        val receivedMessages = LinkedBlockingQueue<Message>()

        val messagingClient = createAndStartClientAndServer(receivedMessages)
        val message = messagingClient.createMessage(topic, DEFAULT_SESSION_ID, "first msg".toByteArray())
        messagingClient.send(message, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertEquals("first msg", String(actual.data))
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    @Test
    fun `client should be able to send message to itself before network map is available, and receive after`() {
        val settableFuture: SettableFuture<Unit> = SettableFuture.create()
        networkMapRegistrationFuture = settableFuture

        val receivedMessages = LinkedBlockingQueue<Message>()

        val messagingClient = createAndStartClientAndServer(receivedMessages)
        val message = messagingClient.createMessage(topic, DEFAULT_SESSION_ID, "first msg".toByteArray())
        messagingClient.send(message, messagingClient.myAddress)

        val networkMapMessage = messagingClient.createMessage(NetworkMapService.FETCH_FLOW_TOPIC, DEFAULT_SESSION_ID, "second msg".toByteArray())
        messagingClient.send(networkMapMessage, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertEquals("second msg", String(actual.data))
        assertNull(receivedMessages.poll(200, MILLISECONDS))
        settableFuture.set(Unit)
        val firstActual: Message = receivedMessages.take()
        assertEquals("first msg", String(firstActual.data))
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    @Test
    fun `client should be able to send large numbers of messages to itself before network map is available and survive restart, then receive messages`() {
        // Crank the iteration up as high as you want... just takes longer to run.
        val iterations = 100
        val settableFuture: SettableFuture<Unit> = SettableFuture.create()
        networkMapRegistrationFuture = settableFuture

        val receivedMessages = LinkedBlockingQueue<Message>()

        val messagingClient = createAndStartClientAndServer(receivedMessages)
        for (iter in 1..iterations) {
            val message = messagingClient.createMessage(topic, DEFAULT_SESSION_ID, "first msg $iter".toByteArray())
            messagingClient.send(message, messagingClient.myAddress)
        }

        val networkMapMessage = messagingClient.createMessage(NetworkMapService.FETCH_FLOW_TOPIC, DEFAULT_SESSION_ID, "second msg".toByteArray())
        messagingClient.send(networkMapMessage, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertEquals("second msg", String(actual.data))
        assertNull(receivedMessages.poll(200, MILLISECONDS))

        // Stop client and server and create afresh.
        messagingClient.stop()
        messagingServer?.stop()

        networkMapRegistrationFuture = Futures.immediateFuture(Unit)

        createAndStartClientAndServer(receivedMessages)
        for (iter in 1..iterations) {
            val firstActual: Message = receivedMessages.take()
            assertThat(String(firstActual.data)).isEqualTo("first msg $iter")
        }
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    private fun startNodeMessagingClient() {
        messagingClient!!.start(rpcOps, userService)
    }

    private fun createAndStartClientAndServer(receivedMessages: LinkedBlockingQueue<Message>): NodeMessagingClient {
        createMessagingServer().start()

        val messagingClient = createMessagingClient()
        startNodeMessagingClient()
        messagingClient.addMessageHandler(topic) { message, r ->
            receivedMessages.add(message)
        }
        messagingClient.addMessageHandler(NetworkMapService.FETCH_FLOW_TOPIC) { message, r ->
            receivedMessages.add(message)
        }
        // Run after the handlers are added, otherwise (some of) the messages get delivered and discarded / dead-lettered.
        thread { messagingClient.run() }
        return messagingClient
    }

    private fun createMessagingClient(server: HostAndPort = hostAndPort): NodeMessagingClient {
        return databaseTransaction(database) {
            NodeMessagingClient(
                    config,
                    server,
                    identity.public.composite,
                    ServiceAffinityExecutor("ArtemisMessagingTests", 1),
                    database,
                    networkMapRegistrationFuture).apply {
                config.configureWithDevSSLCertificate()
                messagingClient = this
            }
        }
    }

    private fun createMessagingServer(local: HostAndPort = hostAndPort): ArtemisMessagingServer {
        return ArtemisMessagingServer(config, local, networkMapCache, userService).apply {
            config.configureWithDevSSLCertificate()
            messagingServer = this
        }
    }
}
