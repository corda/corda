package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowExternalOperation
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.MessageRecipients
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.node.services.messaging.Message
import net.corda.testing.core.*
import net.corda.testing.flows.registerCordappFlowFactory
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MessagingServiceSpy
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class FlowOperatorTests {

    companion object {
        val log = contextLogger()
        val EUGENE_NAME = CordaX500Name("Eugene", "EugeneCorp", "GB")
    }

    lateinit var mockNet: InternalMockNetwork
    lateinit var aliceNode: TestStartedNode
    private lateinit var aliceParty: Party
    lateinit var bobNode: TestStartedNode
    private lateinit var bobParty: Party
    lateinit var charlieNode: TestStartedNode
    private lateinit var charlieParty: Party
    lateinit var daveNode: TestStartedNode
    lateinit var daveParty: Party
    private lateinit var eugeneNode: TestStartedNode
    private lateinit var eugeneParty: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(
                threadPerNode = true,
                cordappsForAllNodes = listOf(enclosedCordapp())
        )
        aliceNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = ALICE_NAME
        ))
        bobNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = BOB_NAME
        ))
        charlieNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = CHARLIE_NAME
        ))
        daveNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = DAVE_NAME
        ))
        eugeneNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = EUGENE_NAME
        ))
        mockNet.startNodes()
        aliceParty = aliceNode.info.legalIdentities.first()
        bobParty = bobNode.info.legalIdentities.first()
        charlieParty = charlieNode.info.legalIdentities.first()
        daveParty = daveNode.info.legalIdentities.first()
        eugeneParty = eugeneNode.info.legalIdentities.first()

        // put nodes offline, alice and charlie are staying online
        bobNode.dispose()
        daveNode.dispose()
        eugeneNode.dispose()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return all flows which are waiting for other party to process`() {
        charlieNode.registerCordappFlowFactory(TestReceiveInitiatingFlow::class) { TestAcceptingFlow("Hello", it) }

        val bobStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        val daveStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))
        charlieNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(charlieParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty))

        assertEquals(2, result1.size)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertNull(result1.last().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.last().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(1, result1.last().waitingForParties.size)
        val firstName = result1.first().waitingForParties.first().party.name
        val lastName = result1.last().waitingForParties.first().party.name
        assertNotEquals(firstName, lastName)
        if(firstName == BOB_NAME) {
            assertEquals(bobStart.id, result1.first().id)
            assertEquals(daveStart.id, result1.last().id)
            assertEquals(DAVE_NAME, lastName)
        } else {
            assertEquals(bobStart.id, result1.last().id)
            assertEquals(daveStart.id, result1.first().id)
            assertEquals(DAVE_NAME, firstName)
            assertEquals(BOB_NAME, lastName)
        }
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return only requested by party flows which are waiting for other party to process`() {
        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        val daveStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(daveParty))

        assertEquals(1, result1.size)
        assertEquals(daveStart.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(DAVE_NAME, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return all parties in a flow which are waiting for other parties to process`() {
        val start = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty, daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty))

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(2, result1.first().waitingForParties.size)
        val firstName = result1.first().waitingForParties.first().party.name
        val lastName = result1.first().waitingForParties.last().party.name
        assertNotEquals(firstName, lastName)
        if(firstName == BOB_NAME) {
            assertEquals(DAVE_NAME, lastName)
        } else {
            assertEquals(DAVE_NAME, firstName)
            assertEquals(BOB_NAME, lastName)
        }
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return only flows which are waiting for other party to process and not in the hospital`() {
        charlieNode.registerCordappFlowFactory(TestReceiveInitiatingFlow::class) { TestAcceptingFlow("Fail", it) }

        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(charlieParty)))
        val daveStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(bobParty, daveParty))

        assertEquals(1, result1.size)
        assertEquals(daveStart.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(DAVE_NAME, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return only flows which are waiting more than 4 seconds for other party to process`() {
        val bobStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        Thread.sleep(4500) // let time to settle all flow states properly
        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty), 4.seconds)

        assertEquals(1, result1.size)
        assertEquals(bobStart.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(BOB_NAME, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForPartiesGrouped should return all flows which are waiting for other party to process grouped by party`() {
        val bobStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        val daveStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForPartiesGrouped(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty))

        assertEquals(2, result1.size)
        assertEquals(1, result1.getValue(bobParty).size)
        assertNull(result1.getValue(bobParty).first().externalOperationImplName)
        assertEquals(bobStart.id, result1.getValue(bobParty).first().id)
        assertEquals(WaitingSource.RECEIVE, result1.getValue(bobParty).first().source)
        assertEquals(1, result1.getValue(bobParty).first().waitingForParties.size)
        assertEquals(BOB_NAME, result1.getValue(bobParty).first().waitingForParties.first().party.name)
        assertEquals(1, result1.getValue(daveParty).size)
        assertEquals(daveStart.id, result1.getValue(daveParty).first().id)
        assertNull(result1.getValue(daveParty).first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.getValue(daveParty).first().source)
        assertEquals(1, result1.getValue(daveParty).first().waitingForParties.size)
        assertEquals(DAVE_NAME, result1.getValue(daveParty).first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `getWaitingFlows should return all flow state machines which are waiting for other party to process`() {
        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getWaitingFlows().toList()
        assertEquals(2, result1.size)
    }

    @Test(timeout=300_000)
    fun `getWaitingFlows should return only requested by id flows which are waiting for other party to process`() {
        charlieNode.registerCordappFlowFactory(TestReceiveInitiatingFlow::class) { TestAcceptingFlow("Fail", it) }

        val charlieStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(charlieParty)))
        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))
        val eugeneStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getWaitingFlows(listOf(charlieStart.id, eugeneStart.id)).toList()

        assertEquals(1, result1.size)
        assertEquals(eugeneStart.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(EUGENE_NAME, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for getting info about other party`() {
        val start = aliceNode.services.startFlow(TestGetFlowInitiatingFlow(listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty))

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.GET_FLOW_INFO, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(EUGENE_NAME, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for sending and receiving from other party when stuck in remote party`() {
        val start = aliceNode.services.startFlow(TestSendAndReceiveInitiatingFlow("Hello", listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty))

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source) // yep, it's receive
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(EUGENE_NAME, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for sending and receiving from other party when stuck in sending`() {
        val future = CompletableFuture<Unit>()
        aliceNode.setMessagingServiceSpy(BlockingMessageSpy("PauseSend", future))

        val start = aliceNode.services.startFlow(TestSendAndReceiveInitiatingFlow("PauseSend", listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty))

        future.complete(Unit)

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.SEND_AND_RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(EUGENE_NAME, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for async external operations`() {
        val future = CompletableFuture<String>()
        val start = aliceNode.services.startFlow(TestExternalAsyncOperationInitiatingFlow(future))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf()) // the list must be empty to get any external operation

        future.complete("Hello")

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertEquals(TestExternalAsyncOperationInitiatingFlow.ExternalOperation::class.java.canonicalName, result1.first().externalOperationImplName)
        assertEquals(WaitingSource.EXTERNAL_OPERATION, result1.first().source)
        assertEquals(0, result1.first().waitingForParties.size)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for external operations`() {
        val future = CompletableFuture<String>()
        val start = aliceNode.services.startFlow(TestExternalOperationInitiatingFlow(future))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf()) // the list must be empty to get any external operation

        future.complete("Hello")

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertEquals(TestExternalOperationInitiatingFlow.ExternalOperation::class.java.canonicalName, result1.first().externalOperationImplName)
        assertEquals(WaitingSource.EXTERNAL_OPERATION, result1.first().source)
        assertEquals(0, result1.first().waitingForParties.size)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are sleeping`() {
        val start = aliceNode.services.startFlow(TestSleepingInitiatingFlow())

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf()) // the list must be empty to get any external operation

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.SLEEP, result1.first().source)
        assertEquals(0, result1.first().waitingForParties.size)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for sending from other party`() {
        val future = CompletableFuture<Unit>()
        aliceNode.setMessagingServiceSpy(BlockingMessageSpy("PauseSend", future))

        val start = aliceNode.services.startFlow(TestSendInitiatingFlow("PauseSend", listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, charlieParty, daveParty, eugeneParty))

        future.complete(Unit)

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.SEND, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(EUGENE_NAME, result1.first().waitingForParties.first().party.name)
    }

    @InitiatingFlow
    class TestReceiveInitiatingFlow(private val payload: String, private val otherParties: List<Party>) : FlowLogic<Unit>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call() {
            if(payload == "Fail") {
                error(payload)
            }

            val sessions = mutableMapOf<FlowSession, Class<out Any>>()
            otherParties.forEach {
                sessions[initiateFlow(it)] = String::class.java
            }

            receiveAllMap(sessions)
        }
    }

    @InitiatingFlow
    class TestSendAndReceiveInitiatingFlow(private val payload: String, private val otherParties: List<Party>) : FlowLogic<Unit>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call() {
            if(payload == "Fail") {
                error(payload)
            }
            otherParties.forEach {
                val session = initiateFlow(it)
                session.sendAndReceive<String>(payload)
            }
        }
    }

    @InitiatingFlow
    class TestGetFlowInitiatingFlow(private val otherParties: List<Party>) : FlowLogic<FlowInfo>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call(): FlowInfo {
            val flowInfo = otherParties.map {
                val session = initiateFlow(it)
                session.getCounterpartyFlowInfo()
            }.toList()
            return flowInfo.first()
        }
    }

    @InitiatingFlow
    class TestExternalAsyncOperationInitiatingFlow(private val future: CompletableFuture<String>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            await(ExternalOperation(future))
        }

        class ExternalOperation(private val future: CompletableFuture<String>) : FlowExternalAsyncOperation<String> {
            override fun execute(deduplicationId: String): CompletableFuture<String> {
                return future
            }
        }
    }

    @InitiatingFlow
    class TestExternalOperationInitiatingFlow(private val future: CompletableFuture<String>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            await(ExternalOperation(future))
        }

        class ExternalOperation(private val future: CompletableFuture<String>) : FlowExternalOperation<String> {
            override fun execute(deduplicationId: String): String {
                return future.get()
            }
        }
    }

    @InitiatingFlow
    class TestSleepingInitiatingFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            sleep(15.seconds)
        }
    }

    @InitiatingFlow
    class TestSendInitiatingFlow(private val payload: String, private val otherParties: List<Party>) : FlowLogic<Unit>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call() {
            if(payload == "Fail") {
                error(payload)
            }
            otherParties.forEach {
                initiateFlow(it).send(payload)
            }
        }
    }

    class TestAcceptingFlow(private val payload: Any, private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            if(payload == "Fail") {
                error(payload)
            }

            otherPartySession.send(payload)
        }
    }

    class BlockingMessageSpy(
            private val expectedPayload: String,
            private val future: CompletableFuture<Unit>
    ) : MessagingServiceSpy() {
        @Suppress("TooGenericExceptionCaught")
        override fun send(message: Message, target: MessageRecipients, sequenceKey: Any) {
            try {
                val sessionMessage = message.data.bytes.deserialize<InitialSessionMessage>()
                if (sessionMessage.firstPayload?.deserialize<String>() == expectedPayload) {
                    future.get()
                }
            } catch (e: Throwable) {
                // Intentional
            }
            messagingService.send(message, target)
        }
    }
}