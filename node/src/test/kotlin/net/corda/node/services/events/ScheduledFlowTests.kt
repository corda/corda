package net.corda.node.services.events

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationOrigin
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.SchedulableFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.MockNode
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals

class ScheduledFlowTests {
    companion object {
        const val PAGE_SIZE = 20
        val SORTING = Sort(listOf(Sort.SortColumn(SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_TXN_ID), Sort.Direction.DESC)))
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: StartedNode<MockNode>
    private lateinit var bobNode: StartedNode<MockNode>
    private lateinit var notary: Party
    private lateinit var alice: Party
    private lateinit var bob: Party

    data class ScheduledState(val creationTime: Instant,
                              val source: Party,
                              val destination: Party,
                              val processed: Boolean = false,
                              override val linearId: UniqueIdentifier = UniqueIdentifier()) : SchedulableState, LinearState {
        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            return if (!processed) {
                val logicRef = flowLogicRefFactory.create(ScheduledFlow::class.jvmName, thisStateRef)
                ScheduledActivity(logicRef, creationTime)
            } else {
                null
            }
        }

        override val participants: List<Party> get() = listOf(source, destination)
    }

    class InsertInitialStateFlow(private val destination: Party, private val notary: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val scheduledState = ScheduledState(serviceHub.clock.instant(), ourIdentity, destination)
            val builder = TransactionBuilder(notary)
                    .addOutputState(scheduledState, DummyContract.PROGRAM_ID)
                    .addCommand(dummyCommand(ourIdentity.owningKey))
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx))
        }
    }

    @SchedulableFlow
    class ScheduledFlow(private val stateRef: StateRef) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val state = serviceHub.toStateAndRef<ScheduledState>(stateRef)
            val scheduledState = state.state.data
            // Only run flow over states originating on this node
            if (!serviceHub.myInfo.isLegalIdentity(scheduledState.source)) {
                return
            }
            require(!scheduledState.processed) { "State should not have been previously processed" }
            val notary = state.state.notary
            val newStateOutput = scheduledState.copy(processed = true)
            val builder = TransactionBuilder(notary)
                    .addInputState(state)
                    .addOutputState(newStateOutput, DummyContract.PROGRAM_ID)
                    .addCommand(dummyCommand(ourIdentity.owningKey))
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx, setOf(scheduledState.destination)))
        }
    }

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.testing.contracts"), threadPerNode = true)
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `create and run scheduled flow then wait for result`() {
        var countScheduledFlows = 0
        aliceNode.smm.track().updates.subscribe {
            if (it is StateMachineManager.Change.Add) {
                val context = it.logic.stateMachine.context
                if (context.origin is InvocationOrigin.Scheduled)
                    countScheduledFlows++
            }
        }
        aliceNode.services.startFlow(InsertInitialStateFlow(bob, notary))
        mockNet.waitQuiescent()
        val stateFromA = aliceNode.database.transaction {
            aliceNode.services.vaultService.queryBy<ScheduledState>().states.single()
        }
        val stateFromB = bobNode.database.transaction {
            bobNode.services.vaultService.queryBy<ScheduledState>().states.single()
        }
        assertEquals(1, countScheduledFlows)
        assertEquals("Must be same copy on both nodes", stateFromA, stateFromB)
        assertTrue("Must be processed", stateFromB.state.data.processed)
    }

    @Test
    fun `run a whole batch of scheduled flows`() {
        val N = 99
        val futures = mutableListOf<CordaFuture<*>>()
        for (i in 0 until N) {
            futures.add(aliceNode.services.startFlow(InsertInitialStateFlow(bob, notary)).resultFuture)
            futures.add(bobNode.services.startFlow(InsertInitialStateFlow(alice, notary)).resultFuture)
        }
        mockNet.waitQuiescent()

        // Check all of the flows completed successfully
        futures.forEach { it.getOrThrow() }

        // Convert the states into maps to make error reporting easier
        val statesFromA: List<StateAndRef<ScheduledState>> = aliceNode.database.transaction {
            queryStates(aliceNode.services.vaultService)
        }
        val statesFromB: List<StateAndRef<ScheduledState>> = bobNode.database.transaction {
            queryStates(bobNode.services.vaultService)
        }
        assertEquals("Expect all states to be present", 2 * N, statesFromA.count())
        statesFromA.forEach { ref ->
            if (ref !in statesFromB) {
                throw IllegalStateException("State $ref is only present on node A.")
            }
        }
        statesFromB.forEach { ref ->
            if (ref !in statesFromA) {
                throw IllegalStateException("State $ref is only present on node B.")
            }
        }
        assertEquals("Expect identical data on both nodes", statesFromA, statesFromB)
        assertTrue("Expect all states have run the scheduled task", statesFromB.all { it.state.data.processed })
    }

    private fun queryStates(vaultService: VaultService): List<StateAndRef<ScheduledState>> =
        vaultService.queryBy<ScheduledState>(VaultQueryCriteria(), sorting = SORTING).states
}
