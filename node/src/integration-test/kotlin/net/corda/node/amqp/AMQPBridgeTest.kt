package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.bridging.AMQPBridgeManager
import net.corda.nodeapi.internal.bridging.BridgeManager
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Assert.assertArrayEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    @Ignore
    @Test
    fun `test acked and nacked messages`() {
        // Create local queue
        val sourceQueueName = "internal.peers." + BOB.publicKey.toStringShort()
        val (artemisServer, artemisClient, bridgeManager) = createArtemis(sourceQueueName)

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


        while (true) {
            val received5 = receive.next()
            val messageID5 = received5.applicationProperties["CountProp"] as Int
            if (messageID5 != 2) { // we may get a duplicate of the interrupted message, in which case skip
                assertEquals(-1, messageID5) // next message should be in order though
                assertArrayEquals("Test_end".toByteArray(), received5.payload)
                break
            }
            received5.complete(true)
        }

        bridgeManager.stop()
        amqpServer.stop()
        artemisClient.stop()
        artemisServer.stop()
    }

    private fun createArtemis(sourceQueueName: String?): Triple<ArtemisMessagingServer, ArtemisMessagingClient, BridgeManager> {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "artemis").whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(artemisAddress).whenever(it).p2pAddress
            doReturn("").whenever(it).exportJMXto
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
        }
        artemisConfig.configureWithDevSSLCertificate()
        val artemisServer = ArtemisMessagingServer(artemisConfig, artemisPort, MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig, artemisAddress, MAX_MESSAGE_SIZE)
        artemisServer.start()
        artemisClient.start()
        val bridgeManager = AMQPBridgeManager(artemisConfig, artemisAddress, MAX_MESSAGE_SIZE)
        bridgeManager.start()
        val artemis = artemisClient.started!!
        if (sourceQueueName != null) {
            // Local queue for outgoing messages
            artemis.session.createQueue(sourceQueueName, RoutingType.ANYCAST, sourceQueueName, true)
            bridgeManager.deployBridge(sourceQueueName, amqpAddress, setOf(BOB.name))
        }
        return Triple(artemisServer, artemisClient, bridgeManager)
    }

    private fun createAMQPServer(): AMQPServer {
        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "server").whenever(it).baseDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
        }
        serverConfig.configureWithDevSSLCertificate()

        return AMQPServer("0.0.0.0",
                amqpPort,
                ArtemisMessagingComponent.PEER_USER,
                ArtemisMessagingComponent.PEER_USER,
                serverConfig.loadSslKeyStore().internal,
                serverConfig.keyStorePassword,
                serverConfig.loadTrustStore().internal,
                trace = true
        )
    }
}