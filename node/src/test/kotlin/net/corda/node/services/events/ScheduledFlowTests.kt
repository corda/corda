package net.corda.node.services.events

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.testing.contracts.DummyContract
import net.corda.core.crypto.containsAny
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.SchedulableFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.linearHeadsOfType
import net.corda.testing.DUMMY_NOTARY
import net.corda.flows.FinalityFlow
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.utilities.transaction
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertEquals

class ScheduledFlowTests {
    lateinit var mockNet: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var nodeA: MockNetwork.MockNode
    lateinit var nodeB: MockNetwork.MockNode

    data class ScheduledState(val creationTime: Instant,
                              val source: Party,
                              val destination: Party,
                              val processed: Boolean = false,
                              override val linearId: UniqueIdentifier = UniqueIdentifier(),
                              override val contract: Contract = DummyContract()) : SchedulableState, LinearState {
        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            if (!processed) {
                val logicRef = flowLogicRefFactory.create(ScheduledFlow::class.java, thisStateRef)
                return ScheduledActivity(logicRef, creationTime)
            } else {
                return null
            }
        }

        override val participants: List<AbstractParty> = listOf(source, destination)

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.owningKey.containsAny(ourKeys) }
        }
    }

    class InsertInitialStateFlow(val destination: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val scheduledState = ScheduledState(serviceHub.clock.instant(),
                    serviceHub.myInfo.legalIdentity, destination)

            val notary = serviceHub.networkMapCache.getAnyNotary()
            val builder = TransactionType.General.Builder(notary)
            builder.withItems(scheduledState)
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx, setOf(serviceHub.myInfo.legalIdentity)))
        }
    }

    @SchedulableFlow
    class ScheduledFlow(val stateRef: StateRef) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val state = serviceHub.toStateAndRef<ScheduledState>(stateRef)
            val scheduledState = state.state.data
            // Only run flow over states originating on this node
            if (scheduledState.source != serviceHub.myInfo.legalIdentity) {
                return
            }
            require(!scheduledState.processed) { "State should not have been previously processed" }
            val notary = state.state.notary
            val newStateOutput = scheduledState.copy(processed = true)
            val builder = TransactionType.General.Builder(notary)
            builder.withItems(state, newStateOutput)
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx, setOf(scheduledState.source, scheduledState.destination)))
        }
    }

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true)
        notaryNode = mockNet.createNode(
                legalName = DUMMY_NOTARY.name,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(ValidatingNotaryService.type)))
        nodeA = mockNet.createNode(notaryNode.network.myAddress, start = false)
        nodeB = mockNet.createNode(notaryNode.network.myAddress, start = false)
        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `create and run scheduled flow then wait for result`() {
        val stateMachines = nodeA.smm.track()
        var countScheduledFlows = 0
        stateMachines.second.subscribe {
            if (it is StateMachineManager.Change.Add) {
                val initiator = it.logic.stateMachine.flowInitiator
                if (initiator is FlowInitiator.Scheduled)
                    countScheduledFlows++
            }
        }
        nodeA.services.startFlow(InsertInitialStateFlow(nodeB.info.legalIdentity))
        mockNet.waitQuiescent()
        val stateFromA = nodeA.database.transaction {
            nodeA.services.vaultService.linearHeadsOfType<ScheduledState>().values.first()
        }
        val stateFromB = nodeB.database.transaction {
            nodeB.services.vaultService.linearHeadsOfType<ScheduledState>().values.first()
        }
        assertEquals(1, countScheduledFlows)
        assertEquals(stateFromA, stateFromB, "Must be same copy on both nodes")
        assertTrue("Must be processed", stateFromB.state.data.processed)
    }

    @Test
    fun `run a whole batch of scheduled flows`() {
        val N = 100
        for (i in 0..N - 1) {
            nodeA.services.startFlow(InsertInitialStateFlow(nodeB.info.legalIdentity))
            nodeB.services.startFlow(InsertInitialStateFlow(nodeA.info.legalIdentity))
        }
        mockNet.waitQuiescent()
        val statesFromA = nodeA.database.transaction {
            nodeA.services.vaultService.linearHeadsOfType<ScheduledState>()
        }
        val statesFromB = nodeB.database.transaction {
            nodeB.services.vaultService.linearHeadsOfType<ScheduledState>()
        }
        assertEquals(2 * N, statesFromA.count(), "Expect all states to be present")
        assertEquals(statesFromA, statesFromB, "Expect identical data on both nodes")
        assertTrue("Expect all states have run the scheduled task", statesFromB.values.all { it.state.data.processed })
    }
}
