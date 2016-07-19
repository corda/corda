@file:Suppress("UNUSED_VARIABLE")

package com.r3corda.node.messaging

import com.r3corda.core.messaging.Message
import com.r3corda.core.messaging.TopicStringValidator
import com.r3corda.node.internal.testing.MockNetwork
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class InMemoryMessagingTests {
    lateinit var network: MockNetwork

    init {
        // BriefLogFormatter.initVerbose()
    }

    @Before
    fun setUp() {
        network = MockNetwork()
    }

    @Test
    fun topicStringValidation() {
        TopicStringValidator.check("this.is.ok")
        TopicStringValidator.check("this.is.OkAlso")
        assertFails {
            TopicStringValidator.check("this.is.not-ok")
        }
        assertFails {
            TopicStringValidator.check("")
        }
        assertFails {
            TopicStringValidator.check("this.is not ok")   // Spaces
        }
    }

    @Test
    fun basics() {
        val node1 = network.createNode()
        val node2 = network.createNode()
        val node3 = network.createNode()

        val bits = "test-content".toByteArray()
        var finalDelivery: Message? = null

        with(node2) {
            node2.net.addMessageHandler { msg, registration ->
                node2.net.send(msg, node3.info.address)
            }
        }

        with(node3) {
            node2.net.addMessageHandler { msg, registration ->
                finalDelivery = msg
            }
        }

        // Node 1 sends a message and it should end up in finalDelivery, after we run the network
        node1.net.send(node1.net.createMessage("test.topic", bits), node2.info.address)

        network.runNetwork(rounds = 1)

        assertTrue(Arrays.equals(finalDelivery!!.data, bits))
    }

    @Test
    fun broadcast() {
        val node1 = network.createNode()
        val node2 = network.createNode()
        val node3 = network.createNode()

        val bits = "test-content".toByteArray()

        var counter = 0
        listOf(node1, node2, node3).forEach { it.net.addMessageHandler { msg, registration -> counter++ } }
        node1.net.send(node2.net.createMessage("test.topic", bits), network.messagingNetwork.everyoneOnline)
        network.runNetwork(rounds = 1)
        assertEquals(3, counter)
    }

    /**
     * Tests that unhandled messages in the received queue are skipped and the next message processed, rather than
     * causing processing to return null as if there was no message.
     */
    @Test
    fun `skip unhandled messages`() {
        val node1 = network.createNode()
        val node2 = network.createNode()
        var received: Int = 0

        node1.net.addMessageHandler("valid_message") { msg, reg ->
            received++
        }

        val invalidMessage = node2.net.createMessage("invalid_message", ByteArray(0))
        val validMessage = node2.net.createMessage("valid_message", ByteArray(0))
        node2.net.send(invalidMessage, node1.net.myAddress)
        network.runNetwork()
        assertEquals(0, received)

        node2.net.send(validMessage, node1.net.myAddress)
        network.runNetwork()
        assertEquals(1, received)

        // Here's the core of the test; previously the unhandled message would cause runNetwork() to abort early, so
        // this would fail.
        node2.net.send(invalidMessage, node1.net.myAddress)
        node2.net.send(validMessage, node1.net.myAddress)
        network.runNetwork()
        assertEquals(2, received)

    }
}
