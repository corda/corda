package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.div
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import net.corda.nodeapi.internal.bridging.BridgeManager
import net.corda.nodeapi.internal.bridging.LoopbackBridgeManager
import net.corda.nodeapi.internal.bridging.payload
import net.corda.nodeapi.internal.protonwrapper.netty.toRevocationConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class LoopbackBridgeTest(private val useOpenSsl: Boolean) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "useOpenSsl = {0}")
        fun data(): Collection<Boolean> = listOf(false, true)
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()
    private val log = loggerFor<LoopbackBridgeTest>()
    private val BOB = TestIdentity(BOB_NAME)
    private val portAllocation = incrementalPortAllocation(10000)
    private val artemisAddress = portAllocation.nextHostAndPort()
    private val amqpAddress = portAllocation.nextHostAndPort()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `test acked and nacked messages`() {
        // Create local queue
        val sourceQueueName = "internal.peers." + BOB.publicKey.toStringShort()
        val (artemisServer, artemisClient, bridgeManager) = createArtemis(sourceQueueName)
        val artemis = artemisClient.started!!

        //Create target artemis inbox
        val queueName = "p2p.inbound.${BOB.publicKey.toStringShort()}"
        artemis.session.createQueue(queueName, RoutingType.ANYCAST, queueName, null, true, false,
                ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(),
                ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), true, null)

        // Pre-populate local queue with 3 messages
        for (i in 0 until 3) {
            val artemisMessage = artemis.session.createMessage(true).apply {
                putStringProperty(P2PMessagingHeaders.bridgedCertificateSubject, ALICE_NAME.toString())
                putIntProperty(P2PMessagingHeaders.senderUUID, i)
                writeBodyBufferBytes("Test$i".toByteArray())
                // Use the magic deduplication property built into Artemis as our message identity too
                putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
            }
            artemis.producer.send(sourceQueueName, artemisMessage)
        }

        var consumer = artemis.session.createConsumer(queueName)

        val dedupeSet = mutableSetOf<String>()
        val receivedSequence = mutableListOf<Int>()
        val atNodeSequence = mutableListOf<Int>()

        fun formatMessage(expected: String, actual: Int, received: List<Int>): String {
            return "Expected message with id $expected, got $actual, previous message receive sequence: $received."
        }

        val received1 = consumer!!.receive(1000)
        val messageID1 = received1.getIntProperty(P2PMessagingHeaders.senderUUID)
        assertArrayEquals("Test$messageID1".toByteArray(), received1.payload())
        assertEquals(0, messageID1)
        dedupeSet += received1.getStringProperty(HDR_DUPLICATE_DETECTION_ID)
        received1.acknowledge() // Accept first message
        receivedSequence += messageID1
        atNodeSequence += messageID1

        val received2 = consumer.receive(1000)
        val messageID2 = received2.getIntProperty(P2PMessagingHeaders.senderUUID)
        assertArrayEquals("Test$messageID2".toByteArray(), received2.payload())
        assertEquals(1, messageID2, formatMessage("1", messageID2, receivedSequence))
        consumer.close() // Reject message and don't add to dedupe
        consumer = artemis.session.createConsumer(queueName)
        receivedSequence += messageID2 // reflects actual sequence

        // drop things until we get back to the replay
        while (true) {
            val received3 = consumer!!.receive(1000)
            val messageID3 = received3.getIntProperty(P2PMessagingHeaders.senderUUID)
            assertArrayEquals("Test$messageID3".toByteArray(), received3.payload())
            receivedSequence += messageID3
            if (messageID3 != 1) { // keep rejecting any batched items following rejection
                consumer.close() // Reject message and don't add to dedupe
                consumer = artemis.session.createConsumer(queueName)
            } else { // beginnings of replay so accept again
                received3.acknowledge()
                val messageId = received3.getStringProperty(HDR_DUPLICATE_DETECTION_ID)
                if (messageId !in dedupeSet) {
                    dedupeSet += messageId
                    atNodeSequence += messageID3
                }
                break
            }
        }

        // start receiving again, but discarding duplicates
        while (true) {
            val received4 = consumer!!.receive(1000)
            val messageID4 = received4.getIntProperty(P2PMessagingHeaders.senderUUID)
            assertArrayEquals("Test$messageID4".toByteArray(), received4.payload())
            receivedSequence += messageID4
            val messageId = received4.getStringProperty(HDR_DUPLICATE_DETECTION_ID)
            if (messageId !in dedupeSet) {
                dedupeSet += messageId
                atNodeSequence += messageID4
            }
            received4.acknowledge()
            if (messageID4 == 2) { // started to replay messages after rejection point
                break
            }
        }

        // Send a fresh item and check receive
        val artemisMessage = artemis.session.createMessage(true).apply {
            putStringProperty(P2PMessagingHeaders.bridgedCertificateSubject, ALICE_NAME.toString())
            putIntProperty(P2PMessagingHeaders.senderUUID, 3)
            writeBodyBufferBytes("Test3".toByteArray())
            // Use the magic deduplication property built into Artemis as our message identity too
            putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
        }
        artemis.producer.send(sourceQueueName, artemisMessage)


        // start receiving again, discarding duplicates
        while (true) {
            val received5 = consumer.receive(1000)
            val messageID5 = received5.getIntProperty(P2PMessagingHeaders.senderUUID)
            assertArrayEquals("Test$messageID5".toByteArray(), received5.payload())
            receivedSequence += messageID5
            val messageId = received5.getStringProperty(HDR_DUPLICATE_DETECTION_ID)
            if (messageId !in dedupeSet) {
                dedupeSet += messageId
                atNodeSequence += messageID5
            }
            received5.acknowledge()
            if (messageID5 == 3) { // reached our fresh message
                break
            }
        }

        log.info("Message sequence: $receivedSequence")
        log.info("Deduped sequence: $atNodeSequence")
        assertEquals(listOf(0, 1, 2, 3), atNodeSequence)
        consumer.close()
        bridgeManager.stop()
        artemisClient.stop()
        artemisServer.stop()
    }

    private fun createArtemis(sourceQueueName: String?): Triple<ArtemisMessagingServer, ArtemisMessagingClient, BridgeManager> {
        val baseDir = temporaryFolder.root.toPath() / "artemis"
        val certificatesDirectory = baseDir / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, useOpenSsl = useOpenSsl)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDir).whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(true).whenever(it).crlCheckSoftFail
            doReturn(artemisAddress).whenever(it).p2pAddress
            doReturn(true).whenever(it).enableSNI
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000))).whenever(it).enterpriseConfiguration
        }
        artemisConfig.configureWithDevSSLCertificate()

        val artemisServer = ArtemisMessagingServer(artemisConfig, artemisAddress.copy(host = "0.0.0.0"), MAX_MESSAGE_SIZE)

        val artemisClient = ArtemisMessagingClient(artemisConfig.p2pSslOptions, artemisAddress, MAX_MESSAGE_SIZE, confirmationWindowSize = artemisConfig.enterpriseConfiguration.tuning.p2pConfirmationWindowSize)

        artemisServer.start()
        artemisClient.start()
        val bridgeManager = LoopbackBridgeManager(artemisConfig.p2pSslOptions.keyStore.get(),
                artemisConfig.p2pSslOptions.trustStore.get(),
                artemisConfig.p2pSslOptions.useOpenSsl,
                null,
                MAX_MESSAGE_SIZE,
                artemisConfig.crlCheckSoftFail.toRevocationConfig(),
                artemisConfig.enableSNI,
                { ArtemisMessagingClient(artemisConfig.p2pSslOptions, artemisAddress, MAX_MESSAGE_SIZE, confirmationWindowSize = artemisConfig.enterpriseConfiguration.tuning.p2pConfirmationWindowSize) },
                null,
                { true },
                false)
        bridgeManager.start()

        val artemis = artemisClient.started!!
        if (sourceQueueName != null) {
            // Local queue for outgoing messages
            artemis.session.createQueue(sourceQueueName, RoutingType.ANYCAST, sourceQueueName, true)
            bridgeManager.deployBridge(ALICE_NAME.toString(), sourceQueueName, listOf(amqpAddress), setOf(BOB.name))
        }
        return Triple(artemisServer, artemisClient, bridgeManager)
    }
}