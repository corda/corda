package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolSessionException
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.deserialize
import com.r3corda.node.services.statemachine.StateMachineManager.SessionData
import com.r3corda.node.services.statemachine.StateMachineManager.SessionMessage
import com.r3corda.testing.node.InMemoryMessagingNetwork
import com.r3corda.node.services.persistence.checkpoints
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    fun `protocol restarted just after receiving payload`() {
        node2.services.registerProtocolInitiator(SendProtocol::class) { ReceiveThenSuspendProtocol(it) }
        val payload = random63BitValue()
        node1.smm.add("test", SendProtocol(payload, node2.info.identity))

        // We push through just enough messages to get only the SessionData sent
        // TODO We should be able to give runNetwork a predicate for when to stop
        net.runNetwork(2)
        node2.stop()
        net.runNetwork()
        val restoredProtocol = node2.restartAndGetRestoredProtocol<ReceiveThenSuspendProtocol>(node1.info.address)
        assertThat(restoredProtocol.receivedPayloads[0]).isEqualTo(payload)
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
        val restoredProtocol = node3.getSingleProtocol<ProtocolNoBlocking>().first
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
        val payload = random63BitValue()
        node1.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { SendProtocol(payload, it) }
        val receiveProtocol = ReceiveThenSuspendProtocol(node1.info.identity)
        node2.smm.add("test", receiveProtocol) // Prepare checkpointed receive protocol
        node2.stop() // kill receiver
        val restoredProtocol = node2.restartAndGetRestoredProtocol<ReceiveThenSuspendProtocol>(node1.info.address)
        assertThat(restoredProtocol.receivedPayloads[0]).isEqualTo(payload)
    }

    @Test
    fun `protocol with send will resend on interrupted restart`() {
        val payload = random63BitValue()
        val payload2 = random63BitValue()

        var sentCount = 0
        var receivedCount = 0
        net.messagingNetwork.sentMessages.subscribe { if (isDataMessage(it)) sentCount++ }
        net.messagingNetwork.receivedMessages.subscribe { if (isDataMessage(it)) receivedCount++ }
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()

        var secondProtocol: PingPongProtocol? = null
        node3.services.registerProtocolInitiator(PingPongProtocol::class) {
            val protocol = PingPongProtocol(it, payload2)
            secondProtocol = protocol
            protocol
        }

        // Kick off first send and receive
        node2.smm.add("test", PingPongProtocol(node3.info.identity, payload))
        assertEquals(1, node2.checkpointStorage.checkpoints().count())
        // Restart node and thus reload the checkpoint and resend the message with same UUID
        node2.stop()
        val node2b = net.createNode(node1.info.address, node2.id, advertisedServices = *node2.advertisedServices.toTypedArray())
        val (firstAgain, fut1) = node2b.getSingleProtocol<PingPongProtocol>()
        net.runNetwork()
        assertEquals(1, node2.checkpointStorage.checkpoints().count())
        // Run the network which will also fire up the second protocol. First message should get deduped. So message data stays in sync.
        net.runNetwork()
        node2b.smm.executor.flush()
        fut1.get()
        // Check protocols completed cleanly and didn't get out of phase
        assertEquals(4, receivedCount, "Protocol should have exchanged 4 unique messages")// Two messages each way
        assertTrue(sentCount > receivedCount, "Node restart should have retransmitted messages") // can't give a precise value as every addMessageHandler re-runs the undelivered messages
        assertEquals(0, node2b.checkpointStorage.checkpoints().count(), "Checkpoints left after restored protocol should have ended")
        assertEquals(0, node3.checkpointStorage.checkpoints().count(), "Checkpoints left after restored protocol should have ended")
        assertEquals(payload2, firstAgain.receivedPayload, "Received payload does not match the first value on Node 3")
        assertEquals(payload2 + 1, firstAgain.receivedPayload2, "Received payload does not match the expected second value on Node 3")
        assertEquals(payload, secondProtocol!!.receivedPayload, "Received payload does not match the (restarted) first value on Node 2")
        assertEquals(payload + 1, secondProtocol!!.receivedPayload2, "Received payload does not match the expected second value on Node 2")
    }

    @Test
    fun `sending to multiple parties`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()
        node2.services.registerProtocolInitiator(SendProtocol::class) { ReceiveThenSuspendProtocol(it) }
        node3.services.registerProtocolInitiator(SendProtocol::class) { ReceiveThenSuspendProtocol(it) }
        val payload = random63BitValue()
        node1.smm.add("multiple-send", SendProtocol(payload, node2.info.identity, node3.info.identity))
        net.runNetwork()
        val node2Protocol = node2.getSingleProtocol<ReceiveThenSuspendProtocol>().first
        val node3Protocol = node3.getSingleProtocol<ReceiveThenSuspendProtocol>().first
        assertThat(node2Protocol.receivedPayloads[0]).isEqualTo(payload)
        assertThat(node3Protocol.receivedPayloads[0]).isEqualTo(payload)
    }

    @Test
    fun `receiving from multiple parties`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()
        val node2Payload = random63BitValue()
        val node3Payload = random63BitValue()
        node2.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { SendProtocol(node2Payload, it) }
        node3.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { SendProtocol(node3Payload, it) }
        val multiReceiveProtocol = ReceiveThenSuspendProtocol(node2.info.identity, node3.info.identity)
        node1.smm.add("multiple-receive", multiReceiveProtocol)
        net.runNetwork(1) // session handshaking
        // have the messages arrive in reverse order of receive
        node3.pumpReceive(false)
        node2.pumpReceive(false)
        net.runNetwork()  // pump remaining messages
        assertThat(multiReceiveProtocol.receivedPayloads[0]).isEqualTo(node2Payload)
        assertThat(multiReceiveProtocol.receivedPayloads[1]).isEqualTo(node3Payload)
    }

    @Test
    fun `exception thrown on other side`() {
        node2.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { ExceptionProtocol }
        val future = node1.smm.add("exception", ReceiveThenSuspendProtocol(node2.info.identity)).resultFuture
        net.runNetwork()
        assertThatThrownBy { future.get() }.hasCauseInstanceOf(ProtocolSessionException::class.java)
    }

    private fun isDataMessage(transfer: InMemoryMessagingNetwork.MessageTransfer): Boolean {
        return transfer.message.topicSession == StateMachineManager.sessionTopic
                && transfer.message.data.deserialize<SessionMessage>() is SessionData
    }

    private inline fun <reified P : NonTerminatingProtocol> MockNode.restartAndGetRestoredProtocol(networkMapAddress: SingleMessageRecipient? = null): P {
        stop()
        val newNode = mockNet.createNode(networkMapAddress, id, advertisedServices = *advertisedServices.toTypedArray())
        mockNet.runNetwork() // allow NetworkMapService messages to stabilise and thus start the state machine
        return newNode.getSingleProtocol<P>().first
    }

    private inline fun <reified P : ProtocolLogic<*>> MockNode.getSingleProtocol(): Pair<P, ListenableFuture<*>> {
        return smm.findStateMachines(P::class.java).single()
    }


    private class ProtocolNoBlocking : ProtocolLogic<Unit>() {
        @Transient var protocolStarted = false

        @Suspendable
        override fun call() {
            protocolStarted = true
        }
    }

    private class ProtocolWithoutCheckpoints : NonTerminatingProtocol() {

        @Transient var protocolStarted = false

        @Suspendable
        override fun doCall() {
            protocolStarted = true
        }
    }


    private class ProtocolWithLazyServiceHub : ProtocolLogic<Unit>() {

        val lazyTime by lazy { serviceHub.clock.instant() }

        @Suspendable
        override fun call() = Unit
    }


    private class SendProtocol(val payload: Any, vararg val otherParties: Party) : ProtocolLogic<Unit>() {

        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call() = otherParties.forEach { send(it, payload) }
    }


    private class ReceiveThenSuspendProtocol(vararg val otherParties: Party) : NonTerminatingProtocol() {

        init {
            require(otherParties.isNotEmpty())
        }

        @Transient var receivedPayloads: List<Any> = emptyList()

        @Suspendable
        override fun doCall() {
            receivedPayloads = otherParties.map { receive<Any>(it).unwrap { it } }
        }
    }

    private class PingPongProtocol(val otherParty: Party, val payload: Long) : ProtocolLogic<Unit>() {

        @Transient var receivedPayload: Long? = null
        @Transient var receivedPayload2: Long? = null

        @Suspendable
        override fun call() {
            receivedPayload = sendAndReceive<Long>(otherParty, payload).unwrap { it }
            receivedPayload2 = sendAndReceive<Long>(otherParty, (payload + 1)).unwrap { it }
        }
    }

    private object ExceptionProtocol : ProtocolLogic<Nothing>() {
        override fun call(): Nothing = throw Exception()
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
