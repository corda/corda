package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.concurrent.Semaphore
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.messaging.MessageRecipients
import net.corda.core.node.services.PartyInfo
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Change
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.StartedNode
import net.corda.node.services.persistence.checkpoints
import net.corda.testing.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.node.InMemoryMessagingNetwork.MessageTransfer
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.node.pumpReceive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Notification
import rx.Observable
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowFrameworkTests {
    companion object {
        init {
            LogHelper.setLevel("+net.corda.flow")
        }
    }

    private lateinit var mockNet: MockNetwork
    private val receivedSessionMessages = ArrayList<SessionTransfer>()
    private lateinit var node1: StartedNode<MockNode>
    private lateinit var node2: StartedNode<MockNode>
    private lateinit var notary: StartedNode<MockNode>
    private lateinit var notaryIdentity: Party

    @Before
    fun start() {
        mockNet = MockNetwork(servicePeerAllocationStrategy = RoundRobin(), cordappPackages = listOf("net.corda.finance.contracts", "net.corda.testing.contracts"))
        node1 = mockNet.createNode()
        node2 = mockNet.createNode()

        mockNet.runNetwork()
        node1.internals.ensureRegistered()

        // We intentionally create our own notary and ignore the one provided by the network
        // Note that these notaries don't operate correctly as they don't share their state. They are only used for testing
        // service addressing.
        notary = mockNet.createNotaryNode()

        receivedSessionMessagesObservable().forEach { receivedSessionMessages += it }
        mockNet.runNetwork()
        notaryIdentity = notary.services.myInfo.legalIdentities[1]
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        receivedSessionMessages.clear()
    }

    @Test
    fun `newly added flow is preserved on restart`() {
        node1.services.startFlow(NoOpFlow(nonTerminating = true))
        node1.internals.acceptableLiveFiberCountOnStop = 1
        val restoredFlow = node1.restartAndGetRestoredFlow<NoOpFlow>()
        assertThat(restoredFlow.flowStarted).isTrue()
    }

    @Test
    fun `flow can lazily use the serviceHub in its constructor`() {
        val flow = LazyServiceHubAccessFlow()
        node1.services.startFlow(flow)
        assertThat(flow.lazyTime).isNotNull()
    }

    @Test
    fun `exception while fiber suspended`() {
        node2.registerFlowFactory(ReceiveFlow::class) { InitiatedSendFlow("Hello", it) }
        val flow = ReceiveFlow(node2.info.chooseIdentity())
        val fiber = node1.services.startFlow(flow) as FlowStateMachineImpl
        // Before the flow runs change the suspend action to throw an exception
        val exceptionDuringSuspend = Exception("Thrown during suspend")
        fiber.actionOnSuspend = {
            throw exceptionDuringSuspend
        }
        mockNet.runNetwork()
        assertThatThrownBy {
            fiber.resultFuture.getOrThrow()
        }.isSameAs(exceptionDuringSuspend)
        assertThat(node1.smm.allStateMachines).isEmpty()
        // Make sure the fiber does actually terminate
        assertThat(fiber.isTerminated).isTrue()
    }

    @Test
    fun `flow restarted just after receiving payload`() {
        node2.registerFlowFactory(SendFlow::class) { InitiatedReceiveFlow(it).nonTerminating() }
        node1.services.startFlow(SendFlow("Hello", node2.info.chooseIdentity()))

        // We push through just enough messages to get only the payload sent
        node2.pumpReceive()
        node2.internals.disableDBCloseOnStop()
        node2.internals.acceptableLiveFiberCountOnStop = 1
        node2.dispose()
        mockNet.runNetwork()
        val restoredFlow = node2.restartAndGetRestoredFlow<InitiatedReceiveFlow>()
        assertThat(restoredFlow.receivedPayloads[0]).isEqualTo("Hello")
    }

    @Test
    fun `flow added before network map does run after init`() {
        val node3 = mockNet.createNode() //create vanilla node
        val flow = NoOpFlow()
        node3.services.startFlow(flow)
        assertEquals(false, flow.flowStarted) // Not started yet as no network activity has been allowed yet
        mockNet.runNetwork() // Allow network map messages to flow
        assertEquals(true, flow.flowStarted) // Now we should have run the flow
    }

    @Test
    fun `flow added before network map will be init checkpointed`() {
        var node3 = mockNet.createNode() //create vanilla node
        val flow = NoOpFlow()
        node3.services.startFlow(flow)
        assertEquals(false, flow.flowStarted) // Not started yet as no network activity has been allowed yet
        node3.internals.disableDBCloseOnStop()
        node3.services.networkMapCache.clearNetworkMapCache() // zap persisted NetworkMapCache to force use of network.
        node3.dispose()

        node3 = mockNet.createNode(node3.internals.id)
        val restoredFlow = node3.getSingleFlow<NoOpFlow>().first
        assertEquals(false, restoredFlow.flowStarted) // Not started yet as no network activity has been allowed yet
        mockNet.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertEquals(true, restoredFlow.flowStarted) // Now we should have run the flow and hopefully cleared the init checkpoint
        node3.internals.disableDBCloseOnStop()
        node3.services.networkMapCache.clearNetworkMapCache() // zap persisted NetworkMapCache to force use of network.
        node3.dispose()

        // Now it is completed the flow should leave no Checkpoint.
        node3 = mockNet.createNode(node3.internals.id)
        mockNet.runNetwork() // Allow network map messages to flow
        node3.smm.executor.flush()
        assertTrue(node3.smm.findStateMachines(NoOpFlow::class.java).isEmpty())
    }

    @Test
    fun `flow loaded from checkpoint will respond to messages from before start`() {
        node1.registerFlowFactory(ReceiveFlow::class) { InitiatedSendFlow("Hello", it) }
        node2.services.startFlow(ReceiveFlow(node1.info.chooseIdentity()).nonTerminating()) // Prepare checkpointed receive flow
        // Make sure the add() has finished initial processing.
        node2.smm.executor.flush()
        node2.internals.disableDBCloseOnStop()
        node2.dispose() // kill receiver
        val restoredFlow = node2.restartAndGetRestoredFlow<ReceiveFlow>()
        assertThat(restoredFlow.receivedPayloads[0]).isEqualTo("Hello")
    }

    @Test
    fun `flow with send will resend on interrupted restart`() {
        val payload = random63BitValue()
        val payload2 = random63BitValue()

        var sentCount = 0
        mockNet.messagingNetwork.sentMessages.toSessionTransfers().filter { it.isPayloadTransfer }.forEach { sentCount++ }

        val node3 = mockNet.createNode()
        val secondFlow = node3.registerFlowFactory(PingPongFlow::class) { PingPongFlow(it, payload2) }
        mockNet.runNetwork()

        // Kick off first send and receive
        node2.services.startFlow(PingPongFlow(node3.info.chooseIdentity(), payload))
        node2.database.transaction {
            assertEquals(1, node2.checkpointStorage.checkpoints().size)
        }
        // Make sure the add() has finished initial processing.
        node2.smm.executor.flush()
        node2.internals.disableDBCloseOnStop()
        // Restart node and thus reload the checkpoint and resend the message with same UUID
        node2.dispose()
        node2.database.transaction {
            assertEquals(1, node2.checkpointStorage.checkpoints().size) // confirm checkpoint
            node2.services.networkMapCache.clearNetworkMapCache()
        }
        val node2b = mockNet.createNode(node2.internals.id)
        node2.internals.manuallyCloseDB()
        val (firstAgain, fut1) = node2b.getSingleFlow<PingPongFlow>()
        // Run the network which will also fire up the second flow. First message should get deduped. So message data stays in sync.
        mockNet.runNetwork()
        node2b.smm.executor.flush()
        fut1.getOrThrow()

        val receivedCount = receivedSessionMessages.count { it.isPayloadTransfer }
        // Check flows completed cleanly and didn't get out of phase
        assertEquals(4, receivedCount, "Flow should have exchanged 4 unique messages")// Two messages each way
        // can't give a precise value as every addMessageHandler re-runs the undelivered messages
        assertTrue(sentCount > receivedCount, "Node restart should have retransmitted messages")
        node2b.database.transaction {
            assertEquals(0, node2b.checkpointStorage.checkpoints().size, "Checkpoints left after restored flow should have ended")
        }
        node3.database.transaction {
            assertEquals(0, node3.checkpointStorage.checkpoints().size, "Checkpoints left after restored flow should have ended")
        }
        assertEquals(payload2, firstAgain.receivedPayload, "Received payload does not match the first value on Node 3")
        assertEquals(payload2 + 1, firstAgain.receivedPayload2, "Received payload does not match the expected second value on Node 3")
        assertEquals(payload, secondFlow.getOrThrow().receivedPayload, "Received payload does not match the (restarted) first value on Node 2")
        assertEquals(payload + 1, secondFlow.getOrThrow().receivedPayload2, "Received payload does not match the expected second value on Node 2")
    }

    @Test
    fun `sending to multiple parties`() {
        val node3 = mockNet.createNode()
        mockNet.runNetwork()
        node2.registerFlowFactory(SendFlow::class) { InitiatedReceiveFlow(it).nonTerminating() }
        node3.registerFlowFactory(SendFlow::class) { InitiatedReceiveFlow(it).nonTerminating() }
        val payload = "Hello World"
        node1.services.startFlow(SendFlow(payload, node2.info.chooseIdentity(), node3.info.chooseIdentity()))
        mockNet.runNetwork()
        val node2Flow = node2.getSingleFlow<InitiatedReceiveFlow>().first
        val node3Flow = node3.getSingleFlow<InitiatedReceiveFlow>().first
        assertThat(node2Flow.receivedPayloads[0]).isEqualTo(payload)
        assertThat(node3Flow.receivedPayloads[0]).isEqualTo(payload)

        assertSessionTransfers(node2,
                node1 sent sessionInit(SendFlow::class, payload = payload) to node2,
                node2 sent sessionConfirm() to node1,
                node1 sent normalEnd to node2
                //There's no session end from the other flows as they're manually suspended
        )

        assertSessionTransfers(node3,
                node1 sent sessionInit(SendFlow::class, payload = payload) to node3,
                node3 sent sessionConfirm() to node1,
                node1 sent normalEnd to node3
                //There's no session end from the other flows as they're manually suspended
        )

        node2.internals.acceptableLiveFiberCountOnStop = 1
        node3.internals.acceptableLiveFiberCountOnStop = 1
    }

    @Test
    fun `receiving from multiple parties`() {
        val node3 = mockNet.createNode()
        mockNet.runNetwork()
        val node2Payload = "Test 1"
        val node3Payload = "Test 2"
        node2.registerFlowFactory(ReceiveFlow::class) { InitiatedSendFlow(node2Payload, it) }
        node3.registerFlowFactory(ReceiveFlow::class) { InitiatedSendFlow(node3Payload, it) }
        val multiReceiveFlow = ReceiveFlow(node2.info.chooseIdentity(), node3.info.chooseIdentity()).nonTerminating()
        node1.services.startFlow(multiReceiveFlow)
        node1.internals.acceptableLiveFiberCountOnStop = 1
        mockNet.runNetwork()
        assertThat(multiReceiveFlow.receivedPayloads[0]).isEqualTo(node2Payload)
        assertThat(multiReceiveFlow.receivedPayloads[1]).isEqualTo(node3Payload)

        assertSessionTransfers(node2,
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent sessionData(node2Payload) to node1,
                node2 sent normalEnd to node1
        )

        assertSessionTransfers(node3,
                node1 sent sessionInit(ReceiveFlow::class) to node3,
                node3 sent sessionConfirm() to node1,
                node3 sent sessionData(node3Payload) to node1,
                node3 sent normalEnd to node1
        )
    }

    @Test
    fun `both sides do a send as their first IO request`() {
        node2.registerFlowFactory(PingPongFlow::class) { PingPongFlow(it, 20L) }
        node1.services.startFlow(PingPongFlow(node2.info.chooseIdentity(), 10L))
        mockNet.runNetwork()

        assertSessionTransfers(
                node1 sent sessionInit(PingPongFlow::class, payload = 10L) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent sessionData(20L) to node1,
                node1 sent sessionData(11L) to node2,
                node2 sent sessionData(21L) to node1,
                node1 sent normalEnd to node2,
                node2 sent normalEnd to node1
        )
    }

    @Test
    fun `other side ends before doing expected send`() {
        node2.registerFlowFactory(ReceiveFlow::class) { NoOpFlow() }
        val resultFuture = node1.services.startFlow(ReceiveFlow(node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
            resultFuture.getOrThrow()
        }.withMessageContaining(String::class.java.name)  // Make sure the exception message mentions the type the flow was expecting to receive
    }

    @Test
    fun `receiving unexpected session end before entering sendAndReceive`() {
        node2.registerFlowFactory(WaitForOtherSideEndBeforeSendAndReceive::class) { NoOpFlow() }
        val sessionEndReceived = Semaphore(0)
        receivedSessionMessagesObservable().filter { it.message is SessionEnd }.subscribe { sessionEndReceived.release() }
        val resultFuture = node1.services.startFlow(
                WaitForOtherSideEndBeforeSendAndReceive(node2.info.chooseIdentity(), sessionEndReceived)).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
            resultFuture.getOrThrow()
        }
    }

    @InitiatingFlow
    private class WaitForOtherSideEndBeforeSendAndReceive(val otherParty: Party,
                                                          @Transient val receivedOtherFlowEnd: Semaphore) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Kick off the flow on the other side ...
            val session = initiateFlow(otherParty)
            session.send(1)
            // ... then pause this one until it's received the session-end message from the other side
            receivedOtherFlowEnd.acquire()
            session.sendAndReceive<Int>(2)
        }
    }

    @Test
    fun `non-FlowException thrown on other side`() {
        val erroringFlowFuture = node2.registerFlowFactory(ReceiveFlow::class) {
            ExceptionFlow { Exception("evil bug!") }
        }
        val erroringFlowSteps = erroringFlowFuture.flatMap { it.progressSteps }

        val receiveFlow = ReceiveFlow(node2.info.chooseIdentity())
        val receiveFlowSteps = receiveFlow.progressSteps
        val receiveFlowResult = node1.services.startFlow(receiveFlow).resultFuture

        mockNet.runNetwork()

        assertThat(erroringFlowSteps.get()).containsExactly(
                Notification.createOnNext(ExceptionFlow.START_STEP),
                Notification.createOnError(erroringFlowFuture.get().exceptionThrown)
        )

        val receiveFlowException = assertFailsWith(UnexpectedFlowEndException::class) {
            receiveFlowResult.getOrThrow()
        }
        assertThat(receiveFlowException.message).doesNotContain("evil bug!")
        assertThat(receiveFlowSteps.get()).containsExactly(
                Notification.createOnNext(ReceiveFlow.START_STEP),
                Notification.createOnError(receiveFlowException)
        )

        assertSessionTransfers(
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent erroredEnd() to node1
        )
    }

    @Test
    fun `FlowException thrown on other side`() {
        val erroringFlow = node2.registerFlowFactory(ReceiveFlow::class) {
            ExceptionFlow { MyFlowException("Nothing useful") }
        }
        val erroringFlowSteps = erroringFlow.flatMap { it.progressSteps }

        val receivingFiber = node1.services.startFlow(ReceiveFlow(node2.info.chooseIdentity())) as FlowStateMachineImpl

        mockNet.runNetwork()

        assertThatExceptionOfType(MyFlowException::class.java)
                .isThrownBy { receivingFiber.resultFuture.getOrThrow() }
                .withMessage("Nothing useful")
                .withStackTraceContaining(ReceiveFlow::class.java.name)  // Make sure the stack trace is that of the receiving flow
        node2.database.transaction {
            assertThat(node2.checkpointStorage.checkpoints()).isEmpty()
        }

        assertThat(receivingFiber.isTerminated).isTrue()
        assertThat((erroringFlow.get().stateMachine as FlowStateMachineImpl).isTerminated).isTrue()
        assertThat(erroringFlowSteps.get()).containsExactly(
                Notification.createOnNext(ExceptionFlow.START_STEP),
                Notification.createOnError(erroringFlow.get().exceptionThrown)
        )

        assertSessionTransfers(
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent erroredEnd(erroringFlow.get().exceptionThrown) to node1
        )
        // Make sure the original stack trace isn't sent down the wire
        assertThat((receivedSessionMessages.last().message as ErrorSessionEnd).errorResponse!!.stackTrace).isEmpty()
    }

    @Test
    fun `FlowException propagated in invocation chain`() {
        val node3 = mockNet.createNode()
        mockNet.runNetwork()

        node3.registerFlowFactory(ReceiveFlow::class) { ExceptionFlow { MyFlowException("Chain") } }
        node2.registerFlowFactory(ReceiveFlow::class) { ReceiveFlow(node3.info.chooseIdentity()) }
        val receivingFiber = node1.services.startFlow(ReceiveFlow(node2.info.chooseIdentity()))
        mockNet.runNetwork()
        assertThatExceptionOfType(MyFlowException::class.java)
                .isThrownBy { receivingFiber.resultFuture.getOrThrow() }
                .withMessage("Chain")
    }

    @Test
    fun `FlowException thrown and there is a 3rd unrelated party flow`() {
        val node3 = mockNet.createNode()
        mockNet.runNetwork()

        // Node 2 will send its payload and then block waiting for the receive from node 1. Meanwhile node 1 will move
        // onto node 3 which will throw the exception
        val node2Fiber = node2
                .registerFlowFactory(ReceiveFlow::class) { SendAndReceiveFlow(it, "Hello") }
                .map { it.stateMachine }
        node3.registerFlowFactory(ReceiveFlow::class) { ExceptionFlow { MyFlowException("Nothing useful") } }

        val node1Fiber = node1.services.startFlow(ReceiveFlow(node2.info.chooseIdentity(), node3.info.chooseIdentity())) as FlowStateMachineImpl
        mockNet.runNetwork()

        // Node 1 will terminate with the error it received from node 3 but it won't propagate that to node 2 (as it's
        // not relevant to it) but it will end its session with it
        assertThatExceptionOfType(MyFlowException::class.java).isThrownBy {
            node1Fiber.resultFuture.getOrThrow()
        }
        val node2ResultFuture = node2Fiber.getOrThrow().resultFuture
        assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
            node2ResultFuture.getOrThrow()
        }

        assertSessionTransfers(node2,
                node1 sent sessionInit(ReceiveFlow::class) to node2,
                node2 sent sessionConfirm() to node1,
                node2 sent sessionData("Hello") to node1,
                node1 sent erroredEnd() to node2
        )
    }

    private class ConditionalExceptionFlow(val otherPartySession: FlowSession, val sendPayload: Any) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val throwException = otherPartySession.receive<Boolean>().unwrap { it }
            if (throwException) {
                throw MyFlowException("Throwing exception as requested")
            }
            otherPartySession.send(sendPayload)
        }
    }

    @Test
    fun `retry subFlow due to receiving FlowException`() {
        @InitiatingFlow
        class AskForExceptionFlow(val otherParty: Party, val throwException: Boolean) : FlowLogic<String>() {
            @Suspendable
            override fun call(): String = initiateFlow(otherParty).sendAndReceive<String>(throwException).unwrap { it }
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

        node2.registerFlowFactory(AskForExceptionFlow::class) { ConditionalExceptionFlow(it, "Hello") }
        val resultFuture = node1.services.startFlow(RetryOnExceptionFlow(node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThat(resultFuture.getOrThrow()).isEqualTo("Hello")
    }

    @Test
    fun `serialisation issue in counterparty`() {
        node2.registerFlowFactory(ReceiveFlow::class) { InitiatedSendFlow(NonSerialisableData(1), it) }
        val result = node1.services.startFlow(ReceiveFlow(node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
            result.getOrThrow()
        }
    }

    @Test
    fun `FlowException has non-serialisable object`() {
        node2.registerFlowFactory(ReceiveFlow::class) {
            ExceptionFlow { NonSerialisableFlowException(NonSerialisableData(1)) }
        }
        val result = node1.services.startFlow(ReceiveFlow(node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(FlowException::class.java).isThrownBy {
            result.getOrThrow()
        }
    }

    @Test
    fun `wait for transaction`() {
        val ptx = TransactionBuilder(notary = notaryIdentity)
                .addOutputState(DummyState(), DummyContract.PROGRAM_ID)
                .addCommand(dummyCommand(node1.info.chooseIdentity().owningKey))
        val stx = node1.services.signInitialTransaction(ptx)

        val committerFiber = node1.registerFlowFactory(WaitingFlows.Waiter::class) {
            WaitingFlows.Committer(it)
        }.map { it.stateMachine }
        val waiterStx = node2.services.startFlow(WaitingFlows.Waiter(stx, node1.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThat(waiterStx.getOrThrow()).isEqualTo(committerFiber.getOrThrow().resultFuture.getOrThrow())
    }

    @Test
    fun `committer throws exception before calling the finality flow`() {
        val ptx = TransactionBuilder(notary = notaryIdentity)
                .addOutputState(DummyState(), DummyContract.PROGRAM_ID)
                .addCommand(dummyCommand())
        val stx = node1.services.signInitialTransaction(ptx)

        node1.registerFlowFactory(WaitingFlows.Waiter::class) {
            WaitingFlows.Committer(it) { throw Exception("Error") }
        }
        val waiter = node2.services.startFlow(WaitingFlows.Waiter(stx, node1.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
            waiter.getOrThrow()
        }
    }

    @Test
    fun `verify vault query service is tokenizable by force checkpointing within a flow`() {
        val ptx = TransactionBuilder(notary = notaryIdentity)
                .addOutputState(DummyState(), DummyContract.PROGRAM_ID)
                .addCommand(dummyCommand(node1.info.chooseIdentity().owningKey))
        val stx = node1.services.signInitialTransaction(ptx)

        node1.registerFlowFactory(VaultQueryFlow::class) {
            WaitingFlows.Committer(it)
        }
        val result = node2.services.startFlow(VaultQueryFlow(stx, node1.info.chooseIdentity())).resultFuture

        mockNet.runNetwork()
        assertThat(result.getOrThrow()).isEmpty()
    }

    @Test
    fun `customised client flow`() {
        val receiveFlowFuture = node2.registerFlowFactory(SendFlow::class) { InitiatedReceiveFlow(it) }
        node1.services.startFlow(CustomSendFlow("Hello", node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThat(receiveFlowFuture.getOrThrow().receivedPayloads).containsOnly("Hello")
    }

    @Test
    fun `customised client flow which has annotated @InitiatingFlow again`() {
        val result = node1.services.startFlow(IncorrectCustomSendFlow("Hello", node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            result.getOrThrow()
        }.withMessageContaining(InitiatingFlow::class.java.simpleName)
    }

    @Test
    fun `upgraded initiating flow`() {
        node2.registerFlowFactory(UpgradedFlow::class, initiatedFlowVersion = 1) { InitiatedSendFlow("Old initiated", it) }
        val result = node1.services.startFlow(UpgradedFlow(node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThat(receivedSessionMessages).startsWith(
                node1 sent sessionInit(UpgradedFlow::class, flowVersion = 2) to node2,
                node2 sent sessionConfirm(flowVersion = 1) to node1
        )
        val (receivedPayload, node2FlowVersion) = result.getOrThrow()
        assertThat(receivedPayload).isEqualTo("Old initiated")
        assertThat(node2FlowVersion).isEqualTo(1)
    }

    @Test
    fun `upgraded initiated flow`() {
        node2.registerFlowFactory(SendFlow::class, initiatedFlowVersion = 2) { UpgradedFlow(it) }
        val initiatingFlow = SendFlow("Old initiating", node2.info.chooseIdentity())
        val flowInfo = node1.services.startFlow(initiatingFlow).resultFuture
        mockNet.runNetwork()
        assertThat(receivedSessionMessages).startsWith(
                node1 sent sessionInit(SendFlow::class, flowVersion = 1, payload = "Old initiating") to node2,
                node2 sent sessionConfirm(flowVersion = 2) to node1
        )
        assertThat(flowInfo.get().flowVersion).isEqualTo(2)
    }

    @Test
    fun `unregistered flow`() {
        val future = node1.services.startFlow(SendFlow("Hello", node2.info.chooseIdentity())).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(UnexpectedFlowEndException::class.java)
                .isThrownBy { future.getOrThrow() }
                .withMessageEndingWith("${SendFlow::class.java.name} is not registered")
    }

    @Test
    fun `unknown class in session init`() {
        node1.sendSessionMessage(SessionInit(random63BitValue(), "not.a.real.Class", 1, "version", null), node2)
        mockNet.runNetwork()
        assertThat(receivedSessionMessages).hasSize(2) // Only the session-init and session-reject are expected
        val reject = receivedSessionMessages.last().message as SessionReject
        assertThat(reject.errorMessage).isEqualTo("Don't know not.a.real.Class")
    }

    @Test
    fun `non-flow class in session init`() {
        node1.sendSessionMessage(SessionInit(random63BitValue(), String::class.java.name, 1, "version", null), node2)
        mockNet.runNetwork()
        assertThat(receivedSessionMessages).hasSize(2) // Only the session-init and session-reject are expected
        val reject = receivedSessionMessages.last().message as SessionReject
        assertThat(reject.errorMessage).isEqualTo("${String::class.java.name} is not a flow")
    }

    @Test
    fun `single inlined sub-flow`() {
        node2.registerFlowFactory(SendAndReceiveFlow::class) { SingleInlinedSubFlow(it) }
        val result = node1.services.startFlow(SendAndReceiveFlow(node2.info.chooseIdentity(), "Hello")).resultFuture
        mockNet.runNetwork()
        assertThat(result.getOrThrow()).isEqualTo("HelloHello")
    }

    @Test
    fun `double inlined sub-flow`() {
        node2.registerFlowFactory(SendAndReceiveFlow::class) { DoubleInlinedSubFlow(it) }
        val result = node1.services.startFlow(SendAndReceiveFlow(node2.info.chooseIdentity(), "Hello")).resultFuture
        mockNet.runNetwork()
        assertThat(result.getOrThrow()).isEqualTo("HelloHello")
    }

    @Test
    fun `double initiateFlow throws`() {
        val future = node1.services.startFlow(DoubleInitiatingFlow()).resultFuture
        mockNet.runNetwork()
        assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { future.getOrThrow() }
                .withMessageContaining("Attempted to initiateFlow() twice")
    }

    @InitiatingFlow
    private class DoubleInitiatingFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            initiateFlow(serviceHub.myInfo.chooseIdentity())
            initiateFlow(serviceHub.myInfo.chooseIdentity())
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //region Helpers

    private inline fun <reified P : FlowLogic<*>> StartedNode<MockNode>.restartAndGetRestoredFlow() = internals.run {
        disableDBCloseOnStop() // Handover DB to new node copy
        stop()
        val newNode = mockNet.createNode(id)
        newNode.internals.acceptableLiveFiberCountOnStop = 1
        manuallyCloseDB()
        mockNet.runNetwork() // allow NetworkMapService messages to stabilise and thus start the state machine
        newNode.getSingleFlow<P>().first
    }

    private inline fun <reified P : FlowLogic<*>> StartedNode<*>.getSingleFlow(): Pair<P, CordaFuture<*>> {
        return smm.findStateMachines(P::class.java).single()
    }

    private inline fun <reified P : FlowLogic<*>> StartedNode<*>.registerFlowFactory(
            initiatingFlowClass: KClass<out FlowLogic<*>>,
            initiatedFlowVersion: Int = 1,
            noinline flowFactory: (FlowSession) -> P): CordaFuture<P> {
        val observable = internals.internalRegisterFlowFactory(
                initiatingFlowClass.java,
                InitiatedFlowFactory.CorDapp(initiatedFlowVersion, "", flowFactory),
                P::class.java,
                track = true)
        return observable.toFuture()
    }

    private fun sessionInit(clientFlowClass: KClass<out FlowLogic<*>>, flowVersion: Int = 1, payload: Any? = null): SessionInit {
        return SessionInit(0, clientFlowClass.java.name, flowVersion, "", payload?.serialize())
    }

    private fun sessionConfirm(flowVersion: Int = 1) = SessionConfirm(0, 0, flowVersion, "")
    private fun sessionData(payload: Any) = SessionData(0, payload.serialize())
    private val normalEnd = NormalSessionEnd(0)
    private fun erroredEnd(errorResponse: FlowException? = null) = ErrorSessionEnd(0, errorResponse)

    private fun StartedNode<*>.sendSessionMessage(message: SessionMessage, destination: StartedNode<*>) {
        services.networkService.apply {
            val address = getAddressOfParty(PartyInfo.SingleNode(destination.info.chooseIdentity(), emptyList()))
            send(createMessage(StateMachineManager.sessionTopic, message.serialize().bytes), address)
        }
    }

    private fun assertSessionTransfers(vararg expected: SessionTransfer) {
        assertThat(receivedSessionMessages).containsExactly(*expected)
    }

    private fun assertSessionTransfers(node: StartedNode<MockNode>, vararg expected: SessionTransfer): List<SessionTransfer> {
        val actualForNode = receivedSessionMessages.filter { it.from == node.internals.id || it.to == node.network.myAddress }
        assertThat(actualForNode).containsExactly(*expected)
        return actualForNode
    }

    private data class SessionTransfer(val from: Int, val message: SessionMessage, val to: MessageRecipients) {
        val isPayloadTransfer: Boolean get() = message is SessionData || message is SessionInit && message.firstPayload != null
        override fun toString(): String = "$from sent $message to $to"
    }

    private fun receivedSessionMessagesObservable(): Observable<SessionTransfer> {
        return mockNet.messagingNetwork.receivedMessages.toSessionTransfers()
    }

    private fun Observable<MessageTransfer>.toSessionTransfers(): Observable<SessionTransfer> {
        return filter { it.message.topicSession == StateMachineManager.sessionTopic }.map {
            val from = it.sender.id
            val message = it.message.data.deserialize<SessionMessage>()
            SessionTransfer(from, sanitise(message), it.recipients)
        }
    }

    private fun sanitise(message: SessionMessage) = when (message) {
        is SessionData -> message.copy(recipientSessionId = 0)
        is SessionInit -> message.copy(initiatorSessionId = 0, appName = "")
        is SessionConfirm -> message.copy(initiatorSessionId = 0, initiatedSessionId = 0, appName = "")
        is NormalSessionEnd -> message.copy(recipientSessionId = 0)
        is ErrorSessionEnd -> message.copy(recipientSessionId = 0)
        else -> message
    }

    private infix fun StartedNode<MockNode>.sent(message: SessionMessage): Pair<Int, SessionMessage> = Pair(internals.id, message)
    private infix fun Pair<Int, SessionMessage>.to(node: StartedNode<*>): SessionTransfer = SessionTransfer(first, second, node.network.myAddress)

    private val FlowLogic<*>.progressSteps: CordaFuture<List<Notification<ProgressTracker.Step>>>
        get() {
            return progressTracker!!.changes
                    .ofType(Change.Position::class.java)
                    .map { it.newStep }
                    .materialize()
                    .toList()
                    .toFuture()
        }

    private class LazyServiceHubAccessFlow : FlowLogic<Unit>() {
        val lazyTime: Instant by lazy { serviceHub.clock.instant() }
        @Suspendable
        override fun call() = Unit
    }

    private class NoOpFlow(val nonTerminating: Boolean = false) : FlowLogic<Unit>() {
        @Transient
        var flowStarted = false

        @Suspendable
        override fun call() {
            flowStarted = true
            if (nonTerminating) {
                Fiber.park()
            }
        }
    }

    @InitiatingFlow
    private open class SendFlow(val payload: Any, vararg val otherParties: Party) : FlowLogic<FlowInfo>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call(): FlowInfo {
            val flowInfos = otherParties.map {
                val session = initiateFlow(it)
                session.send(payload)
                session.getCounterpartyFlowInfo()
            }.toList()
            return flowInfos.first()
        }
    }

    private open class InitiatedSendFlow(val payload: Any, val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = otherPartySession.send(payload)
    }

    private interface CustomInterface

    private class CustomSendFlow(payload: String, otherParty: Party) : CustomInterface, SendFlow(payload, otherParty)

    @InitiatingFlow
    private class IncorrectCustomSendFlow(payload: String, otherParty: Party) : CustomInterface, SendFlow(payload, otherParty)

    @InitiatingFlow
    private class ReceiveFlow(vararg val otherParties: Party) : FlowLogic<Unit>() {
        object START_STEP : ProgressTracker.Step("Starting")
        object RECEIVED_STEP : ProgressTracker.Step("Received")

        init {
            require(otherParties.isNotEmpty())
        }

        override val progressTracker: ProgressTracker = ProgressTracker(START_STEP, RECEIVED_STEP)
        private var nonTerminating: Boolean = false
        @Transient
        var receivedPayloads: List<String> = emptyList()

        @Suspendable
        override fun call() {
            progressTracker.currentStep = START_STEP
            receivedPayloads = otherParties.map { initiateFlow(it).receive<String>().unwrap { it } }
            progressTracker.currentStep = RECEIVED_STEP
            if (nonTerminating) {
                Fiber.park()
            }
        }

        fun nonTerminating(): ReceiveFlow {
            nonTerminating = true
            return this
        }
    }

    private class InitiatedReceiveFlow(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        object START_STEP : ProgressTracker.Step("Starting")
        object RECEIVED_STEP : ProgressTracker.Step("Received")

        override val progressTracker: ProgressTracker = ProgressTracker(START_STEP, RECEIVED_STEP)
        private var nonTerminating: Boolean = false
        @Transient
        var receivedPayloads: List<String> = emptyList()

        @Suspendable
        override fun call() {
            progressTracker.currentStep = START_STEP
            receivedPayloads = listOf(otherPartySession.receive<String>().unwrap { it })
            progressTracker.currentStep = RECEIVED_STEP
            if (nonTerminating) {
                Fiber.park()
            }
        }

        fun nonTerminating(): InitiatedReceiveFlow {
            nonTerminating = true
            return this
        }
    }

    @InitiatingFlow
    private class SendAndReceiveFlow(val otherParty: Party, val payload: Any, val otherPartySession: FlowSession? = null) : FlowLogic<Any>() {
        constructor(otherPartySession: FlowSession, payload: Any) : this(otherPartySession.counterparty, payload, otherPartySession)

        @Suspendable
        override fun call(): Any = (otherPartySession ?: initiateFlow(otherParty)).sendAndReceive<Any>(payload).unwrap { it }
    }

    private class InlinedSendFlow(val payload: String, val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = otherPartySession.send(payload)
    }

    @InitiatingFlow
    private class PingPongFlow(val otherParty: Party, val payload: Long, val otherPartySession: FlowSession? = null) : FlowLogic<Unit>() {
        constructor(otherPartySession: FlowSession, payload: Long) : this(otherPartySession.counterparty, payload, otherPartySession)

        @Transient
        var receivedPayload: Long? = null
        @Transient
        var receivedPayload2: Long? = null

        @Suspendable
        override fun call() {
            val session = otherPartySession ?: initiateFlow(otherParty)
            receivedPayload = session.sendAndReceive<Long>(payload).unwrap { it }
            receivedPayload2 = session.sendAndReceive<Long>(payload + 1).unwrap { it }
        }
    }

    private class ExceptionFlow<E : Exception>(val exception: () -> E) : FlowLogic<Nothing>() {
        object START_STEP : ProgressTracker.Step("Starting")

        override val progressTracker: ProgressTracker = ProgressTracker(START_STEP)
        lateinit var exceptionThrown: E

        @Suspendable
        override fun call(): Nothing {
            progressTracker.currentStep = START_STEP
            exceptionThrown = exception()
            throw exceptionThrown
        }
    }

    private class MyFlowException(override val message: String) : FlowException() {
        override fun equals(other: Any?): Boolean = other is MyFlowException && other.message == this.message
        override fun hashCode(): Int = message.hashCode()
    }

    private object WaitingFlows {
        @InitiatingFlow
        class Waiter(val stx: SignedTransaction, val otherParty: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val otherPartySession = initiateFlow(otherParty)
                otherPartySession.send(stx)
                return waitForLedgerCommit(stx.id)
            }
        }

        class Committer(val otherPartySession: FlowSession, val throwException: (() -> Exception)? = null) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val stx = otherPartySession.receive<SignedTransaction>().unwrap { it }
                if (throwException != null) throw throwException.invoke()
                return subFlow(FinalityFlow(stx, setOf(otherPartySession.counterparty)))
            }
        }
    }

    @InitiatingFlow
    private class VaultQueryFlow(val stx: SignedTransaction, val otherParty: Party) : FlowLogic<List<StateAndRef<ContractState>>>() {
        @Suspendable
        override fun call(): List<StateAndRef<ContractState>> {
            val otherPartySession = initiateFlow(otherParty)
            otherPartySession.send(stx)
            // hold onto reference here to force checkpoint of vaultService and thus
            // prove it is registered as a tokenizableService in the node
            val vaultQuerySvc = serviceHub.vaultService
            waitForLedgerCommit(stx.id)
            return vaultQuerySvc.queryBy<ContractState>().states
        }
    }

    @InitiatingFlow(version = 2)
    private class UpgradedFlow(val otherParty: Party, val otherPartySession: FlowSession? = null) : FlowLogic<Pair<Any, Int>>() {
        constructor(otherPartySession: FlowSession) : this(otherPartySession.counterparty, otherPartySession)

        @Suspendable
        override fun call(): Pair<Any, Int> {
            val otherPartySession = this.otherPartySession ?: initiateFlow(otherParty)
            val received = otherPartySession.receive<Any>().unwrap { it }
            val otherFlowVersion = otherPartySession.getCounterpartyFlowInfo().flowVersion
            return Pair(received, otherFlowVersion)
        }
    }

    private class SingleInlinedSubFlow(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val payload = otherPartySession.receive<String>().unwrap { it }
            subFlow(InlinedSendFlow(payload + payload, otherPartySession))
        }
    }

    private class DoubleInlinedSubFlow(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(SingleInlinedSubFlow(otherPartySession))
        }
    }

    private data class NonSerialisableData(val a: Int)
    private class NonSerialisableFlowException(@Suppress("unused") val data: NonSerialisableData) : FlowException()

    //endregion Helpers
}
