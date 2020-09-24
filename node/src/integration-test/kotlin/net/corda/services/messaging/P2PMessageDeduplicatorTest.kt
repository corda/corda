package net.corda.services.messaging

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.ByteSequence
import net.corda.node.services.messaging.MessageIdentifier
import net.corda.node.services.messaging.P2PMessageDeduplicator
import net.corda.node.services.messaging.ReceivedMessage
import net.corda.node.services.messaging.generateShardId
import net.corda.node.services.statemachine.MessageType
import net.corda.node.services.statemachine.SessionId
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.time.Instant

class P2PMessageDeduplicatorTest {

    companion object {
        private const val TOPIC = "whatever"
        private val DATA = ByteSequence.of("blah blah blah".toByteArray())
        private val SHARD_ID = generateShardId("some-flow-id")
        private val SESSION_ID = SessionId(BigInteger.ONE)
        private val TIMESTAMP = Instant.now()
        private val SENDER = CordaX500Name("CordaWorld", "The Sea Devil", "NeverLand", "NL")
        private const val SENDER_UUID = "some-sender-uuid"
        private const val PLATFORM_VERSION = 42

        private const val FIRST_SENDER_SEQ_NO = 10L
        private const val LAST_SENDER_SEQ_NO = 35L
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private lateinit var database: CordaPersistence
    private lateinit var deduplicator: P2PMessageDeduplicator


    @Before
    fun setUp() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProps, DatabaseConfig(), { null }, { null }, runMigrationScripts = true, allowHibernateToManageAppSchema = false)
        deduplicator = P2PMessageDeduplicator(TestingNamedCacheFactory(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test(timeout=300_000)
    fun `correctly deduplicates a session-init message`() {
        val msgId = MessageIdentifier(MessageType.SESSION_INIT, SHARD_ID, SESSION_ID, 0, TIMESTAMP)
        val receivedMessage = createMessage(msgId, FIRST_SENDER_SEQ_NO)

        assertThat(deduplicator.isDuplicateSessionInit(receivedMessage)).isFalse()

        processMessage(receivedMessage)

        assertThat(deduplicator.isDuplicateSessionInit(receivedMessage)).isTrue()
    }

    @Test(timeout=300_000)
    fun `fails when requested to deduplicate a non session-init message`() {
        val msgId = MessageIdentifier(MessageType.DATA_MESSAGE, SHARD_ID, SESSION_ID, 3, TIMESTAMP)
        val receivedMessage = createMessage(msgId, 25)

        assertThatThrownBy { deduplicator.isDuplicateSessionInit(receivedMessage) }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("was not a session-init message")
    }

    @Test(timeout=300_000)
    fun `updates session data correctly when session is completed`() {
        val msgId = MessageIdentifier(MessageType.SESSION_INIT, SHARD_ID, SESSION_ID, 0, TIMESTAMP)
        val sessionInitMessage = createMessage(msgId, FIRST_SENDER_SEQ_NO)

        processMessage(sessionInitMessage)

        val sessionDataAfterSessionInit = database.transaction {
            entityManager.find(P2PMessageDeduplicator.SessionData::class.java, SESSION_ID.value)
        }
        assertThat(sessionDataAfterSessionInit.firstSenderSeqNo).isEqualTo(FIRST_SENDER_SEQ_NO)
        assertThat(sessionDataAfterSessionInit.lastSenderSeqNo).isNull()
        assertThat(sessionDataAfterSessionInit.generationTime).isEqualTo(TIMESTAMP)

        database.transaction {
            deduplicator.signalSessionEnd(SESSION_ID, SENDER_UUID, LAST_SENDER_SEQ_NO)
        }

        val sessionDataAfterSessionEnd = database.transaction {
            entityManager.find(P2PMessageDeduplicator.SessionData::class.java, SESSION_ID.value)
        }
        assertThat(sessionDataAfterSessionEnd.firstSenderSeqNo).isEqualTo(FIRST_SENDER_SEQ_NO)
        assertThat(sessionDataAfterSessionEnd.lastSenderSeqNo).isEqualTo(LAST_SENDER_SEQ_NO)
        assertThat(sessionDataAfterSessionEnd.generationTime).isEqualTo(TIMESTAMP)
    }

    private fun processMessage(receivedMessage: ReceivedMessage) {
        deduplicator.isDuplicateSessionInit(receivedMessage)

        deduplicator.signalMessageProcessStart(receivedMessage)
        database.transaction {
            deduplicator.persistDeduplicationId(receivedMessage.uniqueMessageId)
        }
        deduplicator.signalMessageProcessFinish(receivedMessage.uniqueMessageId)
    }

    private fun createMessage(msgId: MessageIdentifier, senderSeqNo: Long?): ReceivedMessage {
        return MockReceivedMessage(TOPIC, DATA, TIMESTAMP, msgId, SENDER_UUID, emptyMap(), SENDER, PLATFORM_VERSION, senderSeqNo, msgId.messageType == MessageType.SESSION_INIT)
    }

    data class MockReceivedMessage(override val topic: String,
                                   override val data: ByteSequence,
                                   override val debugTimestamp: Instant,
                                   override val uniqueMessageId: MessageIdentifier,
                                   override val senderUUID: String?,
                                   override val additionalHeaders: Map<String, String>,
                                   override val peer: CordaX500Name,
                                   override val platformVersion: Int,
                                   override val senderSeqNo: Long?,
                                   override val isSessionInit: Boolean): ReceivedMessage
}