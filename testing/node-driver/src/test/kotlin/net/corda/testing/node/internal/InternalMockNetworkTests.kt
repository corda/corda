package net.corda.testing.node.internal

import net.corda.core.messaging.AllPossibleRecipients
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.node.services.messaging.Message
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InternalMockNetworkTests {
    lateinit var mockNet: InternalMockNetwork

    @After
    fun tearDown() {
        if (this::mockNet.isInitialized) {
            mockNet.stopNodes()
        }
    }

    @Test
    fun basics() {
        mockNet = InternalMockNetwork()

        val node1 = mockNet.createNode()
        val node2 = mockNet.createNode()
        val node3 = mockNet.createNode()

        val bits = "test-content".toByteArray()
        var finalDelivery: Message? = null
        node2.network.addMessageHandler("test.topic") { msg, _, _ ->
            node2.network.send(msg, node3.network.myAddress)
        }
        node3.network.addMessageHandler("test.topic") { msg, _, _ ->
            finalDelivery = msg
        }

        // Node 1 sends a message and it should end up in finalDelivery, after we run the network
        node1.network.send(node1.network.createMessage("test.topic", data = bits), node2.network.myAddress)

        mockNet.runNetwork(rounds = 1)

        assertTrue(Arrays.equals(finalDelivery!!.data.bytes, bits))
    }

    @Test
    fun broadcast() {
        mockNet = InternalMockNetwork()

        val node1 = mockNet.createNode()
        val node2 = mockNet.createNode()
        val node3 = mockNet.createNode()

        val bits = "test-content".toByteArray()

        var counter = 0
        listOf(node1, node2, node3).forEach { it.network.addMessageHandler("test.topic") { _, _, _ -> counter++ } }
        node1.network.send(node2.network.createMessage("test.topic", data = bits), rigorousMock<AllPossibleRecipients>())
        mockNet.runNetwork(rounds = 1)
        assertEquals(3, counter)
    }

    /**
     * Tests that unhandled messages in the received queue are skipped and the next message processed, rather than
     * causing processing to return null as if there was no message.
     */
    @Test
    fun `skip unhandled messages`() {
        mockNet = InternalMockNetwork()

        val node1 = mockNet.createNode()
        val node2 = mockNet.createNode()
        var received = 0

        node1.network.addMessageHandler("valid_message") { _, _, _ ->
            received++
        }

        val invalidMessage = node2.network.createMessage("invalid_message", data = ByteArray(1))
        val validMessage = node2.network.createMessage("valid_message", data = ByteArray(1))
        node2.network.send(invalidMessage, node1.network.myAddress)
        mockNet.runNetwork()
        assertEquals(0, received)

        node2.network.send(validMessage, node1.network.myAddress)
        mockNet.runNetwork()
        assertEquals(1, received)

        // Here's the core of the test; previously the unhandled message would cause runNetwork() to abort early, so
        // this would fail. Make fresh messages to stop duplicate uniqueMessageId causing drops
        val invalidMessage2 = node2.network.createMessage("invalid_message", data = ByteArray(1))
        val validMessage2 = node2.network.createMessage("valid_message", data = ByteArray(1))
        node2.network.send(invalidMessage2, node1.network.myAddress)
        node2.network.send(validMessage2, node1.network.myAddress)
        mockNet.runNetwork()
        assertEquals(2, received)
    }

    @Test
    fun `does not leak serialization env if init fails`() {
        val e = Exception("didn't work")
        assertThatThrownBy {
            object : InternalMockNetwork(cordappsForAllNodes = emptySet()) {
                override fun createNotaries() = throw e
            }
        }.isSameAs(e)
        assertThatThrownBy { effectiveSerializationEnv }.isInstanceOf(IllegalStateException::class.java)
    }
}
