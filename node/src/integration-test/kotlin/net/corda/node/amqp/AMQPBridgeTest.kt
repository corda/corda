package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.div
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import net.corda.nodeapi.internal.bridging.AMQPBridgeManager
import net.corda.nodeapi.internal.bridging.BridgeManager
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.stubs.CertificateStoreStubs
import net.corda.testing.internal.rigorousMock
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.*
import kotlin.test.assertEquals

class AMQPBridgeTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val log = loggerFor<AMQPBridgeTest>()

    private val BOB = TestIdentity(BOB_NAME)

    private val portAllocation = PortAllocation.Incremental(10000)
    private val artemisAddress = portAllocation.nextHostAndPort()
    private val amqpAddress = portAllocation.nextHostAndPort()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `test acked and nacked messages`() {
        // Create local queue
        val sourceQueueName = "internal.peers." + BOB.publicKey.toStringShort()
        val (artemisServer, artemisClient, bridgeManager) = createArtemis(sourceQueueName)

        // Pre-populate local queue with 3 messages
        val artemis = artemisClient.started!!
        for (i in 0 until 3) {
            val artemisMessage = artemis.session.createMessage(true).apply {
                putIntProperty(P2PMessagingHeaders.senderUUID, i)
                writeBodyBufferBytes("Test$i".toByteArray())
                // Use the magic deduplication property built into Artemis as our message identity too
                putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
            }
            artemis.producer.send(sourceQueueName, artemisMessage)
        }

        //Create target server
        val amqpServer = createAMQPServer()
        val dedupeSet = mutableSetOf<String>()

        val receive = amqpServer.onReceive.toBlocking().iterator
        amqpServer.start()

        val receivedSequence = mutableListOf<Int>()
        val atNodeSequence = mutableListOf<Int>()

        fun formatMessage(expected: String, actual: Int, received: List<Int>): String {
            return "Expected message with id $expected, got $actual, previous message receive sequence: " +
                    "${received.joinToString(",  ", "[", "]")}."
        }

        val received1 = receive.next()
        val messageID1 = received1.applicationProperties[P2PMessagingHeaders.senderUUID.toString()] as Int
        assertArrayEquals("Test$messageID1".toByteArray(), received1.payload)
        assertEquals(0, messageID1)
        dedupeSet += received1.applicationProperties[HDR_DUPLICATE_DETECTION_ID.toString()] as String
        received1.complete(true) // Accept first message
        receivedSequence += messageID1
        atNodeSequence += messageID1

        val received2 = receive.next()
        val messageID2 = received2.applicationProperties[P2PMessagingHeaders.senderUUID.toString()] as Int
        assertArrayEquals("Test$messageID2".toByteArray(), received2.payload)
        assertEquals(1, messageID2, formatMessage("1", messageID2, receivedSequence))
        received2.complete(false) // Reject message and don't add to dedupe
        receivedSequence += messageID2 // reflects actual sequence

        // drop things until we get back to the replay
        while (true) {
            val received3 = receive.next()
            val messageID3 = received3.applicationProperties[P2PMessagingHeaders.senderUUID.toString()] as Int
            assertArrayEquals("Test$messageID3".toByteArray(), received3.payload)
            receivedSequence += messageID3
            if (messageID3 != 1) { // keep rejecting any batched items following rejection
                received3.complete(false)
            } else { // beginnings of replay so accept again
                received3.complete(true)
                val messageId = received3.applicationProperties[HDR_DUPLICATE_DETECTION_ID.toString()] as String
                if (messageId !in dedupeSet) {
                    dedupeSet += messageId
                    atNodeSequence += messageID3
                }
                break
            }
        }

        // start receiving again, but discarding duplicates
        while (true) {
            val received4 = receive.next()
            val messageID4 = received4.applicationProperties[P2PMessagingHeaders.senderUUID.toString()] as Int
            assertArrayEquals("Test$messageID4".toByteArray(), received4.payload)
            receivedSequence += messageID4
            val messageId = received4.applicationProperties[HDR_DUPLICATE_DETECTION_ID.toString()] as String
            if (messageId !in dedupeSet) {
                dedupeSet += messageId
                atNodeSequence += messageID4
            }
            received4.complete(true)
            if (messageID4 == 2) { // started to replay messages after rejection point
                break
            }
        }

        // Send a fresh item and check receive
        val artemisMessage = artemis.session.createMessage(true).apply {
            putIntProperty(P2PMessagingHeaders.senderUUID, 3)
            writeBodyBufferBytes("Test3".toByteArray())
            // Use the magic deduplication property built into Artemis as our message identity too
            putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
        }
        artemis.producer.send(sourceQueueName, artemisMessage)


        // start receiving again, discarding duplicates
        while (true) {
            val received5 = receive.next()
            val messageID5 = received5.applicationProperties[P2PMessagingHeaders.senderUUID.toString()] as Int
            assertArrayEquals("Test$messageID5".toByteArray(), received5.payload)
            receivedSequence += messageID5
            val messageId = received5.applicationProperties[HDR_DUPLICATE_DETECTION_ID.toString()] as String
            if (messageId !in dedupeSet) {
                dedupeSet += messageId
                atNodeSequence += messageID5
            }
            received5.complete(true)
            if (messageID5 == 3) { // reached our fresh message
                break
            }
        }

        log.info("Message sequence: ${receivedSequence.joinToString(", ", "[", "]")}")
        log.info("Deduped sequence: ${atNodeSequence.joinToString(", ", "[", "]")}")
        assertEquals(listOf(0, 1, 2, 3), atNodeSequence)
        bridgeManager.stop()
        amqpServer.stop()
        artemisClient.stop()
        artemisServer.stop()
    }

    private fun createArtemis(sourceQueueName: String?): Triple<ArtemisMessagingServer, ArtemisMessagingClient, BridgeManager> {
        val baseDir = temporaryFolder.root.toPath() / "artemis"
        val certificatesDirectory = baseDir / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDir).whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(true).whenever(it).crlCheckSoftFail
            doReturn(artemisAddress).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
        }
        artemisConfig.configureWithDevSSLCertificate()
        val artemisServer = ArtemisMessagingServer(artemisConfig, artemisAddress.copy(host = "0.0.0.0"), MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig.p2pSslOptions, artemisAddress, MAX_MESSAGE_SIZE)
        artemisServer.start()
        artemisClient.start()
        val bridgeManager = AMQPBridgeManager(artemisConfig.p2pSslOptions, artemisAddress, MAX_MESSAGE_SIZE)
        bridgeManager.start()
        val artemis = artemisClient.started!!
        if (sourceQueueName != null) {
            // Local queue for outgoing messages
            artemis.session.createQueue(sourceQueueName, RoutingType.ANYCAST, sourceQueueName, true)
            bridgeManager.deployBridge(sourceQueueName, listOf(amqpAddress), setOf(BOB.name))
        }
        return Triple(artemisServer, artemisClient, bridgeManager)
    }

    private fun createAMQPServer(maxMessageSize: Int = MAX_MESSAGE_SIZE): AMQPServer {
        val baseDir = temporaryFolder.root.toPath() / "server"
        val certificatesDirectory = baseDir / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "server").whenever(it).baseDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
        }
        serverConfig.configureWithDevSSLCertificate()

        val keyStore = serverConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore  = serverConfig.p2pSslOptions.trustStore.get()
            override val trace: Boolean = true
            override val maxMessageSize: Int = maxMessageSize
        }
        return AMQPServer("0.0.0.0",
                amqpAddress.port,
                amqpConfig
        )
    }
}