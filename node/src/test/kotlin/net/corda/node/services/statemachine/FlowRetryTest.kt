package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class FlowRetryTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var notaryIdentity: Party

    @Before
    fun setUpMockNet() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()
        )

        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))

        // Extract identities
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notaryIdentity = mockNet.defaultNotaryIdentity

    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    fun TestStartedNode.waitForFlowStatus(id: StateMachineRunId, flowStatus: Checkpoint.FlowStatus) : Boolean {
        for (i in 0..100) {
            val checkpoint = this.database.transaction {
                this@waitForFlowStatus.internals.checkpointStorage.getDBCheckpoint(id)
            }
            if (checkpoint?.status == flowStatus) return true
            Thread.sleep(10)
        }
        return false
    }

    @Test(timeout = 300_000)
    fun `A hospitalized flow can be retried`() {
        val flow = aliceNode.services.startFlow(HospitalizingFlow())
        assertEquals(true, aliceNode.waitForFlowStatus(flow.id, Checkpoint.FlowStatus.HOSPITALIZED))

        val retry = aliceNode.internals.smm.retryFlow(flow.id)
        assertEquals(true, retry)
        val retriedFuture = aliceNode.smm.allStateMachines.single().stateMachine.resultFuture
        retriedFuture.getOrThrow()
    }

    @Test(timeout = 300_000)
    fun `A flow will only rerun once when retried multiple times`() {
        val flow = aliceNode.services.startFlow(HospitalizingFlow())
        assertEquals(true, aliceNode.waitForFlowStatus(flow.id, Checkpoint.FlowStatus.HOSPITALIZED))
        val firstRetry = aliceNode.internals.smm.retryFlow(flow.id)
        assertEquals(true, firstRetry)

        for (i in 0..5) {
            val retryAgain = aliceNode.internals.smm.retryFlow(flow.id)
            assertEquals(false, retryAgain)
        }
    }

    @Test(timeout = 300_000)
    fun `A running flow can not be retried`() {
        val flow = aliceNode.services.startFlow(LongPausingFlow())
        for (i in 0..5) {
            val retryAgain = aliceNode.internals.smm.retryFlow(flow.id)
            assertEquals(false, retryAgain)
        }
        assertEquals(true, aliceNode.waitForFlowStatus(flow.id, Checkpoint.FlowStatus.PAUSED))

        val flowKilled = aliceNode.internals.smm.killFlow(flow.id)
        assertEquals(true, flowKilled)
    }


    class HospitalizingFlow : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val result = serviceHub.jdbcSession().prepareStatement("select status from node_checkpoints where flow_id = '${stateMachine.id.uuid}'").executeQuery()
            result.next()
            val status = Checkpoint.FlowStatus.values()[result.getInt(1)]
            // The first time the flow is run the flow should end up in the Hospital. After which on a retry the flow should succeed.
            if (status != Checkpoint.FlowStatus.HOSPITALIZED) {
                logger.error("Status = $status")
                throw HospitalizeFlowException("HospitalizeFlowException")
            }
            return true
        }
    }

    class LongPausingFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            sleep(Duration.ofSeconds(300))
        }
    }

}