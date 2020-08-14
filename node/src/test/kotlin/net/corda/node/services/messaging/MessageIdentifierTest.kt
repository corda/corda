package net.corda.node.services.messaging

import net.corda.node.services.statemachine.MessageType
import net.corda.node.services.statemachine.SessionId
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.time.Instant

class MessageIdentifierTest {

    private val shardIdentifier = "XXXXXXXX"
    private val sessionIdentifier = SessionId(BigInteger.valueOf(14))
    private val sessionSequenceNumber = 1
    private val timestamp = Instant.ofEpochMilli(100)

    private val messageIdString = "XI-0000000000000064-XXXXXXXX-0000000000000000000000000000000E-1"

    @Test(timeout=300_000)
    fun `can parse message identifier from string value`() {
        val messageIdentifier = MessageIdentifier(MessageType.SESSION_INIT, shardIdentifier, sessionIdentifier, sessionSequenceNumber, timestamp)

        assertThat(messageIdentifier.toString()).isEqualTo(messageIdString)
    }

    @Test(timeout=300_000)
    fun `can convert message identifier object to string value`() {
        val messageIdentifierString = messageIdString

        val messageIdentifier = MessageIdentifier.parse(messageIdentifierString)
        assertThat(messageIdentifier.messageType).isInstanceOf(MessageType.SESSION_INIT::class.java)
        assertThat(messageIdentifier.shardIdentifier).isEqualTo(shardIdentifier)
        assertThat(messageIdentifier.sessionIdentifier).isEqualTo(sessionIdentifier)
        assertThat(messageIdentifier.sessionSequenceNumber).isEqualTo(1)
        assertThat(messageIdentifier.timestamp).isEqualTo(timestamp)
    }

    @Test(timeout=300_000)
    fun `shard identifier needs to be 8 characters long`() {
        Assertions.assertThatThrownBy { MessageIdentifier(MessageType.SESSION_INIT, "XX", sessionIdentifier, 1, timestamp) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Shard identifier needs to be 8 characters long, but it was XX")
    }

}