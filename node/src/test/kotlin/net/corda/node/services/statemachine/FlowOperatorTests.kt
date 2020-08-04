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
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.testing.flows.registerCordappFlowFactory
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
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
        val aliceX500Name = CordaX500Name("Alice", "AliceCorp", "GB")
        val bobX500Name = CordaX500Name("Bob", "BobCorp", "GB")
        val carolX500Name = CordaX500Name("Carol", "CarolCorp", "GB")
        val daveX500Name = CordaX500Name("Dave", "DaveCorp", "GB")
        val eugeneX500Name = CordaX500Name("Offline", "OfflineCorp", "GB")
    }

    lateinit var mockNet: InternalMockNetwork
    lateinit var aliceNode: TestStartedNode
    private lateinit var aliceParty: Party
    lateinit var bobNode: TestStartedNode
    private lateinit var bobParty: Party
    lateinit var carolNode: TestStartedNode
    private lateinit var carolParty: Party
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
                legalName = aliceX500Name
        ))
        bobNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = bobX500Name
        ))
        carolNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = carolX500Name
        ))
        daveNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = daveX500Name
        ))
        eugeneNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = eugeneX500Name
        ))
        mockNet.startNodes()
        aliceParty = aliceNode.info.legalIdentities.first()
        bobParty = bobNode.info.legalIdentities.first()
        carolParty = carolNode.info.legalIdentities.first()
        daveParty = daveNode.info.legalIdentities.first()
        eugeneParty = eugeneNode.info.legalIdentities.first()

        // put nodes offline, alice and carol are staying online
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
        carolNode.registerCordappFlowFactory(TestReceiveInitiatingFlow::class) { TestAcceptingFlow("Hello", it) }

        val bobStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        val daveStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))
        carolNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(carolParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(1000) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, carolParty, daveParty, eugeneParty))

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
        if(firstName == bobX500Name) {
            assertEquals(bobStart.id, result1.first().id)
            assertEquals(daveStart.id, result1.last().id)
            assertEquals(daveX500Name, lastName)
        } else {
            assertEquals(bobStart.id, result1.last().id)
            assertEquals(daveStart.id, result1.first().id)
            assertEquals(daveX500Name, firstName)
            assertEquals(bobX500Name, lastName)
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
        assertEquals(daveX500Name, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return all parties in a flow which are waiting for other parties to process`() {
        val start = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty, daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(1000) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, carolParty, daveParty, eugeneParty))

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(2, result1.first().waitingForParties.size)
        val firstName = result1.first().waitingForParties.first().party.name
        val lastName = result1.first().waitingForParties.last().party.name
        assertNotEquals(firstName, lastName)
        if(firstName == bobX500Name) {
            assertEquals(daveX500Name, lastName)
        } else {
            assertEquals(daveX500Name, firstName)
            assertEquals(bobX500Name, lastName)
        }
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return only flows which are waiting for other party to process and not in the hospital`() {
        carolNode.registerCordappFlowFactory(TestReceiveInitiatingFlow::class) { TestAcceptingFlow("Fail", it) }

        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(carolParty)))
        val daveStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(bobParty, daveParty))

        assertEquals(1, result1.size)
        assertEquals(daveStart.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(daveX500Name, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForParties should return only flows which are waiting more than 4 seconds for other party to process`() {
        val bobStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        Thread.sleep(4500) // let time to settle all flow states properly
        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, carolParty, daveParty, eugeneParty), 4.seconds)

        assertEquals(1, result1.size)
        assertEquals(bobStart.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(bobX500Name, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `getFlowsCurrentlyWaitingForPartiesGrouped should return all flows which are waiting for other party to process grouped by party`() {
        val bobStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(bobParty)))
        val daveStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForPartiesGrouped(listOf(aliceParty, bobParty, carolParty, daveParty, eugeneParty))

        assertEquals(2, result1.size)
        assertEquals(1, result1.getValue(bobParty).size)
        assertNull(result1.getValue(bobParty).first().externalOperationImplName)
        assertEquals(bobStart.id, result1.getValue(bobParty).first().id)
        assertEquals(WaitingSource.RECEIVE, result1.getValue(bobParty).first().source)
        assertEquals(1, result1.getValue(bobParty).first().waitingForParties.size)
        assertEquals(bobX500Name, result1.getValue(bobParty).first().waitingForParties.first().party.name)
        assertEquals(1, result1.getValue(daveParty).size)
        assertEquals(daveStart.id, result1.getValue(daveParty).first().id)
        assertNull(result1.getValue(daveParty).first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.getValue(daveParty).first().source)
        assertEquals(1, result1.getValue(daveParty).first().waitingForParties.size)
        assertEquals(daveX500Name, result1.getValue(daveParty).first().waitingForParties.first().party.name)
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
        carolNode.registerCordappFlowFactory(TestReceiveInitiatingFlow::class) { TestAcceptingFlow("Fail", it) }

        val carolStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(carolParty)))
        aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(daveParty)))
        val eugeneStart = aliceNode.services.startFlow(TestReceiveInitiatingFlow("Hello", listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getWaitingFlows(listOf(carolStart.id, eugeneStart.id)).toList()

        assertEquals(1, result1.size)
        assertEquals(eugeneStart.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(eugeneX500Name, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for getting info about other party`() {
        val start = aliceNode.services.startFlow(TestGetFlowInitiatingFlow(listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, carolParty, daveParty, eugeneParty))

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.GET_FLOW_INFO, result1.first().source)
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(eugeneX500Name, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for sending and receiving from other party`() {
        val start = aliceNode.services.startFlow(TestSendAndReceiveInitiatingFlow("Hello", listOf(eugeneParty)))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, carolParty, daveParty, eugeneParty))

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertNull(result1.first().externalOperationImplName)
        assertEquals(WaitingSource.RECEIVE, result1.first().source) // yep, it's receive
        assertEquals(1, result1.first().waitingForParties.size)
        assertEquals(eugeneX500Name, result1.first().waitingForParties.first().party.name)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for async external operations`() {
        val future = CompletableFuture<String>()
        val start = aliceNode.services.startFlow(TestExternalAsyncOperationInitiatingFlow(future))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf()) // the list must be empty to get any external operation

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertEquals(TestExternalAsyncOperationInitiatingFlow.ExternalOperation::class.java.canonicalName, result1.first().externalOperationImplName)
        assertEquals(WaitingSource.ASYNC_OPERATION, result1.first().source)
        assertEquals(0, result1.first().waitingForParties.size)
    }

    @Test(timeout=300_000)
    fun `should return all flows which are waiting for external operations`() {
        val future = CompletableFuture<String>()
        val start = aliceNode.services.startFlow(TestExternalOperationInitiatingFlow(future))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(500) // let time to settle all flow states properly

        val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf()) // the list must be empty to get any external operation

        assertEquals(1, result1.size)
        assertEquals(start.id, result1.first().id)
        assertEquals(TestExternalOperationInitiatingFlow.ExternalOperation::class.java.canonicalName, result1.first().externalOperationImplName)
        assertEquals(WaitingSource.ASYNC_OPERATION, result1.first().source)
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

    class TestAcceptingFlow(private val payload: Any, private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            if(payload == "Fail") {
                error(payload)
            }

            otherPartySession.send(payload)
        }
    }
}