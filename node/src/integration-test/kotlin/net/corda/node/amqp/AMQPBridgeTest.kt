package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.*
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
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.*
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
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

    // TODO: revisit upon Matthew Nesbitt return
    @Ignore()
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

        val receivedSequence = mutableListOf<Int>()

        fun formatMessage(expected: String, actual: Int, received: List<Int>): String {
            return "Expected message with id $expected, got $actual, previous message receive sequence: "
            "${received.joinToString(",  ", "[", "]")}."
        }

        val received1 = receive.next()
        val messageID1 = received1.applicationProperties["CountProp"] as Int
        assertArrayEquals("Test$messageID1".toByteArray(), received1.payload)
        assertEquals(0, messageID1)
        received1.complete(true) // Accept first message
        receivedSequence.add(messageID1)

        val received2 = receive.next()
        val messageID2 = received2.applicationProperties["CountProp"] as Int
        assertArrayEquals("Test$messageID2".toByteArray(), received2.payload)
        assertEquals(1, messageID2, formatMessage("1", messageID2, receivedSequence))
        received2.complete(false) // Reject message
        receivedSequence.add(messageID2)

        while (true) {
            val received3 = receive.next()
            val messageID3 = received3.applicationProperties["CountProp"] as Int
            assertArrayEquals("Test$messageID3".toByteArray(), received3.payload)
            assertNotEquals(0, messageID3, formatMessage("< 1", messageID3, receivedSequence))
            receivedSequence.add(messageID3)
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
            receivedSequence.add(messageID4)
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
                assertEquals(-1, messageID5, formatMessage("-1", messageID5, receivedSequence)) // next message should be in order though
                assertArrayEquals("Test_end".toByteArray(), received5.payload)
                receivedSequence.add(messageID5)
                break
            }
            receivedSequence.add(messageID5)
            received5.complete(true)
        }

        println("Message sequence: ${receivedSequence.joinToString(", ", "[", "]")}")
        bridgeManager.stop()
        amqpServer.stop()
        artemisClient.stop()
        artemisServer.stop()
    }

    @Test
    @Ignore("Run only manually to check the throughput of the AMQP bridge")
    fun `AMQP full bridge throughput`() {
        val numMessages = 10000
        // Create local queue
        val sourceQueueName = "internal.peers." + BOB.publicKey.toStringShort()
        val (artemisServer, artemisClient, bridgeManager) = createArtemis(sourceQueueName)

        val artemis = artemisClient.started!!
        val queueName = ArtemisMessagingComponent.RemoteInboxAddress(BOB.publicKey).queueName

        val (artemisRecServer, artemisRecClient) = createArtemisReceiver(amqpAddress, "artemisBridge")
        //artemisBridgeClient.started!!.session.createQueue(SimpleString(queueName), RoutingType.ANYCAST, SimpleString(queueName), true)

        var numReceived = 0

        artemisRecClient.started!!.session.createQueue(SimpleString(queueName), RoutingType.ANYCAST, SimpleString(queueName), true)
        val artemisConsumer = artemisRecClient.started!!.session.createConsumer(queueName)

        val rubbishPayload = ByteArray(10 * 1024)
        var timeNanosCreateMessage = 0L
        var timeNanosSendMessage = 0L
        var timeMillisRead = 0L
        val simpleSourceQueueName = SimpleString(sourceQueueName)
        val totalTimeMillis = measureTimeMillis {
            repeat(numMessages) {
                var artemisMessage: ClientMessage? = null
                timeNanosCreateMessage += measureNanoTime {
                    artemisMessage = artemis.session.createMessage(true).apply {
                        putIntProperty("CountProp", it)
                        writeBodyBufferBytes(rubbishPayload)
                        // Use the magic deduplication property built into Artemis as our message identity too
                        putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
                    }
                }
                timeNanosSendMessage += measureNanoTime {
                    artemis.producer.send(simpleSourceQueueName, artemisMessage, {})
                }
            }
            artemisClient.started!!.session.commit()


            timeMillisRead = measureTimeMillis {
                while (numReceived < numMessages) {
                    val current = artemisConsumer.receive()
                    val messageId = current.getIntProperty("CountProp")
                    assertEquals(numReceived, messageId)
                    ++numReceived
                    current.acknowledge()
                }
            }
        }
        println("Creating $numMessages messages took ${timeNanosCreateMessage / (1000 * 1000)} milliseconds")
        println("Sending $numMessages messages took ${timeNanosSendMessage / (1000 * 1000)} milliseconds")
        println("Receiving $numMessages messages took $timeMillisRead milliseconds")
        println("Total took $totalTimeMillis milliseconds")
        assertEquals(numMessages, numReceived)

        bridgeManager.stop()
        artemisClient.stop()
        artemisServer.stop()
        artemisRecClient.stop()
        artemisRecServer.stop()
    }


    private fun createArtemis(sourceQueueName: String?): Triple<ArtemisMessagingServer, ArtemisMessagingClient, BridgeManager> {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "artemis").whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(artemisAddress).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000))).whenever(it).enterpriseConfiguration
        }
        artemisConfig.configureWithDevSSLCertificate()
        val artemisServer = ArtemisMessagingServer(artemisConfig, artemisPort, MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig, artemisAddress, MAX_MESSAGE_SIZE, confirmationWindowSize = 10 * 1024)
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

    private fun createArtemisReceiver(targetAdress: NetworkHostAndPort, workingDir: String): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / workingDir).whenever(it).baseDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(targetAdress).whenever(it).p2pAddress
            doReturn("").whenever(it).jmxMonitoringHttpPort
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000))).whenever(it).enterpriseConfiguration
        }
        artemisConfig.configureWithDevSSLCertificate()
        val artemisServer = ArtemisMessagingServer(artemisConfig, targetAdress.port, MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig, targetAdress, MAX_MESSAGE_SIZE, confirmationWindowSize = 10 * 1024)
        artemisServer.start()
        artemisClient.start()

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