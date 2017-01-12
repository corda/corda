package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.flows.FinalityFlow
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertEquals

class ScheduledFlowTests {
    lateinit var net: MockNetwork
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

        override val participants: List<CompositeKey> = listOf(source.owningKey, destination.owningKey)

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.containsAny(ourKeys) }
        }
    }

    class InsertInitialStateFlow(val destination: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val scheduledState = ScheduledState(serviceHub.clock.instant(),
                    serviceHub.myInfo.legalIdentity, destination)

            val notary = serviceHub.networkMapCache.getAnyNotary()
            val builder = TransactionType.General.Builder(notary)
            val tx = builder.withItems(scheduledState).
                    signWith(serviceHub.legalIdentityKey).toSignedTransaction(false)
            subFlow(FinalityFlow(tx, setOf(serviceHub.myInfo.legalIdentity)))
        }
    }

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
            val tx = builder.withItems(state, newStateOutput).
                    signWith(serviceHub.legalIdentityKey).toSignedTransaction(false)
            subFlow(FinalityFlow(tx, setOf(scheduledState.source, scheduledState.destination)))
        }
    }

    class ScheduledFlowTestPlugin : CordaPluginRegistry() {
        override val requiredFlows: Map<String, Set<String>> = mapOf(
                InsertInitialStateFlow::class.java.name to setOf(Party::class.java.name),
                ScheduledFlow::class.java.name to setOf(StateRef::class.java.name)
        )
    }


    @Before
    fun setup() {
        net = MockNetwork(threadPerNode = true)
        notaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                keyPair = DUMMY_NOTARY_KEY,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(ValidatingNotaryService.type)))
        nodeA = net.createNode(notaryNode.info.address, start = false)
        nodeB = net.createNode(notaryNode.info.address, start = false)
        nodeA.testPluginRegistries.add(ScheduledFlowTestPlugin())
        nodeB.testPluginRegistries.add(ScheduledFlowTestPlugin())
        net.startNodes()
    }

    @After
    fun cleanUp() {
        net.stopNodes()
    }

    @Test
    fun `create and run scheduled flow then wait for result`() {
        nodeA.services.startFlow(InsertInitialStateFlow(nodeB.info.legalIdentity))
        net.waitQuiescent()
        val stateFromA = databaseTransaction(nodeA.database) {
            nodeA.services.vaultService.linearHeadsOfType<ScheduledState>().values.first()
        }
        val stateFromB = databaseTransaction(nodeB.database) {
            nodeB.services.vaultService.linearHeadsOfType<ScheduledState>().values.first()
        }
        assertEquals(stateFromA, stateFromB, "Must be same copy on both nodes")
        assertTrue("Must be processed", stateFromB.state.data.processed)
    }

    @Test
    fun `Run a whole batch of scheduled flows`() {
        val N = 100
        for (i in 0..N - 1) {
            nodeA.services.startFlow(InsertInitialStateFlow(nodeB.info.legalIdentity))
            nodeB.services.startFlow(InsertInitialStateFlow(nodeA.info.legalIdentity))
        }
        net.waitQuiescent()
        val statesFromA = databaseTransaction(nodeA.database) {
            nodeA.services.vaultService.linearHeadsOfType<ScheduledState>()
        }
        val statesFromB = databaseTransaction(nodeB.database) {
            nodeB.services.vaultService.linearHeadsOfType<ScheduledState>()
        }
        assertEquals(2 * N, statesFromA.count(), "Expect all states to be present")
        assertEquals(statesFromA, statesFromB, "Expect identical data on both nodes")
        assertTrue("Expect all states have run the scheduled task", statesFromB.values.all { it.state.data.processed })
    }
}
