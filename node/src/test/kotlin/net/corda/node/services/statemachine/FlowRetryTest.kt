package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StateMachineRunId
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
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

    companion object {
        val NUMBER_OF_RETRIES = 5
    }

    @Before
    fun setUpMockNet() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()
        )
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    fun TestStartedNode.waitForFlowStatus(id: StateMachineRunId, flowStatus: Checkpoint.FlowStatus?) : Boolean {
        for (i in 0..100) {
            val checkpoint = this.database.transaction {
                this@waitForFlowStatus.internals.checkpointStorage.getDBCheckpoint(id)
            }
            if (checkpoint?.status == flowStatus) return true
            println("Status = ${checkpoint?.status}")
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

        for (i in 0..NUMBER_OF_RETRIES) {
            val retryAgain = aliceNode.internals.smm.retryFlow(flow.id)
            assertEquals(false, retryAgain)
        }
    }

    @Test(timeout = 300_000)
    fun `A running flow can not be retried`() {
        val flow = aliceNode.services.startFlow(LongPausingFlow())
        assertEquals(true, aliceNode.waitForFlowStatus(flow.id, Checkpoint.FlowStatus.RUNNABLE))
        for (i in 0..NUMBER_OF_RETRIES) {
            val retryAgain = aliceNode.internals.smm.retryFlow(flow.id)
            assertEquals(false, retryAgain)
        }
        assertEquals(1, aliceNode.internals.smm.allStateMachines.size)

        val flowKilled = aliceNode.internals.smm.killFlow(flow.id)
        assertEquals(true, flowKilled)
    }

    @Test(timeout = 300_000)
    fun `A completed flow can not be retried`() {
        val flow = aliceNode.services.startFlow(FastCompletionFlow())
        flow.resultFuture.getOrThrow()
        //This assumes that the checkpoint gets removed from the database when 
        assertEquals(true, aliceNode.waitForFlowStatus(flow.id, null))
        for (i in 0..NUMBER_OF_RETRIES) {
            val retryAgain = aliceNode.internals.smm.retryFlow(flow.id)
            assertEquals(false, retryAgain)
        }
    }

    @Test(timeout = 300_000)
    fun `A failed flow can not be retried`() {
        val flow = aliceNode.services.startFlow(FlowExceptionFlow())
        aliceNode.waitForFlowStatus(flow.id, Checkpoint.FlowStatus.FAILED)
        for (i in 0..NUMBER_OF_RETRIES) {
            val retryAgain = aliceNode.internals.smm.retryFlow(flow.id)
            assertEquals(false, retryAgain)
        }
    }

    class FlowExceptionFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            throw FlowException("FlowException")
        }
    }

    class FastCompletionFlow : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            sleep(Duration.ofMillis(10))
            return true
        }
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