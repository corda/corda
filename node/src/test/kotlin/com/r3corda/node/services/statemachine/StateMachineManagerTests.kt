package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

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
        val sessionID = random63BitValue()
        val payload = random63BitValue()
        node1.smm.add("test", SendProtocol(topic, node2.info.identity, sessionID, payload))
        node2.smm.add("test", ReceiveProtocol(topic, sessionID))
        net.runNetwork()
        node2.stop()
        val restoredProtocol = node2.restartAndGetRestoredProtocol<ReceiveProtocol>(node1.info.address)
        assertThat(restoredProtocol.receivedPayload).isEqualTo(payload)
    }

    private inline fun <reified P : NonTerminatingProtocol> MockNode.restartAndGetRestoredProtocol(networkMapAddress: SingleMessageRecipient? = null): P {
        val servicesArray = advertisedServices.toTypedArray()
        val node = mockNet.createNode(networkMapAddress, id, advertisedServices = *servicesArray)
        return node.smm.findStateMachines(P::class.java).single().first
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


    private class SendProtocol(override val topic: String, val destination: Party, val sessionID: Long, val payload: Any) : ProtocolLogic<Unit>() {
        @Suspendable
        override fun call() = send(destination, sessionID, payload)
    }


    private class ReceiveProtocol(override val topic: String, val sessionID: Long) : NonTerminatingProtocol() {

        @Transient var receivedPayload: Any? = null

        @Suspendable
        override fun doCall() {
            receivedPayload = receive<Any>(sessionID).unwrap { it }
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
