/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.messaging

import com.codahale.metrics.MetricRegistry
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.generateKeyPair
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.node.internal.configureDatabase
import net.corda.node.services.config.*
import net.corda.node.services.network.NetworkMapCacheImpl
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.AffinityExecutor.ServiceAffinityExecutor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import net.corda.testing.node.internal.makeInternalTestDataSourceProperties
import org.apache.activemq.artemis.api.core.Message.HDR_VALIDATED_USER
import org.apache.activemq.artemis.api.core.SimpleString
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

    private val portAllocation = PortAllocation.Incremental(10000)
    private val serverPort = portAllocation.nextPort()
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
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000))).whenever(it).enterpriseConfiguration
            doReturn(FlowTimeoutConfiguration(5.seconds, 3, backoffBase = 1.0)).whenever(it).flowTimeout
        }
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeInternalTestDataSourceProperties(configSupplier = { ConfigFactory.empty() }), DatabaseConfig(runMigration = true), { null }, { null })
        val persistentNetworkMapCache = PersistentNetworkMapCache(database, ALICE_NAME).apply { start(emptyList()) }
        networkMapCache = NetworkMapCacheImpl(persistentNetworkMapCache, rigorousMock(), database).apply { start() }
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
        val remoteServerAddress = portAllocation.nextHostAndPort()

        createMessagingServer(remoteServerAddress.port).start()
        createMessagingClient(server = remoteServerAddress)
        startNodeMessagingClient()
    }

    @Test
    fun `client should throw if remote server not found`() {
        val serverAddress = portAllocation.nextHostAndPort()
        val invalidServerAddress = portAllocation.nextHostAndPort()

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
        thread {
            messagingClient.send(tooLagerMessage, messagingClient.myAddress)
        }.join(10.seconds.toMillis())
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

    @Test
    fun `we can fake send and receive`() {
        val (messagingClient, receivedMessages) = createAndStartClientAndServer()
        val message = messagingClient.createMessage(TOPIC, data = "first msg".toByteArray())
        val fakeMsg = messagingClient.messagingExecutor!!.cordaToArtemisMessage(message)
        fakeMsg!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))
        messagingClient.deliver(fakeMsg)
        val received = receivedMessages.take()
        assertThat(String(received.data.bytes, Charsets.UTF_8)).isEqualTo("first msg")
    }

    @Test
    fun `redelivery from same client is ignored`() {
        val (messagingClient, receivedMessages) = createAndStartClientAndServer()
        val message = messagingClient.createMessage(TOPIC, data = "first msg".toByteArray())
        val fakeMsg = messagingClient.messagingExecutor!!.cordaToArtemisMessage(message)
        fakeMsg!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))
        messagingClient.deliver(fakeMsg)
        messagingClient.deliver(fakeMsg)
        val received = receivedMessages.take()
        assertThat(String(received.data.bytes, Charsets.UTF_8)).isEqualTo("first msg")
        val received2 = receivedMessages.poll()
        assertThat(received2).isNull()
    }

    // Redelivery from a sender who stops and restarts (some re-sends from the sender, with sender state reset with exception of recovered checkpoints)
    @Test
    fun `re-send from different client is ignored`() {
        val (messagingClient1, receivedMessages) = createAndStartClientAndServer()
        val message = messagingClient1.createMessage(TOPIC, data = "first msg".toByteArray())
        val fakeMsg = messagingClient1.messagingExecutor!!.cordaToArtemisMessage(message)
        fakeMsg!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))
        messagingClient1.deliver(fakeMsg)

        // Now change the sender
        try {
            val messagingClient2 = createMessagingClient()
            startNodeMessagingClient()

            val fakeMsg2 = messagingClient2.messagingExecutor!!.cordaToArtemisMessage(message)
            fakeMsg2!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))

            messagingClient1.deliver(fakeMsg2)
            val received = receivedMessages.take()
            assertThat(String(received.data.bytes, Charsets.UTF_8)).isEqualTo("first msg")
            val received2 = receivedMessages.poll()
            assertThat(received2).isNull()
        } finally {
            messagingClient1.stop()
        }
    }

    // Redelivery to a receiver who stops and restarts (some re-deliveries from Artemis, but with receiver state reset)
    @Test
    fun `re-receive from different client is ignored`() {
        val (messagingClient1, receivedMessages) = createAndStartClientAndServer()
        val message = messagingClient1.createMessage(TOPIC, data = "first msg".toByteArray())
        val fakeMsg = messagingClient1.messagingExecutor!!.cordaToArtemisMessage(message)
        fakeMsg!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))
        messagingClient1.deliver(fakeMsg)

        // Now change the receiver
        try {
            val messagingClient2 = createMessagingClient()
            messagingClient2.addMessageHandler(TOPIC) { msg, _, handle ->
                database.transaction { handle.insideDatabaseTransaction() }
                handle.afterDatabaseTransaction() // We ACK first so that if it fails we won't get a duplicate in [receivedMessages]
                receivedMessages.add(msg)
            }
            startNodeMessagingClient()

            messagingClient2.deliver(fakeMsg)

            val received = receivedMessages.take()
            assertThat(String(received.data.bytes, Charsets.UTF_8)).isEqualTo("first msg")
            val received2 = receivedMessages.poll()
            assertThat(received2).isNull()
        } finally {
            messagingClient1.stop()
        }
    }

    // Redelivery to a receiver who stops and restarts (some re-deliveries from Artemis, but with receiver state reset), but the original
    // messages were recorded as consumed out of order, and only the *second* message was acked.
    @Test
    fun `re-receive from different client is not ignored when acked out of order`() {
        // Don't ack first message, pretend we exit before that happens (but after second message is acked).
        val (messagingClient1, receivedMessages) = createAndStartClientAndServer(dontAckCondition = { received -> String(received.data.bytes, Charsets.UTF_8) == "first msg" })
        val message1 = messagingClient1.createMessage(TOPIC, data = "first msg".toByteArray())
        val message2 = messagingClient1.createMessage(TOPIC, data = "second msg".toByteArray())
        val fakeMsg1 = messagingClient1.messagingExecutor!!.cordaToArtemisMessage(message1)
        val fakeMsg2 = messagingClient1.messagingExecutor!!.cordaToArtemisMessage(message2)
        fakeMsg1!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))
        fakeMsg2!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))

        messagingClient1.deliver(fakeMsg1)
        messagingClient1.deliver(fakeMsg2)

        // Now change the receiver
        try {
            val messagingClient2 = createMessagingClient()
            messagingClient2.addMessageHandler(TOPIC) { msg, _, handle ->
                // The try-finally causes the test to fail if there's a duplicate insert (which, naturally, is an error but otherwise gets swallowed).
                try {
                    database.transaction { handle.insideDatabaseTransaction() }
                } finally {
                    handle.afterDatabaseTransaction() // We ACK first so that if it fails we won't get a duplicate in [receivedMessages]
                    receivedMessages.add(msg)
                }
            }
            startNodeMessagingClient()

            messagingClient2.deliver(fakeMsg1)
            messagingClient2.deliver(fakeMsg2)

            // Should receive 2 and then 1 (and not 2 again).
            val received = receivedMessages.take()
            assertThat(received.senderSeqNo).isEqualTo(1)
            val received2 = receivedMessages.poll()
            assertThat(received2.senderSeqNo).isEqualTo(0)
            val received3 = receivedMessages.poll()
            assertThat(received3).isNull()
        } finally {
            messagingClient1.stop()
        }
    }

    // Re-receive on different client from re-started sender
    @Test
    fun `re-send from different client and re-receive from different client is ignored`() {
        val (messagingClient1, receivedMessages) = createAndStartClientAndServer()
        val message = messagingClient1.createMessage(TOPIC, data = "first msg".toByteArray())
        val fakeMsg = messagingClient1.messagingExecutor!!.cordaToArtemisMessage(message)
        fakeMsg!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))
        messagingClient1.deliver(fakeMsg)

        // Now change the send *and* receiver
        val messagingClient2 = createMessagingClient()
        try {
            startNodeMessagingClient()
            val fakeMsg2 = messagingClient2.messagingExecutor!!.cordaToArtemisMessage(message)
            fakeMsg2!!.putStringProperty(HDR_VALIDATED_USER, SimpleString("O=Bank A, L=New York, C=US"))

            val messagingClient3 = createMessagingClient()
            messagingClient3.addMessageHandler(TOPIC) { msg, _, handle ->
                database.transaction { handle.insideDatabaseTransaction() }
                handle.afterDatabaseTransaction() // We ACK first so that if it fails we won't get a duplicate in [receivedMessages]
                receivedMessages.add(msg)
            }
            startNodeMessagingClient()

            messagingClient3.deliver(fakeMsg2)

            val received = receivedMessages.take()
            assertThat(String(received.data.bytes, Charsets.UTF_8)).isEqualTo("first msg")
            val received2 = receivedMessages.poll()
            assertThat(received2).isNull()
        } finally {
            messagingClient1.stop()
            messagingClient2.stop()
        }
    }

    private fun startNodeMessagingClient(maxMessageSize: Int = MAX_MESSAGE_SIZE) {
        messagingClient!!.start(identity.public, null, maxMessageSize, legalName = ALICE_NAME.toString())
    }

    private fun createAndStartClientAndServer(platformVersion: Int = 1, serverMaxMessageSize: Int = MAX_MESSAGE_SIZE,
                                              clientMaxMessageSize: Int = MAX_MESSAGE_SIZE,
                                              dontAckCondition: (msg: ReceivedMessage) -> Boolean = { false }
    ): Pair<P2PMessagingClient, BlockingQueue<ReceivedMessage>> {
        val receivedMessages = LinkedBlockingQueue<ReceivedMessage>()

        createMessagingServer(maxMessageSize = serverMaxMessageSize).start()

        val messagingClient = createMessagingClient(platformVersion = platformVersion)
        messagingClient.addMessageHandler(TOPIC) { message, _, handle ->
            if (dontAckCondition(message)) return@addMessageHandler
            database.transaction { handle.insideDatabaseTransaction() }
            handle.afterDatabaseTransaction() // We ACK first so that if it fails we won't get a duplicate in [receivedMessages]
            receivedMessages.add(message)
        }
        startNodeMessagingClient(maxMessageSize = clientMaxMessageSize)

        // Run after the handlers are added, otherwise (some of) the messages get delivered and discarded / dead-lettered.
        thread(isDaemon = true) { messagingClient.run() }

        return Pair(messagingClient, receivedMessages)
    }

    private fun createMessagingClient(server: NetworkHostAndPort = NetworkHostAndPort("localhost", serverPort), platformVersion: Int = 1): P2PMessagingClient {
        return database.transaction {
            P2PMessagingClient(
                    config,
                    MOCK_VERSION_INFO.copy(platformVersion = platformVersion),
                    server,
                    ServiceAffinityExecutor("ArtemisMessagingTests", 1),
                    database,
                    networkMapCache,
                    MetricRegistry(),
                    isDrainingModeOn = { false },
                    drainingModeWasChangedEvents = PublishSubject.create()).apply {
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
