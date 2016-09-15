package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.testing.connectProtocols
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateMachineManagerTests {

    val net = MockNetwork()
    lateinit var node1: MockNode
    lateinit var node2: MockNode

    @Before
    fun start() {
        val nodes = net.createTwoNodes()
        node1 = nodes.first
        node2 = nodes.second
        net.runNetwork()
    }

    @After
    fun cleanUp() {
        net.stopNodes()
    }

    @Test
    fun `newly added protocol is preserved on restart`() {
        node1.smm.add("test", ProtocolWithoutCheckpoints())
        val restoredProtocol = node1.restartAndGetRestoredProtocol<ProtocolWithoutCheckpoints>()
        assertThat(restoredProtocol.protocolStarted).isTrue()
    }

    @Test
    fun `protocol can lazily use the serviceHub in its constructor`() {
        val protocol = ProtocolWithLazyServiceHub()
        node1.smm.add("test", protocol)
        assertThat(protocol.lazyTime).isNotNull()
    }

    @Test
    fun `protocol suspended just after receiving payload`() {
        val topic = "send-and-receive"
        val payload = random63BitValue()
        val sendProtocol = SendProtocol(topic, node2.info.identity, payload)
        val receiveProtocol = ReceiveProtocol(topic, node1.info.identity)
        connectProtocols(sendProtocol, receiveProtocol)
        node1.smm.add("test", sendProtocol)
        node2.smm.add("test", receiveProtocol)
        net.runNetwork()
        node2.stop()
        val restoredProtocol = node2.restartAndGetRestoredProtocol<ReceiveProtocol>(node1.info.address)
        assertThat(restoredProtocol.receivedPayload).isEqualTo(payload)
    }

    @Test
    fun `protocol added before network map does run after init`() {
        val node3 = net.createNode(node1.info.address) //create vanilla node
        val protocol = ProtocolNoBlocking()
        node3.smm.add("test", protocol)
        assertEquals(false, protocol.protocolStarted) // Not started yet as no network activity has been allowed yet
        net.runNetwork() // Allow network map messages to flow
        assertEquals(true, protocol.protocolStarted) // Now we should have run the protocol
    }

    @Test
    fun `protocol added before network map will be init checkpointed`() {
        var node3 = net.createNode(node1.info.address) //create vanilla node
        val protocol = ProtocolNoBlocking()
        node3.smm.add("test", protocol)
        assertEquals(false, protocol.protocolStarted) // Not started yet as no network activity has been allowed yet
        node3.stop()

        node3 = net.createNode(node1.info.address, forcedID = node3.id)
        val restoredProtocol = node3.smm.findStateMachines(ProtocolNoBlocking::class.java).single().first
        assertEquals(false, restoredProtocol.protocolStarted) // Not started yet as no network activity has been allowed yet
        net.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertEquals(true, restoredProtocol.protocolStarted) // Now we should have run the protocol and hopefully cleared the init checkpoint
        node3.stop()

        // Now it is completed the protocol should leave no Checkpoint.
        node3 = net.createNode(node1.info.address, forcedID = node3.id)
        net.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertTrue(node3.smm.findStateMachines(ProtocolNoBlocking::class.java).isEmpty())
    }

    @Test
    fun `protocol loaded from checkpoint will respond to messages from before start`() {
        val topic = "send-and-receive"
        val payload = random63BitValue()
        val sendProtocol = SendProtocol(topic, node2.info.identity, payload)
        val receiveProtocol = ReceiveProtocol(topic, node1.info.identity)
        connectProtocols(sendProtocol, receiveProtocol)
        node2.smm.add("test", receiveProtocol) // Prepare checkpointed receive protocol
        node2.stop() // kill receiver
        node1.smm.add("test", sendProtocol) // now generate message to spool up and thus come in ahead of messages for NetworkMapService
        val restoredProtocol = node2.restartAndGetRestoredProtocol<ReceiveProtocol>(node1.info.address)
        assertThat(restoredProtocol.receivedPayload).isEqualTo(payload)
    }

    private inline fun <reified P : NonTerminatingProtocol> MockNode.restartAndGetRestoredProtocol(networkMapAddress: SingleMessageRecipient? = null): P {
        val servicesArray = advertisedServices.toTypedArray()
        val node = mockNet.createNode(networkMapAddress, id, advertisedServices = *servicesArray)
        mockNet.runNetwork() // allow NetworkMapService messages to stabilise and thus start the state machine
        return node.smm.findStateMachines(P::class.java).single().first
    }


    private class ProtocolNoBlocking : ProtocolLogic<Unit>() {
        @Transient var protocolStarted = false

        @Suspendable
        override fun call() {
            protocolStarted = true
        }

        override val topic: String get() = throw UnsupportedOperationException()
    }

    private class ProtocolWithoutCheckpoints : NonTerminatingProtocol() {

        @Transient var protocolStarted = false

        @Suspendable
        override fun doCall() {
            protocolStarted = true
        }

        override val topic: String get() = throw UnsupportedOperationException()
    }


    private class ProtocolWithLazyServiceHub : ProtocolLogic<Unit>() {

        val lazyTime by lazy { serviceHub.clock.instant() }

        @Suspendable
        override fun call() {}

        override val topic: String get() = throw UnsupportedOperationException()
    }


    private class SendProtocol(override val topic: String, val otherParty: Party, val payload: Any) : ProtocolLogic<Unit>() {
        @Suspendable
        override fun call() = send(otherParty, payload)
    }


    private class ReceiveProtocol(override val topic: String, val otherParty: Party) : NonTerminatingProtocol() {

        @Transient var receivedPayload: Any? = null

        @Suspendable
        override fun doCall() {
            receivedPayload = receive<Any>(otherParty).unwrap { it }
        }
    }


    /**
     * A protocol that suspends forever after doing some work. This is to allow it to be retrieved from the SMM after
     * restart for testing checkpoint restoration. Store any results as @Transient fields.
     */
    private abstract class NonTerminatingProtocol : ProtocolLogic<Unit>() {

        @Suspendable
        override fun call() {
            doCall()
            Fiber.park()
        }

        @Suspendable
        abstract fun doCall()
    }

}
