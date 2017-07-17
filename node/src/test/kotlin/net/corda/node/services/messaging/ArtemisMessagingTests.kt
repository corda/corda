package net.corda.node.services.messaging

import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.crypto.generateKeyPair
import net.corda.core.messaging.RPCOps
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.ALICE
import net.corda.testing.LogHelper
import net.corda.node.services.RPCUserService
import net.corda.node.services.RPCUserServiceImpl
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.freeLocalHostAndPort
import net.corda.testing.freePort
import net.corda.testing.node.MOCK_VERSION_INFO
import net.corda.testing.node.makeTestDataSourceProperties
import net.corda.testing.testNodeConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull

//TODO This needs to be merged into P2PMessagingTest as that creates a more realistic environment
class ArtemisMessagingTests {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    val serverPort = freePort()
    val rpcPort = freePort()
    val topic = "platform.self"
    val identity = generateKeyPair()

    lateinit var config: NodeConfiguration
    lateinit var database: CordaPersistence
    lateinit var userService: RPCUserService
    lateinit var networkMapRegistrationFuture: ListenableFuture<Unit>

    var messagingClient: NodeMessagingClient? = null
    var messagingServer: ArtemisMessagingServer? = null

    // TODO: We should have a dummy service hub rather than change behaviour in tests
    val networkMapCache = InMemoryNetworkMapCache(serviceHub = null)

    val rpcOps = object : RPCOps {
        override val protocolVersion: Int get() = throw UnsupportedOperationException()
    }

    @Before
    fun setUp() {
        val baseDirectory = temporaryFolder.root.toPath()
        userService = RPCUserServiceImpl(emptyList())
        config = testNodeConfiguration(
                baseDirectory = baseDirectory,
                myLegalName = ALICE.name)
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties())
        networkMapRegistrationFuture = Futures.immediateFuture(Unit)
    }

    @After
    fun cleanUp() {
        messagingClient?.stop()
        messagingServer?.stop()
        messagingClient = null
        messagingServer = null
        database.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `server starting with the port already bound should throw`() {
        ServerSocket(serverPort).use {
            val messagingServer = createMessagingServer()
            assertThatThrownBy { messagingServer.start() }
        }
    }

    @Test
    fun `client should connect to remote server`() {
        val remoteServerAddress = freeLocalHostAndPort()

        createMessagingServer(remoteServerAddress.port).start()
        createMessagingClient(server = remoteServerAddress)
        startNodeMessagingClient()
    }

    @Test
    fun `client should throw if remote server not found`() {
        val serverAddress = freeLocalHostAndPort()
        val invalidServerAddress = freeLocalHostAndPort()

        createMessagingServer(serverAddress.port).start()

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

        val networkMapMessage = messagingClient.createMessage(NetworkMapService.FETCH_TOPIC, DEFAULT_SESSION_ID, "second msg".toByteArray())
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
        networkMapRegistrationFuture = SettableFuture.create()

        val receivedMessages = LinkedBlockingQueue<Message>()

        val messagingClient = createAndStartClientAndServer(receivedMessages)
        for (iter in 1..iterations) {
            val message = messagingClient.createMessage(topic, DEFAULT_SESSION_ID, "first msg $iter".toByteArray())
            messagingClient.send(message, messagingClient.myAddress)
        }

        val networkMapMessage = messagingClient.createMessage(NetworkMapService.FETCH_TOPIC, DEFAULT_SESSION_ID, "second msg".toByteArray())
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
        messagingClient.addMessageHandler(topic) { message, _ ->
            receivedMessages.add(message)
        }
        messagingClient.addMessageHandler(NetworkMapService.FETCH_TOPIC) { message, _ ->
            receivedMessages.add(message)
        }
        // Run after the handlers are added, otherwise (some of) the messages get delivered and discarded / dead-lettered.
        thread { messagingClient.run(messagingServer!!.serverControl) }
        return messagingClient
    }

    private fun createMessagingClient(server: NetworkHostAndPort = NetworkHostAndPort("localhost", serverPort)): NodeMessagingClient {
        return database.transaction {
            NodeMessagingClient(
                    config,
                    MOCK_VERSION_INFO,
                    server,
                    identity.public,
                    ServiceAffinityExecutor("ArtemisMessagingTests", 1),
                    database,
                    networkMapRegistrationFuture,
                    MonitoringService(MetricRegistry())).apply {
                config.configureWithDevSSLCertificate()
                messagingClient = this
            }
        }
    }

    private fun createMessagingServer(local: Int = serverPort, rpc: Int = rpcPort): ArtemisMessagingServer {
        return ArtemisMessagingServer(config, local, rpc, networkMapCache, userService).apply {
            config.configureWithDevSSLCertificate()
            messagingServer = this
        }
    }
}
