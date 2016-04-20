@file:Suppress("UNUSED_VARIABLE")

package core.messaging

import core.serialization.deserialize
import core.testing.MockNetwork
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

    @Test
    fun downAndUp() {
        // Test (re)delivery of messages to nodes that aren't created yet, or were stopped and then restarted.
        // The purpose of this functionality is to simulate a reliable messaging system that keeps trying until
        // messages are delivered.
        val node1 = network.createNode()
        var node2 = network.createNode()

        node1.net.send("test.topic", node2.info.address, "hello!")
        network.runNetwork(rounds = 1)   // No handler registered, so the message goes into a holding area.
        var runCount = 0
        node2.net.addMessageHandler("test.topic") { msg, registration ->
            if (msg.data.deserialize<String>() == "hello!")
                runCount++
        }
        network.runNetwork(rounds = 1)  // Try again now the handler is registered
        assertEquals(1, runCount)

        // Shut node2 down for a while. Node 1 keeps sending it messages though.
        node2.stop()

        node1.net.send("test.topic", node2.info.address, "are you there?")
        node1.net.send("test.topic", node2.info.address, "wake up!")

        // Now re-create node2 with the same address as last time, and re-register a message handler.
        // Check that the messages that were sent whilst it was gone are still there, waiting for it.
        node2 = network.createNode(null, node2.id)
        node2.net.addMessageHandler("test.topic") { a, b -> runCount++ }
        network.runNetwork(rounds = 1)
        assertEquals(2, runCount)
        network.runNetwork(rounds = 1)
        assertEquals(3, runCount)
        network.runNetwork(rounds = 1)
        assertEquals(3, runCount)
    }
}
