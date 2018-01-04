package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.toBase58String
import net.corda.node.internal.protonwrapper.netty.AMQPServer
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.services.config.*
import net.corda.node.services.messaging.ArtemisMessagingClient
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_QUEUE
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.testing.*
import net.corda.testing.internal.rigorousMock
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.Observable
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AMQPBridgeTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val ALICE = TestIdentity(ALICE_NAME)
    private val BOB = TestIdentity(BOB_NAME)

    private val artemisPort = freePort()
    private val artemisPort2 = freePort()
    private val amqpPort = freePort()
    private val artemisAddress = NetworkHostAndPort("localhost", artemisPort)
    private val artemisAddress2 = NetworkHostAndPort("localhost", artemisPort2)
    private val amqpAddress = NetworkHostAndPort("localhost", amqpPort)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `test acked and nacked messages`() {
        // Create local queue
        val sourceQueueName = "internal.peers." + BOB.publicKey.toBase58String()
        val (artemisServer, artemisClient) = createArtemis(sourceQueueName)

        // Pre-populate local queue with 3 messages
        val artemis = artemisClient.started!!
        for (i in 0 until 3) {
            val artemisMessage = artemis.session.createMessage(true).apply {
                putIntProperty("CountProp", i)
                writeBodyBufferBytes("Test$i".toByteArray())
                // Use the magic deduplication property built into Artemis as our message identity too
                putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
            }
            artemis.producer.send(sourceQueueName, artemisMessage)
        }

        //Create target server
        val amqpServer = createAMQPServer()

        val receive = amqpServer.onReceive.toBlocking().iterator
        amqpServer.start()

        val received1 = receive.next()
        val messageID1 = received1.applicationProperties["CountProp"] as Int
        assertArrayEquals("Test$messageID1".toByteArray(), received1.payload)
        assertEquals(0, messageID1)
        received1.complete(true) // Accept first message

        val received2 = receive.next()
        val messageID2 = received2.applicationProperties["CountProp"] as Int
        assertArrayEquals("Test$messageID2".toByteArray(), received2.payload)
        assertEquals(1, messageID2)
        received2.complete(false) // Reject message

        while (true) {
            val received3 = receive.next()
            val messageID3 = received3.applicationProperties["CountProp"] as Int
            assertArrayEquals("Test$messageID3".toByteArray(), received3.payload)
            assertNotEquals(0, messageID3)
            if (messageID3 != 1) { // keep rejecting any batched items following rejection
                received3.complete(false)
            } else { // beginnings of replay so accept again
                received3.complete(true)
                break
            }
        }

        while (true) {
            val received4 = receive.next()
            val messageID4 = received4.applicationProperties["CountProp"] as Int
            assertArrayEquals("Test$messageID4".toByteArray(), received4.payload)
            if (messageID4 != 1) { // we may get a duplicate of the rejected message, in which case skip
                assertEquals(2, messageID4) // next message should be in order though
                break
            }
            received4.complete(true)
        }

        // Send a fresh item and check receive
        val artemisMessage = artemis.session.createMessage(true).apply {
            putIntProperty("CountProp", -1)
            writeBodyBufferBytes("Test_end".toByteArray())
            // Use the magic deduplication property built into Artemis as our message identity too
            putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
        }
        artemis.producer.send(sourceQueueName, artemisMessage)

        val received5 = receive.next()
        val messageID5 = received5.applicationProperties["CountProp"] as Int
        assertArrayEquals("Test_end".toByteArray(), received5.payload)
        assertEquals(-1, messageID5) // next message should be in order
        received5.complete(true)

        amqpServer.stop()
        artemisClient.stop()
        artemisServer.stop()
    }

    @Test
    fun `Test legacy bridge still works`() {
        // Create local queue
        val sourceQueueName = "internal.peers." + ALICE.publicKey.toBase58String()
        val (artemisLegacyServer, artemisLegacyClient) = createLegacyArtemis(sourceQueueName)


        val (artemisServer, artemisClient) = createArtemis(null)

        val artemis = artemisLegacyClient.started!!
        for (i in 0 until 3) {
            val artemisMessage = artemis.session.createMessage(true).apply {
                putIntProperty("CountProp", i)
                writeBodyBufferBytes("Test$i".toByteArray())
                // Use the magic deduplication property built into Artemis as our message identity too
                putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
            }
            artemis.producer.send(sourceQueueName, artemisMessage)
        }


        val subs = artemisClient.started!!.session.createConsumer(P2P_QUEUE)
        for (i in 0 until 3) {
            val msg = subs.receive()
            val messageBody = ByteArray(msg.bodySize).apply { msg.bodyBuffer.readBytes(this) }
            assertArrayEquals("Test$i".toByteArray(), messageBody)
            assertEquals(i, msg.getIntProperty("CountProp"))
        }

        artemisClient.stop()
        artemisServer.stop()
        artemisLegacyClient.stop()
        artemisLegacyServer.stop()

    }

    private fun createArtemis(sourceQueueName: String?): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "artemis").whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn("").whenever(it).exportJMXto
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
            doReturn(true).whenever(it).useAMQPBridges
        }
        artemisConfig.configureWithDevSSLCertificate()
        val networkMap = rigorousMock<NetworkMapCache>().also {
            doReturn(Observable.never<NetworkMapCache.MapChange>()).whenever(it).changed
            doReturn(listOf(NodeInfo(listOf(amqpAddress), listOf(BOB.identity), 1, 1L))).whenever(it).getNodesByLegalIdentityKey(any())
        }
        val userService = rigorousMock<RPCSecurityManager>()
        val artemisServer = ArtemisMessagingServer(artemisConfig, artemisPort, null, networkMap, userService, MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig, artemisAddress, MAX_MESSAGE_SIZE)
        artemisServer.start()
        artemisClient.start()
        val artemis = artemisClient.started!!
        if (sourceQueueName != null) {
            // Local queue for outgoing messages
            artemis.session.createQueue(sourceQueueName, RoutingType.MULTICAST, sourceQueueName, true)
        }
        return Pair(artemisServer, artemisClient)
    }

    private fun createLegacyArtemis(sourceQueueName: String): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "artemis2").whenever(it).baseDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn("").whenever(it).exportJMXto
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
            doReturn(false).whenever(it).useAMQPBridges
            doReturn(ActiveMqServerConfiguration(BridgeConfiguration(0, 0, 0.0))).whenever(it).activeMQServer
        }
        artemisConfig.configureWithDevSSLCertificate()
        val networkMap = rigorousMock<NetworkMapCache>().also {
            doReturn(Observable.never<NetworkMapCache.MapChange>()).whenever(it).changed
            doReturn(listOf(NodeInfo(listOf(artemisAddress), listOf(ALICE.identity), 1, 1L))).whenever(it).getNodesByLegalIdentityKey(any())
        }
        val userService = rigorousMock<RPCSecurityManager>()
        val artemisServer = ArtemisMessagingServer(artemisConfig, artemisPort2, null, networkMap, userService, MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig, artemisAddress2, MAX_MESSAGE_SIZE)
        artemisServer.start()
        artemisClient.start()
        val artemis = artemisClient.started!!
        // Local queue for outgoing messages
        artemis.session.createQueue(sourceQueueName, RoutingType.MULTICAST, sourceQueueName, true)
        return Pair(artemisServer, artemisClient)
    }

    private fun createAMQPServer(): AMQPServer {
        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "server").whenever(it).baseDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
        }
        serverConfig.configureWithDevSSLCertificate()

        val serverTruststore = loadKeyStore(serverConfig.trustStoreFile, serverConfig.trustStorePassword)
        val serverKeystore = loadKeyStore(serverConfig.sslKeystore, serverConfig.keyStorePassword)
        val amqpServer = AMQPServer("0.0.0.0",
                amqpPort,
                ArtemisMessagingComponent.PEER_USER,
                ArtemisMessagingComponent.PEER_USER,
                serverKeystore,
                serverConfig.keyStorePassword,
                serverTruststore,
                trace = true)
        return amqpServer
    }
}