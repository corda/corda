/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

@file:Suppress("UNUSED_VARIABLE")

package core.messaging

import core.serialization.deserialize
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

open class TestWithInMemoryNetwork {
    val nodes: MutableMap<InMemoryNetwork.Handle, InMemoryNetwork.Node> = HashMap()
    lateinit var network: InMemoryNetwork

    fun makeNode(inBackground: Boolean = false): Pair<InMemoryNetwork.Handle, InMemoryNetwork.Node> {
        // The manuallyPumped = true bit means that we must call the pump method on the system in order to
        val (address, builder) = network.createNode(!inBackground)
        val node = builder.start().get()
        nodes[address] = node
        return Pair(address, node)
    }

    @Before
    fun setupNetwork() {
        network = InMemoryNetwork()
        nodes.clear()
    }

    @After
    fun stopNetwork() {
        network.stop()
    }

    fun pumpAll(blocking: Boolean) = network.nodes.map { it.pump(blocking) }

    // Keep calling "pump" in rounds until every node in the network reports that it had nothing to do
    fun <T> runNetwork(body: () -> T): T {
        val result = body()
        while (pumpAll(false).any { it }) {}
        return result
    }
}

class InMemoryMessagingTests : TestWithInMemoryNetwork() {
    init {
        // BriefLogFormatter.initVerbose()
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
        val (addr1, node1) = makeNode()
        val (addr2, node2) = makeNode()
        val (addr3, node3) = makeNode()

        val bits = "test-content".toByteArray()
        var finalDelivery: Message? = null

        with(node2) {
            addMessageHandler { msg, registration ->
                send(msg, addr3)
            }
        }

        with(node3) {
            addMessageHandler { msg, registration ->
                finalDelivery = msg
            }
        }

        // Node 1 sends a message and it should end up in finalDelivery, after we pump each node.
        runNetwork {
            node1.send(node1.createMessage("test.topic", bits), addr2)
        }

        assertTrue(Arrays.equals(finalDelivery!!.data, bits))
    }

    @Test
    fun broadcast() {
        val (addr1, node1) = makeNode()
        val (addr2, node2) = makeNode()
        val (addr3, node3) = makeNode()

        val bits = "test-content".toByteArray()

        var counter = 0
        listOf(node1, node2, node3).forEach { it.addMessageHandler { msg, registration -> counter++ } }
        runNetwork {
            node1.send(node2.createMessage("test.topic", bits), network.everyoneOnline)
        }
        assertEquals(3, counter)
    }

    @Test
    fun downAndUp() {
        // Test (re)delivery of messages to nodes that aren't created yet, or were stopped and then restarted.
        // The purpose of this functionality is to simulate a reliable messaging system that keeps trying until
        // messages are delivered.
        val (addr1, node1) = makeNode()
        var (addr2, node2) = makeNode()

        node1.send("test.topic", addr2, "hello!")
        node2.pump(false)   // No handler registered, so the message goes into a holding area.
        var runCount = 0
        node2.addMessageHandler("test.topic") { msg, registration ->
            if (msg.data.deserialize<String>() == "hello!")
                runCount++
        }
        node2.pump(false)  // Try again now the handler is registered
        assertEquals(1, runCount)

        // Shut node2 down for a while. Node 1 keeps sending it messages though.
        node2.stop()

        node1.send("test.topic", addr2, "are you there?")
        node1.send("test.topic", addr2, "wake up!")

        // Now re-create node2 with the same address as last time, and re-register a message handler.
        // Check that the messages that were sent whilst it was gone are still there, waiting for it.
        node2 = network.createNodeWithID(true, addr2.id).start().get()
        node2.addMessageHandler("test.topic") { a, b -> runCount++ }
        assertTrue(node2.pump(false))
        assertEquals(2, runCount)
        assertTrue(node2.pump(false))
        assertEquals(3, runCount)
        assertFalse(node2.pump(false))
        assertEquals(3, runCount)
    }
}