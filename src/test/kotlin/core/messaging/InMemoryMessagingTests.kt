/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

@file:Suppress("UNUSED_VARIABLE")

package core.messaging

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class InMemoryMessagingTests {
    val nodes: MutableMap<SingleMessageRecipient, InMemoryNetwork.Node> = HashMap()
    lateinit var network: InMemoryNetwork

    init {
        // BriefLogFormatter.initVerbose()
    }

    fun makeNode(): Pair<SingleMessageRecipient, InMemoryNetwork.Node> {
        // The manuallyPumped = true bit means that we must call the pump method on the system in order to
        val (address, builder) = network.createNode(manuallyPumped = true)
        val node = builder.start().get()
        nodes[address] = node
        return Pair(address, node)
    }

    fun pumpAll() {
        nodes.values.forEach { it.pump(false) }
    }

    // Utilities to help define messaging rounds.
    fun roundWithPumpings(times: Int, body: () -> Unit) {
        body()
        repeat(times) { pumpAll() }
    }

    fun round(body: () -> Unit) = roundWithPumpings(1, body)

    @Before
    fun before() {
        network = InMemoryNetwork()
        nodes.clear()
    }

    @After
    fun after() {
        network.stop()
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
            addMessageHandler {
                send(it, addr3)
            }
        }

        with(node3) {
            addMessageHandler {
                finalDelivery = it
            }
        }

        // Node 1 sends a message and it should end up in finalDelivery, after we pump each node.
        roundWithPumpings(2) {
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
        listOf(node1, node2, node3).forEach { it.addMessageHandler { counter++ } }
        round {
            node1.send(node2.createMessage("test.topic", bits), network.entireNetwork)
        }
        assertEquals(3, counter)
    }
}