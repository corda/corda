package net.corda.node.services.statemachine

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant

class MessageIdentifierTest {

    private val expectedIdentifierFormat = "XC-00000000000F4240-F40C8848-000000000000060A-1"
    private val prefix = "XC"
    private val shardId = "F40C8848"
    private val flowId = "some-flow-id"
    private val sessionId = 1546L
    private val sequenceNumber = 1
    private val timestamp = Instant.ofEpochSecond(1_000)

    @Test(timeout=300_000)
    fun `test identifier to string`() {
        val identifier = MessageIdentifier(prefix, generateShard(flowId), sessionId, sequenceNumber, timestamp)

        assertThat(identifier.toString()).isEqualTo(expectedIdentifierFormat)
    }

    @Test(timeout=300_000)
    fun `test identifier parsing from string`() {
        val identifier = MessageIdentifier.parse(expectedIdentifierFormat)

        assertThat(identifier.prefix).isEqualTo(prefix)
        assertThat(identifier.shardIdentifier).isEqualTo(shardId)
        assertThat(identifier.sessionIdentifier).isEqualTo(sessionId)
        assertThat(identifier.sessionSequenceNumber).isEqualTo(sequenceNumber)
        assertThat(identifier.timestamp).isEqualTo(timestamp)
    }
}