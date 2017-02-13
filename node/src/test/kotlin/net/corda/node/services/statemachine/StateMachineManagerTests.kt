package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.DummyState
import net.corda.core.contracts.issuedBy
import net.corda.core.crypto.Party
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.getOrThrow
import net.corda.core.map
import net.corda.core.messaging.MessageRecipients
import net.corda.core.node.services.PartyInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.core.random63BitValue
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.LogHelper
import net.corda.core.utilities.unwrap
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.NotaryFlow
import net.corda.node.services.persistence.checkpoints
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.initiateSingleShotFlow
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.InMemoryMessagingNetwork.MessageTransfer
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.sequence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StateMachineManagerTests {
    companion object {
        init {
            LogHelper.setLevel("+net.corda.flow")
        }
    }

    private val net = MockNetwork(servicePeerAllocationStrategy = RoundRobin())
    private val sessionTransfers = ArrayList<SessionTransfer>()
    private lateinit var node1: MockNode
    private lateinit var node2: MockNode
    private lateinit var notary1: MockNode
    private lateinit var notary2: MockNode

    @Before
    fun start() {
        val nodes = net.createTwoNodes()
        node1 = nodes.first
        node2 = nodes.second
        val notaryKeyPair = generateKeyPair()
        val notaryService = ServiceInfo(ValidatingNotaryService.type, "notary-service-2000")
        val overrideServices = mapOf(Pair(notaryService, notaryKeyPair))
        // Note that these notaries don't operate correctly as they don't share their state. They are only used for testing
        // service addressing.
        notary1 = net.createNotaryNode(networkMapAddr = node1.services.myInfo.address, overrideServices = overrideServices, serviceName = "notary-service-2000")
        notary2 = net.createNotaryNode(networkMapAddr = node1.services.myInfo.address, overrideServices = overrideServices, serviceName = "notary-service-2000")

        net.messagingNetwork.receivedMessages.toSessionTransfers().forEach { sessionTransfers += it }
        net.runNetwork()
    }

    @After
    fun cleanUp() {
        net.stopNodes()
    }

    @Test
    fun `newly added flow is preserved on restart`() {
        node1.services.startFlow(NoOpFlow(nonTerminating = true))
        node1.acceptableLiveFiberCountOnStop = 1
        val restoredFlow = node1.restartAndGetRestoredFlow<NoOpFlow>()
        assertThat(restoredFlow.flowStarted).isTrue()
    }

    @Test
    fun `flow can lazily use the serviceHub in its constructor`() {
        val flow = object : FlowLogic<Unit>() {
            val lazyTime by lazy { serviceHub.clock.instant() }
            @Suspendable
            override fun call() = Unit
        }
        node1.services.startFlow(flow)
        assertThat(flow.lazyTime).isNotNull()
    }

    @Test
    fun `exception while fiber suspended`() {
        node2.services.registerFlowInitiator(ReceiveFlow::class) { SendFlow("Hello", it) }
        val flow = ReceiveFlow(node2.info.legalIdentity)
        val fiber = node1.services.startFlow(flow) as FlowStateMachineImpl
        // Before the flow runs change the suspend action to throw an exception
        val exceptionDuringSuspend = Exception("Thrown during suspend")
        fiber.actionOnSuspend = {
            throw exceptionDuringSuspend
        }
        net.runNetwork()
        assertThatThrownBy {
            fiber.resultFuture.getOrThrow()
        }.isSameAs(exceptionDuringSuspend)
        assertThat(node1.smm.allStateMachines).isEmpty()
        // Make sure the fiber does actually terminate
        assertThat(fiber.isTerminated).isTrue()
    }

    @Test
    fun `flow restarted just after receiving payload`() {
        node2.services.registerFlowInitiator(SendFlow::class) { ReceiveFlow(it).nonTerminating() }
        node1.services.startFlow(SendFlow("Hello", node2.info.legalIdentity))

        // We push through just enough messages to get only the payload sent
        node2.pumpReceive()
        node2.disableDBCloseOnStop()
        node2.acceptableLiveFiberCountOnStop = 1
        node2.stop()
        net.runNetwork()
        val restoredFlow = node2.restartAndGetRestoredFlow<ReceiveFlow>(node1)
        assertThat(restoredFlow.receivedPayloads[0]).isEqualTo("Hello")
    }

    @Test
    fun `flow added before network map does run after init`() {
        val node3 = net.createNode(node1.info.address) //create vanilla node
        val flow = NoOpFlow()
        node3.services.startFlow(flow)
        assertEquals(false, flow.flowStarted) // Not started yet as no network activity has been allowed yet
        net.runNetwork() // Allow network map messages to flow
        assertEquals(true, flow.flowStarted) // Now we should have run the flow
    }

    @Test
    fun `flow added before network map will be init checkpointed`() {
        var node3 = net.createNode(node1.info.address) //create vanilla node
        val flow = NoOpFlow()
        node3.services.startFlow(flow)
        assertEquals(false, flow.flowStarted) // Not started yet as no network activity has been allowed yet
        node3.disableDBCloseOnStop()
        node3.stop()

        node3 = net.createNode(node1.info.address, forcedID = node3.id)
        val restoredFlow = node3.getSingleFlow<NoOpFlow>().first
        assertEquals(false, restoredFlow.flowStarted) // Not started yet as no network activity has been allowed yet
        net.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertEquals(true, restoredFlow.flowStarted) // Now we should have run the flow and hopefully cleared the init checkpoint
        node3.disableDBCloseOnStop()
        node3.stop()

        // Now it is completed the flow should leave no Checkpoint.
        node3 = net.createNode(node1.info.address, forcedID = node3.id)
        net.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertTrue(node3.smm.findStateMachines(NoOpFlow::class.java).isEmpty())
    }

    @Test
    fun `flow loaded from checkpoint will respond to messages from before start`() {
        node1.services.registerFlowInitiator(ReceiveFlow::class) { SendFlow("Hello", it) }
        node2.services.startFlow(ReceiveFlow(node1.info.legalIdentity).nonTerminating()) // Prepare checkpointed receive flow
        // Make sure the add() has finished initial processing.
        node2.smm.executor.flush()
        node2.disableDBCloseOnStop()
        node2.stop() // kill receiver
        val restoredFlow = node2.restartAndGetRestoredFlow<ReceiveFlow>(node1)
        assertThat(restoredFlow.receivedPayloads[0]).isEqualTo("Hello")
    }

    @Test
    fun `flow with send will resend on interrupted restart`() {
        val payload = random63BitValue()
        val payload2 = random63BitValue()

        var sentCount = 0
        net.messagingNetwork.sentMessages.toSessionTransfers().filter { it.isPayloadTransfer }.forEach { sentCount++ }

        val node3 = net.createNode(node1.info.address)
        val secondFlow = node3.initiateSingleShotFlow(PingPongFlow::class) { PingPongFlow(it, payload2) }
        net.runNetwork()

        // Kick off first send and receive
        node2.services.startFlow(PingPongFlow(node3.info.legalIdentity, payload))
        databaseTransaction(node2.database) {
            assertEquals(1, node2.checkpointStorage.checkpoints().size)
        }
        // Make sure the add() has finished initial processing.
        node2.smm.executor.flush()
        node2.disableDBCloseOnStop()
        // Restart node and thus reload the checkpoint and resend the message with same UUID
        node2.stop()
        databaseTransaction(node2.database) {
            assertEquals(1, node2.checkpointStorage.checkpoints().size) // confirm checkpoint
        }
        val node2b = net.createNode(node1.info.address, node2.id, advertisedServices = *node2.advertisedServices.toTypedArray())
        node2.manuallyCloseDB()
        val (firstAgain, fut1) = node2b.getSingleFlow<PingPongFlow>()
        // Run the network which will also fire up the second flow. First message should get deduped. So message data stays in sync.
        net.runNetwork()
        node2b.smm.executor.flush()
        fut1.getOrThrow()

        val receivedCount = sessionTransfers.count { it.isPayloadTransfer }
        // Check flows completed cleanly and didn't get out of phase
        assertEquals(4, receivedCount, "Flow should have exchanged 4 unique messages")// Two messages each way
        // can't give a precise value as every addMessageHandler re-runs the undelivered messages
        assertTrue(sentCount > receivedCount, "Node restart should have retransmitted messages")
        databaseTransaction(node2b.database) {
            assertEquals(0, node2b.checkpointStorage.checkpoints().size, "Checkpoints left after restored flow should have ended")
        }
        databaseTransaction(node3.database) {
            assertEquals(0, node3.checkpointStorage.checkpoints().size, "Checkpoints left after restored flow should have ended")
        }
        assertEquals(payload2, firstAgain.receivedPayload, "Received payload does not match the first value on Node 3")
        assertEquals(payload2 + 1, firstAgain.receivedPayload2, "Received payload does not match the expected second value on Node 3")
        assertEquals(payload, secondFlow.getOrThrow().receivedPayload, "Received payload does not match the (restarted) first value on Node 2")
        assertEquals(payload + 1, secondFlow.getOrThrow().receivedPayload2, "Received payload does not match the expected second value on Node 2")
    }

    @Test
    fun `sending to multiple parties`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()
        node2.services.registerFlowInitiator(SendFlow::class) { ReceiveFlow(it).nonTerminating() }
        node3.services.registerFlowInitiator(SendFlow::class) { ReceiveFlow(it).nonTerminating() }
        val payload = "Hello World"
        node1.services.startFlow(SendFlow(payload, node2.info.legalIdentity, node3.info.legalIdentity))
        net.runNetwork()
        val node2Flow = node2.getSingleFlow<ReceiveFlow>().first
        val node3Flow = node3.getSingleFlow<ReceiveFlow>().first
        assertThat(node2Flow.receivedPayloads[0]).isEqualTo(payload)
        assertThat(node3Flow.receivedPayloads[0]).isEqualTo(payload)

        assertSessionTransfers(node2,
                node1 sent sessionInit(SendFlow::class, payload) to node2,
                node2 sent sessionConfirm to node1,
                node1 sent normalEnd to node2
                //There's no session end from the other flows as they're manually suspended
        )

        assertSessionTransfers(node3,
                node1 sent sessionInit(SendFlow::class, payload) to node3,
                node3 sent sessionConfirm to node1,
                node1 sent normalEnd to node3
                //There's no session end from the other flows as they're manually suspended
        )

        node2.acceptableLiveFiberCountOnStop = 1
        node3.acceptableLiveFiberCountOnStop = 1
    }

    @Test
    fun `receiving from multiple parties`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()
        val node2Payload = "Test 1"
        val node3Payload = "Test 2"
        node2.services.registerFlowInitiator(ReceiveFlow::class) { SendFlow(node2Payload, it) }
        node3.services.registerFlowInitiator(ReceiveFlow::class) { SendFlow(node3Payload, it) }
        val multiReceiveFlow = ReceiveFlow(node2.info.legalIdentity, node3.info.legalIdentity).nonTerminating()
        node1.services.startFlow(multiReceiveFlow)
        node1.acceptableLiveFiberCountOnStop = 1
        net.runNetwork()
        assertThat(multiReceiveFlow.receivedPayloads[0]).isEqualTo(node2Payload)
        assertThat(multiReceiveFlow.receivedPayloads[1]).isEqualTo(node3Payload)

        assertSessionTransfers(node2,
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm to node1,
                node2 sent sessionData(node2Payload) to node1,
                node2 sent normalEnd to node1
        )

        assertSessionTransfers(node3,
                node1 sent sessionInit(ReceiveFlow::class) to node3,
                node3 sent sessionConfirm to node1,
                node3 sent sessionData(node3Payload) to node1,
                node3 sent normalEnd to node1
        )
    }

    @Test
    fun `both sides do a send as their first IO request`() {
        node2.services.registerFlowInitiator(PingPongFlow::class) { PingPongFlow(it, 20L) }
        node1.services.startFlow(PingPongFlow(node2.info.legalIdentity, 10L))
        net.runNetwork()

        assertSessionTransfers(
                node1 sent sessionInit(PingPongFlow::class, 10L) to node2,
                node2 sent sessionConfirm to node1,
                node2 sent sessionData(20L) to node1,
                node1 sent sessionData(11L) to node2,
                node2 sent sessionData(21L) to node1,
                node1 sent normalEnd to node2
        )
    }

    @Test
    fun `different notaries are picked when addressing shared notary identity`() {
        assertEquals(notary1.info.notaryIdentity, notary2.info.notaryIdentity)
        node1.services.startFlow(CashIssueFlow(
                2000.DOLLARS,
                OpaqueBytes.of(0x01),
                node1.info.legalIdentity,
                notary1.info.notaryIdentity))
        // We pay a couple of times, the notary picking should go round robin
        for (i in 1 .. 3) {
            node1.services.startFlow(CashPaymentFlow(
                    500.DOLLARS.issuedBy(node1.info.legalIdentity.ref(0x01)),
                    node2.info.legalIdentity))
            net.runNetwork()
        }
        val endpoint = net.messagingNetwork.endpoint(notary1.net.myAddress as InMemoryMessagingNetwork.PeerHandle)!!
        val party1Info = notary1.services.networkMapCache.getPartyInfo(notary1.info.notaryIdentity)!!
        assert(party1Info is PartyInfo.Service)
        val notary1Address: MessageRecipients = endpoint.getAddressOfParty(notary1.services.networkMapCache.getPartyInfo(notary1.info.notaryIdentity)!!)
        assertThat(notary1Address).isInstanceOf(InMemoryMessagingNetwork.ServiceHandle::class.java)
        assertEquals(notary1Address, endpoint.getAddressOfParty(notary2.services.networkMapCache.getPartyInfo(notary2.info.notaryIdentity)!!))
        sessionTransfers.expectEvents(isStrict = false) {
            sequence(
                    // First Pay
                    expect(match = { it.message is SessionInit && it.message.flowName == NotaryFlow.Client::class.java.name }) {
                        it.message as SessionInit
                        assertEquals(node1.id, it.from)
                        assertEquals(notary1Address, it.to)
                    },
                    expect(match = { it.message is SessionConfirm }) {
                        it.message as SessionConfirm
                        assertEquals(notary1.id, it.from)
                    },
                    // Second pay
                    expect(match = { it.message is SessionInit && it.message.flowName == NotaryFlow.Client::class.java.name }) {
                        it.message as SessionInit
                        assertEquals(node1.id, it.from)
                        assertEquals(notary1Address, it.to)
                    },
                    expect(match = { it.message is SessionConfirm }) {
                        it.message as SessionConfirm
                        assertEquals(notary2.id, it.from)
                    },
                    // Third pay
                    expect(match = { it.message is SessionInit && it.message.flowName == NotaryFlow.Client::class.java.name }) {
                        it.message as SessionInit
                        assertEquals(node1.id, it.from)
                        assertEquals(notary1Address, it.to)
                    },
                    expect(match = { it.message is SessionConfirm }) {
                        it.message as SessionConfirm
                        assertEquals(it.from, notary1.id)
                    }
            )
        }
    }

    @Test
    fun `other side ends before doing expected send`() {
        node2.services.registerFlowInitiator(ReceiveFlow::class) { NoOpFlow() }
        val resultFuture = node1.services.startFlow(ReceiveFlow(node2.info.legalIdentity)).resultFuture
        net.runNetwork()
        assertThatExceptionOfType(FlowSessionException::class.java).isThrownBy {
            resultFuture.getOrThrow()
        }.withMessageContaining(String::class.java.name)
    }

    @Test
    fun `non-FlowException thrown on other side`() {
        node2.services.registerFlowInitiator(ReceiveFlow::class) { ExceptionFlow { Exception("evil bug!") } }
        val resultFuture = node1.services.startFlow(ReceiveFlow(node2.info.legalIdentity)).resultFuture
        net.runNetwork()
        val exceptionResult = assertFailsWith(FlowSessionException::class) {
            resultFuture.getOrThrow()
        }
        assertThat(exceptionResult.message).doesNotContain("evil bug!")
        assertSessionTransfers(
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm to node1,
                node2 sent erroredEnd() to node1
        )
    }

    @Test
    fun `FlowException thrown on other side`() {
        val erroringFlowFuture = node2.initiateSingleShotFlow(ReceiveFlow::class) {
            ExceptionFlow { MyFlowException("Nothing useful") }
        }
        val receivingFiber = node1.services.startFlow(ReceiveFlow(node2.info.legalIdentity)) as FlowStateMachineImpl
        net.runNetwork()
        assertThatExceptionOfType(MyFlowException::class.java)
                .isThrownBy { receivingFiber.resultFuture.getOrThrow() }
                .withMessage("Nothing useful")
                .withStackTraceContaining(ReceiveFlow::class.java.name)  // Make sure the stack trace is that of the receiving flow
        databaseTransaction(node2.database) {
            assertThat(node2.checkpointStorage.checkpoints()).isEmpty()
        }
        val errorFlow = erroringFlowFuture.getOrThrow()
        assertThat(receivingFiber.isTerminated).isTrue()
        assertThat((errorFlow.stateMachine as FlowStateMachineImpl).isTerminated).isTrue()
        assertSessionTransfers(
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm to node1,
                node2 sent erroredEnd(errorFlow.exceptionThrown) to node1
        )
        // Make sure the original stack trace isn't sent down the wire
        assertThat((sessionTransfers.last().message as ErrorSessionEnd).errorResponse!!.stackTrace).isEmpty()
    }

    @Test
    fun `FlowException propagated in invocation chain`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()

        node3.initiateSingleShotFlow(ReceiveFlow::class) { ExceptionFlow { MyFlowException("Chain") } }
        node2.initiateSingleShotFlow(ReceiveFlow::class) { ReceiveFlow(node3.info.legalIdentity) }
        val receivingFiber = node1.services.startFlow(ReceiveFlow(node2.info.legalIdentity))
        net.runNetwork()
        assertThatExceptionOfType(MyFlowException::class.java)
                .isThrownBy { receivingFiber.resultFuture.getOrThrow() }
                .withMessage("Chain")
    }

    private class SendAndReceiveFlow(val otherParty: Party, val payload: Any) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            sendAndReceive<Any>(otherParty, payload)
        }
    }

    @Test
    fun `FlowException thrown and there is a 3rd unrelated party flow`() {
        val node3 = net.createNode(node1.info.address)
        net.runNetwork()

        // Node 2 will send its payload and then block waiting for the receive from node 1. Meanwhile node 1 will move
        // onto node 3 which will throw the exception
        val node2Fiber = node2
                .initiateSingleShotFlow(ReceiveFlow::class) { SendAndReceiveFlow(it, "Hello") }
                .map { it.stateMachine }
        node3.initiateSingleShotFlow(ReceiveFlow::class) { ExceptionFlow { MyFlowException("Nothing useful") } }

        val node1Fiber = node1.services.startFlow(ReceiveFlow(node2.info.legalIdentity, node3.info.legalIdentity)) as FlowStateMachineImpl
        net.runNetwork()

        // Node 1 will terminate with the error it received from node 3 but it won't propagate that to node 2 (as it's
        // not relevant to it) but it will end its session with it
        assertThatExceptionOfType(MyFlowException::class.java).isThrownBy {
            node1Fiber.resultFuture.getOrThrow()
        }
        val node2ResultFuture = node2Fiber.getOrThrow().resultFuture
        assertThatExceptionOfType(FlowSessionException::class.java).isThrownBy {
            node2ResultFuture.getOrThrow()
        }

        assertSessionTransfers(node2,
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm to node1,
                node2 sent sessionData("Hello") to node1,
                node1 sent erroredEnd() to node2
        )
    }

    private class ConditionalExceptionFlow(val otherParty: Party, val sendPayload: Any) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val throwException = receive<Boolean>(otherParty).unwrap { it }
            if (throwException) {
                throw MyFlowException("Throwing exception as requested")
            }
            send(otherParty, sendPayload)
        }
    }

    @Test
    fun `retry subFlow due to receiving FlowException`() {
        class AskForExceptionFlow(val otherParty: Party, val throwException: Boolean) : FlowLogic<String>() {
            @Suspendable
            override fun call(): String = sendAndReceive<String>(otherParty, throwException).unwrap { it }
        }

        class RetryOnExceptionFlow(val otherParty: Party) : FlowLogic<String>() {
            @Suspendable
            override fun call(): String {
                return try {
                    subFlow(AskForExceptionFlow(otherParty, throwException = true))
                } catch (e: MyFlowException) {
                    subFlow(AskForExceptionFlow(otherParty, throwException = false))
                }
            }
        }

        node2.services.registerFlowInitiator(AskForExceptionFlow::class) { ConditionalExceptionFlow(it, "Hello") }
        val resultFuture = node1.services.startFlow(RetryOnExceptionFlow(node2.info.legalIdentity)).resultFuture
        net.runNetwork()
        assertThat(resultFuture.getOrThrow()).isEqualTo("Hello")
    }

    @Test
    fun `wait for transaction`() {
        val ptx = TransactionBuilder(notary = notary1.info.notaryIdentity)
        ptx.addOutputState(DummyState())
        ptx.signWith(node1.services.legalIdentityKey)
        val stx = ptx.toSignedTransaction()

        val committerFiber = node1
                .initiateSingleShotFlow(WaitingFlows.Waiter::class) { WaitingFlows.Committer(it) }
                .map { it.stateMachine }
        val waiterStx = node2.services.startFlow(WaitingFlows.Waiter(stx, node1.info.legalIdentity)).resultFuture
        net.runNetwork()
        assertThat(waiterStx.getOrThrow()).isEqualTo(committerFiber.getOrThrow().resultFuture.getOrThrow())
    }

    @Test
    fun `committer throws exception before calling the finality flow`() {
        val ptx = TransactionBuilder(notary = notary1.info.notaryIdentity)
        ptx.addOutputState(DummyState())
        ptx.signWith(node1.services.legalIdentityKey)
        val stx = ptx.toSignedTransaction()

        node1.services.registerFlowInitiator(WaitingFlows.Waiter::class) {
            WaitingFlows.Committer(it) { throw Exception("Error") }
        }
        val waiter = node2.services.startFlow(WaitingFlows.Waiter(stx, node1.info.legalIdentity)).resultFuture
        net.runNetwork()
        assertThatExceptionOfType(FlowSessionException::class.java).isThrownBy {
            waiter.getOrThrow()
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //region Helpers

    private inline fun <reified P : FlowLogic<*>> MockNode.restartAndGetRestoredFlow(networkMapNode: MockNode? = null): P {
        disableDBCloseOnStop() // Handover DB to new node copy
        stop()
        val newNode = mockNet.createNode(networkMapNode?.info?.address, id, advertisedServices = *advertisedServices.toTypedArray())
        newNode.acceptableLiveFiberCountOnStop = 1
        manuallyCloseDB()
        mockNet.runNetwork() // allow NetworkMapService messages to stabilise and thus start the state machine
        return newNode.getSingleFlow<P>().first
    }

    private inline fun <reified P : FlowLogic<*>> MockNode.getSingleFlow(): Pair<P, ListenableFuture<*>> {
        return smm.findStateMachines(P::class.java).single()
    }

    private fun sessionInit(flowMarker: KClass<*>, payload: Any? = null) = SessionInit(0, flowMarker.java.name, payload)
    private val sessionConfirm = SessionConfirm(0, 0)
    private fun sessionData(payload: Any) = SessionData(0, payload)
    private val normalEnd = NormalSessionEnd(0)
    private fun erroredEnd(errorResponse: FlowException? = null) = ErrorSessionEnd(0, errorResponse)

    private fun assertSessionTransfers(vararg expected: SessionTransfer) {
        assertThat(sessionTransfers).containsExactly(*expected)
    }

    private fun assertSessionTransfers(node: MockNode, vararg expected: SessionTransfer): List<SessionTransfer> {
        val actualForNode = sessionTransfers.filter { it.from == node.id || it.to == node.net.myAddress }
        assertThat(actualForNode).containsExactly(*expected)
        return actualForNode
    }

    private data class SessionTransfer(val from: Int, val message: SessionMessage, val to: MessageRecipients) {
        val isPayloadTransfer: Boolean get() = message is SessionData || message is SessionInit && message.firstPayload != null
        override fun toString(): String = "$from sent $message to $to"
    }

    private fun Observable<MessageTransfer>.toSessionTransfers(): Observable<SessionTransfer> {
        return filter { it.message.topicSession == StateMachineManager.sessionTopic }.map {
            val from = it.sender.id
            val message = it.message.data.deserialize<SessionMessage>()
            SessionTransfer(from, sanitise(message), it.recipients)
        }
    }

    private fun sanitise(message: SessionMessage): SessionMessage {
        return when (message) {
            is SessionData -> message.copy(recipientSessionId = 0)
            is SessionInit -> message.copy(initiatorSessionId = 0)
            is SessionConfirm -> message.copy(initiatorSessionId = 0, initiatedSessionId = 0)
            is NormalSessionEnd -> message.copy(recipientSessionId = 0)
            is ErrorSessionEnd -> message.copy(recipientSessionId = 0)
            else -> message
        }
    }

    private infix fun MockNode.sent(message: SessionMessage): Pair<Int, SessionMessage> = Pair(id, message)
    private infix fun Pair<Int, SessionMessage>.to(node: MockNode): SessionTransfer = SessionTransfer(first, second, node.net.myAddress)

    private class NoOpFlow(val nonTerminating: Boolean = false) : FlowLogic<Unit>() {
        @Transient var flowStarted = false

        @Suspendable
        override fun call() {
            flowStarted = true
            if (nonTerminating) {
                Fiber.park()
            }
        }
    }


    private class SendFlow(val payload: String, vararg val otherParties: Party) : FlowLogic<Unit>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call() = otherParties.forEach { send(it, payload) }
    }


    private class ReceiveFlow(vararg val otherParties: Party) : FlowLogic<Unit>() {
        private var nonTerminating: Boolean = false

        init {
            require(otherParties.isNotEmpty())
        }

        @Transient var receivedPayloads: List<String> = emptyList()

        @Suspendable
        override fun call() {
            receivedPayloads = otherParties.map { receive<String>(it).unwrap { it } }
            if (nonTerminating) {
                Fiber.park()
            }
        }

        fun nonTerminating(): ReceiveFlow {
            nonTerminating = true
            return this
        }
    }

    private class PingPongFlow(val otherParty: Party, val payload: Long) : FlowLogic<Unit>() {
        @Transient var receivedPayload: Long? = null
        @Transient var receivedPayload2: Long? = null

        @Suspendable
        override fun call() {
            receivedPayload = sendAndReceive<Long>(otherParty, payload).unwrap { it }
            receivedPayload2 = sendAndReceive<Long>(otherParty, payload + 1).unwrap { it }
        }
    }

    private class ExceptionFlow<E : Exception>(val exception: () -> E) : FlowLogic<Nothing>() {
        lateinit var exceptionThrown: E
        override fun call(): Nothing {
            exceptionThrown = exception()
            throw exceptionThrown
        }
    }

    private class MyFlowException(override val message: String) : FlowException() {
        override fun equals(other: Any?): Boolean = other is MyFlowException && other.message == this.message
        override fun hashCode(): Int = message.hashCode()
    }

    private object WaitingFlows {
        class Waiter(val stx: SignedTransaction, val otherParty: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                send(otherParty, stx)
                return waitForLedgerCommit(stx.id)
            }
        }

        class Committer(val otherParty: Party, val throwException: (() -> Exception)? = null) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val stx = receive<SignedTransaction>(otherParty).unwrap { it }
                if (throwException != null) throw throwException.invoke()
                return subFlow(FinalityFlow(stx, setOf(otherParty))).single()
            }
        }
    }

    //endregion Helpers
}
