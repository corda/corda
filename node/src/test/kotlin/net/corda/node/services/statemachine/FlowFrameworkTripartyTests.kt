package net.corda.node.services.statemachine

import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.flows.registerCordappFlowFactory
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.util.*

class FlowFrameworkTripartyTests {
    companion object {
        init {
            LogHelper.setLevel("+net.corda.flow")
        }

        private lateinit var mockNet: InternalMockNetwork
        private lateinit var aliceNode: TestStartedNode
        private lateinit var bobNode: TestStartedNode
        private lateinit var charlieNode: TestStartedNode
        private lateinit var alice: Party
        private lateinit var bob: Party
        private lateinit var charlie: Party
        private lateinit var notaryIdentity: Party
        private val receivedSessionMessages = ArrayList<SessionTransfer>()
    }

    @Before
    fun setUpGlobalMockNet() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()
        )

        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        charlieNode = mockNet.createNode(InternalMockNodeParameters(legalName = CHARLIE_NAME))


        // Extract identities
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notaryIdentity = mockNet.defaultNotaryIdentity

        receivedSessionMessagesObservable().forEach { receivedSessionMessages += it }
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        receivedSessionMessages.clear()
    }

    private fun receivedSessionMessagesObservable(): Observable<SessionTransfer> {
        return mockNet.messagingNetwork.receivedMessages.toSessionTransfers()
    }

    @Test
    fun `sending to multiple parties`() {
        bobNode.registerCordappFlowFactory(SendFlow::class) { InitiatedReceiveFlow(it)
                .nonTerminating() }
        charlieNode.registerCordappFlowFactory(SendFlow::class) { InitiatedReceiveFlow(it)
                .nonTerminating() }
        val payload = "Hello World"
        aliceNode.services.startFlow(SendFlow(payload, bob, charlie))
        mockNet.runNetwork()
        bobNode.internals.acceptableLiveFiberCountOnStop = 1
        charlieNode.internals.acceptableLiveFiberCountOnStop = 1
        val bobFlow = bobNode.getSingleFlow<InitiatedReceiveFlow>().first
        val charlieFlow = charlieNode.getSingleFlow<InitiatedReceiveFlow>().first
        assertThat(bobFlow.receivedPayloads[0]).isEqualTo(payload)
        assertThat(charlieFlow.receivedPayloads[0]).isEqualTo(payload)

        assertSessionTransfers(bobNode,
                aliceNode sent sessionInit(SendFlow::class, payload = payload) to bobNode,
                bobNode sent sessionConfirm() to aliceNode,
                aliceNode sent normalEnd to bobNode
                //There's no session end from the other flows as they're manually suspended
        )

        assertSessionTransfers(charlieNode,
                aliceNode sent sessionInit(SendFlow::class, payload = payload) to charlieNode,
                charlieNode sent sessionConfirm() to aliceNode,
                aliceNode sent normalEnd to charlieNode
                //There's no session end from the other flows as they're manually suspended
        )
    }

    @Test
    fun `receiving from multiple parties`() {
        val bobPayload = "Test 1"
        val charliePayload = "Test 2"
        bobNode.registerCordappFlowFactory(ReceiveFlow::class) { InitiatedSendFlow(bobPayload, it) }
        charlieNode.registerCordappFlowFactory(ReceiveFlow::class) { InitiatedSendFlow(charliePayload, it) }
        val multiReceiveFlow = ReceiveFlow(bob, charlie).nonTerminating()
        aliceNode.services.startFlow(multiReceiveFlow)
        aliceNode.internals.acceptableLiveFiberCountOnStop = 1
        mockNet.runNetwork()
        assertThat(multiReceiveFlow.receivedPayloads[0]).isEqualTo(bobPayload)
        assertThat(multiReceiveFlow.receivedPayloads[1]).isEqualTo(charliePayload)

        assertSessionTransfers(bobNode,
                aliceNode sent sessionInit(ReceiveFlow::class) to bobNode,
                bobNode sent sessionConfirm() to aliceNode,
                bobNode sent sessionData(bobPayload) to aliceNode,
                bobNode sent normalEnd to aliceNode
        )

        assertSessionTransfers(charlieNode,
                aliceNode sent sessionInit(ReceiveFlow::class) to charlieNode,
                charlieNode sent sessionConfirm() to aliceNode,
                charlieNode sent sessionData(charliePayload) to aliceNode,
                charlieNode sent normalEnd to aliceNode
        )
    }

    @Test
    fun `FlowException only propagated to parent`() {
        charlieNode.registerCordappFlowFactory(ReceiveFlow::class) { ExceptionFlow { MyFlowException("Chain") } }
        bobNode.registerCordappFlowFactory(ReceiveFlow::class) { ReceiveFlow(charlie) }
        val receivingFiber = aliceNode.services.startFlow(ReceiveFlow(bob))
        mockNet.runNetwork()
        AssertionsForClassTypes.assertThatExceptionOfType(UnexpectedFlowEndException::class.java)
                .isThrownBy { receivingFiber.resultFuture.getOrThrow() }
    }

    @Test
    fun `FlowException thrown and there is a 3rd unrelated party flow`() {
        // Bob will send its payload and then block waiting for the receive from Alice. Meanwhile Alice will move
        // onto Charlie which will throw the exception
        val node2Fiber = bobNode
                .registerCordappFlowFactory(ReceiveFlow::class) { SendAndReceiveFlow(it, "Hello") }
                .map { it.stateMachine }
        charlieNode.registerCordappFlowFactory(ReceiveFlow::class) { ExceptionFlow { MyFlowException("Nothing useful") } }

        val aliceFiber = aliceNode.services.startFlow(ReceiveFlow(bob, charlie)) as FlowStateMachineImpl
        mockNet.runNetwork()

        // Alice will terminate with the error it received from Charlie but it won't propagate that to Bob (as it's
        // not relevant to it) but it will end its session with it
        AssertionsForClassTypes.assertThatExceptionOfType(MyFlowException::class.java)
                .isThrownBy {
            aliceFiber.resultFuture.getOrThrow()
        }
        val bobResultFuture = node2Fiber.getOrThrow().resultFuture
        AssertionsForClassTypes.assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
            bobResultFuture.getOrThrow()
        }

        assertSessionTransfers(bobNode,
                aliceNode sent sessionInit(ReceiveFlow::class) to bobNode,
                bobNode sent sessionConfirm() to aliceNode,
                bobNode sent sessionData("Hello") to aliceNode,
                aliceNode sent errorMessage() to bobNode
        )
    }

    private val normalEnd = ExistingSessionMessage(SessionId(0), EndSessionMessage) // NormalSessionEnd(0)

    private fun assertSessionTransfers(node: TestStartedNode, vararg expected: SessionTransfer): List<SessionTransfer> {
        val actualForNode = receivedSessionMessages.filter { it.from == node.internals.id || it.to == node.network.myAddress }
        assertThat(actualForNode).containsExactly(*expected)
        return actualForNode
    }
}
