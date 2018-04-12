package net.corda.node.services.messaging

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.generateKeyPair
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.node.internal.configureDatabase
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.P2PMessagingRetryConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.network.NetworkMapCacheImpl
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.subjects.PublishSubject
import java.net.ServerSocket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtemisMessagingTest {
    companion object {
        const val TOPIC = "platform.self"
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val serverPort = freePort()
    private val identity = generateKeyPair()

    private lateinit var config: NodeConfiguration
    private lateinit var database: CordaPersistence
    private var messagingClient: P2PMessagingClient? = null
    private var messagingServer: ArtemisMessagingServer? = null

    private lateinit var networkMapCache: NetworkMapCacheImpl

    @Before
    fun setUp() {
        abstract class AbstractNodeConfiguration : NodeConfiguration
        config = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(NetworkHostAndPort("0.0.0.0", serverPort)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
            doReturn(P2PMessagingRetryConfiguration(5.seconds, 3, backoffBase = 1.0)).whenever(it).p2pMessagingRetry
        }
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), rigorousMock())
        networkMapCache = NetworkMapCacheImpl(PersistentNetworkMapCache(database, emptyList()).start(), rigorousMock())
    }

    @After
    fun cleanUp() {
        messagingClient?.stop()
        messagingServer?.stop()
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
        val (messagingClient, receivedMessages) = createAndStartClientAndServer()
        val message = messagingClient.createMessage(TOPIC, data = "first msg".toByteArray())
        messagingClient.send(message, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertEquals("first msg", String(actual.data.bytes))
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    @Test
    fun `client should fail if message exceed maxMessageSize limit`() {
        val (messagingClient, receivedMessages) = createAndStartClientAndServer()
        val message = messagingClient.createMessage(TOPIC, data = ByteArray(MAX_MESSAGE_SIZE))
        messagingClient.send(message, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertTrue(ByteArray(MAX_MESSAGE_SIZE).contentEquals(actual.data.bytes))
        assertNull(receivedMessages.poll(200, MILLISECONDS))

        val tooLagerMessage = messagingClient.createMessage(TOPIC, data = ByteArray(MAX_MESSAGE_SIZE + 1))
        assertThatThrownBy {
            messagingClient.send(tooLagerMessage, messagingClient.myAddress)
        }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Message exceeds maxMessageSize network parameter")

        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }
    @Test
    fun `server should not process if incoming message exceed maxMessageSize limit`() {
        val (messagingClient, receivedMessages) = createAndStartClientAndServer(clientMaxMessageSize = 100_000, serverMaxMessageSize = 50_000)
        val message = messagingClient.createMessage(TOPIC, data = ByteArray(50_000))
        messagingClient.send(message, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertTrue(ByteArray(50_000).contentEquals(actual.data.bytes))
        assertNull(receivedMessages.poll(200, MILLISECONDS))

        val tooLagerMessage = messagingClient.createMessage(TOPIC, data = ByteArray(100_000))
        assertThatThrownBy {
            messagingClient.send(tooLagerMessage, messagingClient.myAddress)
        }.isInstanceOf(ActiveMQConnectionTimedOutException::class.java)
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    @Test
    fun `platform version is included in the message`() {
        val (messagingClient, receivedMessages) = createAndStartClientAndServer(platformVersion = 3)
        val message = messagingClient.createMessage(TOPIC, data = "first msg".toByteArray())
        messagingClient.send(message, messagingClient.myAddress)

        val received = receivedMessages.take()
        assertThat(received.platformVersion).isEqualTo(3)
    }

    private fun startNodeMessagingClient() {
        messagingClient!!.start()
    }

    private fun createAndStartClientAndServer(platformVersion: Int = 1, serverMaxMessageSize: Int = MAX_MESSAGE_SIZE, clientMaxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<P2PMessagingClient, BlockingQueue<ReceivedMessage>> {
        val receivedMessages = LinkedBlockingQueue<ReceivedMessage>()

        createMessagingServer(maxMessageSize = serverMaxMessageSize).start()

        val messagingClient = createMessagingClient(platformVersion = platformVersion, maxMessageSize = clientMaxMessageSize)
        messagingClient.addMessageHandler(TOPIC) { message, _, handle ->
            database.transaction { handle.insideDatabaseTransaction() }
            handle.afterDatabaseTransaction() // We ACK first so that if it fails we won't get a duplicate in [receivedMessages]
            receivedMessages.add(message)
        }
        startNodeMessagingClient()

        // Run after the handlers are added, otherwise (some of) the messages get delivered and discarded / dead-lettered.
        thread(isDaemon = true) { messagingClient.run() }

        return Pair(messagingClient, receivedMessages)
    }

    private fun createMessagingClient(server: NetworkHostAndPort = NetworkHostAndPort("localhost", serverPort), platformVersion: Int = 1, maxMessageSize: Int = MAX_MESSAGE_SIZE): P2PMessagingClient {
        return database.transaction {
            P2PMessagingClient(
                    config,
                    MOCK_VERSION_INFO.copy(platformVersion = platformVersion),
                    server,
                    identity.public,
                    null,
                    ServiceAffinityExecutor("ArtemisMessagingTests", 1),
                    database,
                    networkMapCache,
                    maxMessageSize = maxMessageSize,
                    isDrainingModeOn = { false },
                    drainingModeWasChangedEvents = PublishSubject.create<Pair<Boolean, Boolean>>()).apply {
                config.configureWithDevSSLCertificate()
                messagingClient = this
            }
        }
    }

    private fun createMessagingServer(local: Int = serverPort, maxMessageSize: Int = MAX_MESSAGE_SIZE): ArtemisMessagingServer {
        return ArtemisMessagingServer(config, NetworkHostAndPort("0.0.0.0", local), maxMessageSize).apply {
            config.configureWithDevSSLCertificate()
            messagingServer = this
        }
    }
}
