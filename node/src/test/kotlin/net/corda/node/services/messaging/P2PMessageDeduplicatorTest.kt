package net.corda.node.services.messaging

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.elapsedTime
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.days
import net.corda.core.utilities.minutes
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.node.utilities.NodeJanitor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.SecureRandom
import java.time.Clock
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P2PMessageDeduplicatorTest {
    companion object {
        private val COUNT_QUERY = "SELECT COUNT(m) FROM ${P2PMessageDeduplicator.ProcessedMessage::class.java.name} m"
        private val log = contextLogger()
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val retainPerSender = 50
    private val retainForDays = 1

    private lateinit var database: CordaPersistence
    private lateinit var deduplicator: P2PMessageDeduplicator
    private lateinit var testClock: TestClock
    private val random = SecureRandom()

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeJanitor::class)
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProps, DatabaseConfig(runMigration = false), { null }, { null })
        testClock = TestClock(Clock.systemUTC())
        deduplicator = P2PMessageDeduplicator(TestingNamedCacheFactory(), database, testClock)
    }

    @After
    fun tearDown() {
        database.close()
        LogHelper.reset(NodeJanitor::class)
    }

    @Test
    fun `correctly deduplicates a message`() {
        val receivedMessage = generateMessage()
        assertFalse(deduplicator.isDuplicate(receivedMessage))

        processMessage(receivedMessage)

        assertTrue(deduplicator.isDuplicate(receivedMessage))
    }

    @Test
    fun `correctly deduplicates a message with a different sending peer`() {
        val receivedMessage = generateMessage()
        assertFalse(deduplicator.isDuplicate(receivedMessage))

        processMessage(receivedMessage)

        assertTrue(deduplicator.isDuplicate(receivedMessage))

        val receivedFromDifferentPeer = generateMessage(uniqueMessageId = receivedMessage.uniqueMessageId, senderUUID = receivedMessage.senderUUID, senderSeqNo = receivedMessage.senderSeqNo)
        assertTrue(deduplicator.isDuplicate(receivedFromDifferentPeer))
    }

    @Test
    fun `cleanup handles empty table`() {
        NodeJanitor.cleanUpProcessedMessages(database, testClock, retainForDays, retainPerSender)
    }

    @Test
    fun `cleanup correctly removes old processed messages`() {
        val senderCount = 10
        val messagePerSender = 100

        // Fill table with messages - part 1
        fillMessages(senderCount, messagePerSender)
        assertEquals(senderCount * messagePerSender, getRowCount())
        // Advance clock so the messages expire
        testClock.advanceBy(retainForDays.days + 1.minutes)

        // Fill table with messages - part 2
        fillMessages(senderCount, messagePerSender)
        // Fill table with null sender messages
        fillNullSenderMessages(messagePerSender)
        assertEquals(senderCount * messagePerSender * 2 + messagePerSender, getRowCount())

        // Clean up old messages. All messages from part 1 should be removed + (messagePerSender - retainPerSender) from part 2.
        val duration = elapsedTime {
            NodeJanitor.cleanUpProcessedMessages(database, testClock, retainForDays, retainPerSender)
        }
        log.info("Cleaning up messages: $duration")

        // Expecting only [retainPerSender] messages per sender to remain + the null sender messages
        assertEquals(senderCount * retainPerSender + messagePerSender, getRowCount())
    }

    @Test
    fun `cleanup retains the right amount of messages per sender`() {
        val messageCount = 100
        val retainPerSender = 50

        // Process [messageCount] messages for a single sender
        val senderName = generateName()
        val senderUUID = UUID.randomUUID()
        val messages = (1..messageCount).map {sequenceNo ->
            val msg = generateMessage(senderSeqNo = sequenceNo.toLong(), peer = senderName, senderUUID = senderUUID.toString())
            processMessage(msg)
            msg
        }

        // Should remove all except [retainPerSender] messages
        NodeJanitor.cleanUpProcessedMessages(database, testClock, retainForDays, retainPerSender)

        // Make sure the last [retainPerSender] messages are correctly deduplicated
        val latestMessages = messages.drop(messageCount - retainPerSender)
        latestMessages.forEach { msg ->
            assertTrue(deduplicator.isDuplicate(msg))
        }
    }

    private fun getRowCount(): Int {
        val rowCount = database.transaction {
            session.createQuery(COUNT_QUERY).singleResult as Long
        }
        return rowCount.toInt()
    }

    private fun fillMessages(senderCount: Int, messagePerSender: Int) {
        val duration = elapsedTime {
            val senders = (1..senderCount).map { generateName() }

            senders.forEach { senderName ->
                val senderUUID = UUID.randomUUID()
                for (sequenceNo in (1..messagePerSender)) {
                    val msg = generateMessage(senderSeqNo = sequenceNo.toLong(), peer = senderName, senderUUID = senderUUID.toString())
                    processMessage(msg)
                }
            }
        }
        log.info("Filling ${senderCount * messagePerSender} messages: $duration")
    }

    private fun fillNullSenderMessages(messageCount: Int) {
        for (i in 1..messageCount) {
            val msg = generateMessage(senderSeqNo = null, senderUUID = null)
            processMessage(msg)
        }
    }

    private fun processMessage(receivedMessage: ReceivedMessage) {
        deduplicator.isDuplicate(receivedMessage)

        deduplicator.signalMessageProcessStart(receivedMessage)
        database.transaction {
            deduplicator.persistDeduplicationId(receivedMessage.uniqueMessageId)
        }
        deduplicator.signalMessageProcessFinish(receivedMessage.uniqueMessageId)
    }

    private fun generateMessage(
            uniqueMessageId: DeduplicationId = DeduplicationId.createRandom(random),
            senderUUID: String? = UUID.randomUUID().toString(),
            senderSeqNo: Long? = random.nextLong(),
            peer: CordaX500Name = generateName()
    ): ReceivedMessage {
        val msg = mock<ReceivedMessage>()
        whenever(msg.isSessionInit).doReturn(false)
        whenever(msg.uniqueMessageId).doReturn(uniqueMessageId)
        whenever(msg.senderUUID).doReturn(senderUUID)
        whenever(msg.senderSeqNo).doReturn(senderSeqNo)
        whenever(msg.peer).doReturn(peer)
        return msg
    }

    private fun generateName(): CordaX500Name = CordaX500Name("Bank " + random.nextInt(), "London", "GB")
}