package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolSessionException
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.deserialize
import com.r3corda.node.services.persistence.checkpoints
import com.r3corda.node.services.statemachine.StateMachineManager.*
import com.r3corda.testing.initiateSingleShotProtocol
import com.r3corda.testing.node.InMemoryMessagingNetwork
import com.r3corda.testing.node.InMemoryMessagingNetwork.MessageTransfer
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateMachineManagerTests {

    private val net = MockNetwork()
    private val sessionTransfers = ArrayList<SessionTransfer>()
    private lateinit var node1: MockNode
    private lateinit var node2: MockNode

    @Before
    fun start() {
        val nodes = net.createTwoNodes()
        node1 = nodes.first
        node2 = nodes.second
        net.messagingNetwork.receivedMessages.toSessionTransfers().forEach { sessionTransfers += it }
        net.runNetwork()
    }

    @After
    fun cleanUp() {
        net.stopNodes()
    }

    @Test
    fun `newly added protocol is preserved on restart`() {
        node1.smm.add(NoOpProtocol(nonTerminating = true))
        val restoredProtocol = node1.restartAndGetRestoredProtocol<NoOpProtocol>()
        assertThat(restoredProtocol.protocolStarted).isTrue()
    }

    @Test
    fun `protocol can lazily use the serviceHub in its constructor`() {
        val protocol = object : ProtocolLogic<Unit>() {
            val lazyTime by lazy { serviceHub.clock.instant() }
            @Suspendable
            override fun call() = Unit
        }
        node1.smm.add(protocol)
        assertThat(protocol.lazyTime).isNotNull()
    }

    @Test
    fun `protocol restarted just after receiving payload`() {
        node2.services.registerProtocolInitiator(SendProtocol::class) { ReceiveThenSuspendProtocol(it) }
        val payload = random63BitValue()
        node1.smm.add(SendProtocol(payload, node2.info.legalIdentity))

        // We push through just enough messages to get only the payload sent
        node2.pumpReceive()
        node2.stop()
        net.runNetwork()
        val restoredProtocol = node2.restartAndGetRestoredProtocol<ReceiveThenSuspendProtocol>(node1)
        assertThat(restoredProtocol.receivedPayloads[0]).isEqualTo(payload)
    }

    @Test
    fun `protocol added before network map does run after init`() {
        val node3 = net.createNode(node1.info.address) //create vanilla node
        val protocol = NoOpProtocol()
        node3.smm.add(protocol)
        assertEquals(false, protocol.protocolStarted) // Not started yet as no network activity has been allowed yet
        net.runNetwork() // Allow network map messages to flow
        assertEquals(true, protocol.protocolStarted) // Now we should have run the protocol
    }

    @Test
    fun `protocol added before network map will be init checkpointed`() {
        var node3 = net.createNode(node1.info.address) //create vanilla node
        val protocol = NoOpProtocol()
        node3.smm.add(protocol)
        assertEquals(false, protocol.protocolStarted) // Not started yet as no network activity has been allowed yet
        node3.stop()

        node3 = net.createNode(node1.info.address, forcedID = node3.id)
        val restoredProtocol = node3.getSingleProtocol<NoOpProtocol>().first
        assertEquals(false, restoredProtocol.protocolStarted) // Not started yet as no network activity has been allowed yet
        net.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertEquals(true, restoredProtocol.protocolStarted) // Now we should have run the protocol and hopefully cleared the init checkpoint
        node3.stop()

        // Now it is completed the protocol should leave no Checkpoint.
        node3 = net.createNode(node1.info.address, forcedID = node3.id)
        net.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertTrue(node3.smm.findStateMachines(NoOpProtocol::class.java).isEmpty())
    }

    @Test
    fun `protocol loaded from checkpoint will respond to messages from before start`() {
        val payload = random63BitValue()
        node1.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { SendProtocol(payload, it) }
        node2.smm.add(ReceiveThenSuspendProtocol(node1.info.legalIdentity)) // Prepare checkpointed receive protocol
        node2.stop() // kill receiver
        val restoredProtocol = node2.restartAndGetRestoredProtocol<ReceiveThenSuspendProtocol>(node1)
        assertThat(restoredProtocol.receivedPayloads[0]).isEqualTo(payload)
    }

    @Test
    fun `protocol with send will resend on interrupted restart`() {
        val payload = random63BitValue()
        val payload2 = random63BitValue()

        var sentCount = 0
        net.messagingNetwork.sentMessages.toSessionTransfers().filter { it.isPayloadTransfer }.forEach { sentCount++ }

        val node3 = net.createNode(node1.info.address)
        val secondProtocol = node3.initiateSingleShotProtocol(PingPongProtocol::class) { PingPongProtocol(it, payload2) }
        net.runNetwork()

        // Kick off first send and receive
        node2.smm.add(PingPongProtocol(node3.info.legalIdentity, payload))
        assertEquals(1, node2.checkpointStorage.checkpoints().size)
        // Restart node and thus reload the checkpoint and resend the message with same UUID
        node2.stop()
        val node2b = net.createNode(node1.info.address, node2.id, advertisedServices = *node2.advertisedServices.toTypedArray())
        val (firstAgain, fut1) = node2b.getSingleProtocol<PingPongProtocol>()
        // Run the network which will also fire up the second protocol. First message should get deduped. So message data stays in sync.
        net.runNetwork()
        assertEquals(1, node2.checkpointStorage.checkpoints().size)
        node2b.smm.executor.flush()
        fut1.get()

        val receivedCount = sessionTransfers.count { it.isPayloadTransfer }
        // Check protocols completed cleanly and didn't get out of phase
        assertEquals(4, receivedCount, "Protocol should have exchanged 4 unique messages")// Two messages each way
        // can't give a precise value as every addMessageHandler re-runs the undelivered messages
        assertTrue(sentCount > receivedCount, "Node restart should have retransmitted messages")
        assertEquals(0, node2b.checkpointStorage.checkpoints().size, "Checkpoints left after restored protocol should have ended")
        assertEquals(0, node3.checkpointStorage.checkpoints().size, "Checkpoints left after restored protocol should have ended")
        assertEquals(payload2, firstAgain.receivedPayload, "Received payload does not match the first value on Node 3")
        assertEquals(payload2 + 1, firstAgain.receivedPayload2, "Received payload does not match the expected second value on Node 3")
        assertEquals(payload, secondProtocol.get().receivedPayload, "Received payload does not match the (restarted) first value on Node 2")
        assertEquals(payload + 1, secondProtocol.get().receivedPayload2, "Received payload does not match the expected second value on Node 2")
    }

    @Test
    fun `sending to multiple parties`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()
        node2.services.registerProtocolInitiator(SendProtocol::class) { ReceiveThenSuspendProtocol(it) }
        node3.services.registerProtocolInitiator(SendProtocol::class) { ReceiveThenSuspendProtocol(it) }
        val payload = random63BitValue()
        node1.smm.add(SendProtocol(payload, node2.info.legalIdentity, node3.info.legalIdentity))
        net.runNetwork()
        val node2Protocol = node2.getSingleProtocol<ReceiveThenSuspendProtocol>().first
        val node3Protocol = node3.getSingleProtocol<ReceiveThenSuspendProtocol>().first
        assertThat(node2Protocol.receivedPayloads[0]).isEqualTo(payload)
        assertThat(node3Protocol.receivedPayloads[0]).isEqualTo(payload)

        assertSessionTransfers(node2,
                node1 sent sessionInit(node1, SendProtocol::class, payload) to node2,
                node2 sent sessionConfirm() to node1,
                node1 sent sessionEnd() to node2
                //There's no session end from the other protocols as they're manually suspended
        )

        assertSessionTransfers(node3,
                node1 sent sessionInit(node1, SendProtocol::class, payload) to node3,
                node3 sent sessionConfirm() to node1,
                node1 sent sessionEnd() to node3
                //There's no session end from the other protocols as they're manually suspended
        )
    }

    @Test
    fun `receiving from multiple parties`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()
        val node2Payload = random63BitValue()
        val node3Payload = random63BitValue()
        node2.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { SendProtocol(node2Payload, it) }
        node3.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { SendProtocol(node3Payload, it) }
        val multiReceiveProtocol = ReceiveThenSuspendProtocol(node2.info.legalIdentity, node3.info.legalIdentity)
        node1.smm.add(multiReceiveProtocol)
        net.runNetwork()
        assertThat(multiReceiveProtocol.receivedPayloads[0]).isEqualTo(node2Payload)
        assertThat(multiReceiveProtocol.receivedPayloads[1]).isEqualTo(node3Payload)

        assertSessionTransfers(node2,
                node1 sent sessionInit(node1, ReceiveThenSuspendProtocol::class) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent sessionData(node2Payload) to node1,
                node2 sent sessionEnd() to node1
        )

        assertSessionTransfers(node3,
                node1 sent sessionInit(node1, ReceiveThenSuspendProtocol::class) to node3,
                node3 sent sessionConfirm() to node1,
                node3 sent sessionData(node3Payload) to node1,
                node3 sent sessionEnd() to node1
        )
    }

    @Test
    fun `both sides do a send as their first IO request`() {
        node2.services.registerProtocolInitiator(PingPongProtocol::class) { PingPongProtocol(it, 20L) }
        node1.smm.add(PingPongProtocol(node2.info.legalIdentity, 10L))
        net.runNetwork()

        assertSessionTransfers(
                node1 sent sessionInit(node1, PingPongProtocol::class, 10L) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent sessionData(20L) to node1,
                node1 sent sessionData(11L) to node2,
                node2 sent sessionData(21L) to node1,
                node1 sent sessionEnd() to node2
        )
    }

    @Test
    fun `exception thrown on other side`() {
        node2.services.registerProtocolInitiator(ReceiveThenSuspendProtocol::class) { ExceptionProtocol }
        val future = node1.smm.add(ReceiveThenSuspendProtocol(node2.info.legalIdentity)).resultFuture
        net.runNetwork()
        assertThatThrownBy { future.get() }.hasCauseInstanceOf(ProtocolSessionException::class.java)
        assertSessionTransfers(
                node1 sent sessionInit(node1, ReceiveThenSuspendProtocol::class) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent sessionEnd() to node1
        )
    }

    private inline fun <reified P : ProtocolLogic<*>> MockNode.restartAndGetRestoredProtocol(
            networkMapNode: MockNode? = null): P {
        stop()
        val newNode = mockNet.createNode(networkMapNode?.info?.address, id, advertisedServices = *advertisedServices.toTypedArray())
        mockNet.runNetwork() // allow NetworkMapService messages to stabilise and thus start the state machine
        return newNode.getSingleProtocol<P>().first
    }

    private inline fun <reified P : ProtocolLogic<*>> MockNode.getSingleProtocol(): Pair<P, ListenableFuture<*>> {
        return smm.findStateMachines(P::class.java).single()
    }

    private fun sessionInit(initiatorNode: MockNode, protocolMarker: KClass<*>, payload: Any? = null): SessionInit {
        return SessionInit(0, initiatorNode.info.legalIdentity, protocolMarker.java.name, payload)
    }

    private fun sessionConfirm() = SessionConfirm(0, 0)

    private fun sessionData(payload: Any) = SessionData(0, payload)

    private fun sessionEnd() = SessionEnd(0)

    private fun assertSessionTransfers(vararg expected: SessionTransfer) {
        assertThat(sessionTransfers).containsExactly(*expected)
    }

    private fun assertSessionTransfers(node: MockNode, vararg expected: SessionTransfer) {
        val actualForNode = sessionTransfers.filter { it.from == node.id || it.to == node.id }
        assertThat(actualForNode).containsExactly(*expected)
    }

    private data class SessionTransfer(val from: Int, val message: SessionMessage, val to: Int) {
        val isPayloadTransfer: Boolean get() = message is SessionData || message is SessionInit && message.firstPayload != null
        override fun toString(): String = "$from sent $message to $to"
    }

    private fun Observable<MessageTransfer>.toSessionTransfers(): Observable<SessionTransfer> {
        return filter { it.message.topicSession == StateMachineManager.sessionTopic }.map {
            val from = it.sender.myAddress.id
            val message = it.message.data.deserialize<SessionMessage>()
            val to = (it.recipients as InMemoryMessagingNetwork.Handle).id
            SessionTransfer(from, sanitise(message), to)
        }
    }

    private fun sanitise(message: SessionMessage): SessionMessage {
        return when (message) {
            is SessionData -> message.copy(recipientSessionId = 0)
            is SessionInit -> message.copy(initiatorSessionId = 0)
            is SessionConfirm -> message.copy(initiatorSessionId = 0, initiatedSessionId = 0)
            is SessionEnd -> message.copy(recipientSessionId = 0)
            else -> message
        }
    }

    private infix fun MockNode.sent(message: SessionMessage): Pair<Int, SessionMessage> = Pair(id, message)
    private infix fun Pair<Int, SessionMessage>.to(node: MockNode): SessionTransfer = SessionTransfer(first, second, node.id)


    private class NoOpProtocol(val nonTerminating: Boolean = false) : ProtocolLogic<Unit>() {

        @Transient var protocolStarted = false

        @Suspendable
        override fun call() {
            protocolStarted = true
            if (nonTerminating) {
                Fiber.park()
            }
        }
    }


    private class SendProtocol(val payload: Any, vararg val otherParties: Party) : ProtocolLogic<Unit>() {

        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call() = otherParties.forEach { send(it, payload) }
    }


    private class ReceiveThenSuspendProtocol(vararg val otherParties: Party) : ProtocolLogic<Unit>() {

        init {
            require(otherParties.isNotEmpty())
        }

        @Transient var receivedPayloads: List<Any> = emptyList()

        @Suspendable
        override fun call() {
            receivedPayloads = otherParties.map { receive<Any>(it).unwrap { it } }
            println(receivedPayloads)
            Fiber.park()
        }
    }

    private class PingPongProtocol(val otherParty: Party, val payload: Long) : ProtocolLogic<Unit>() {

        @Transient var receivedPayload: Long? = null
        @Transient var receivedPayload2: Long? = null

        @Suspendable
        override fun call() {
            receivedPayload = sendAndReceive<Long>(otherParty, payload).unwrap { it }
            println("${psm.id} Received $receivedPayload")
            receivedPayload2 = sendAndReceive<Long>(otherParty, payload + 1).unwrap { it }
            println("${psm.id} Received $receivedPayload2")
        }
    }

    private object ExceptionProtocol : ProtocolLogic<Nothing>() {
        override fun call(): Nothing = throw Exception()
    }

}
