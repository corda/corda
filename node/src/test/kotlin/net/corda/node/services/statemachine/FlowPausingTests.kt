package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.FlowStateMachine
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertNull
import java.time.Duration
import kotlin.test.assertEquals

class FlowPausingTests {

    companion object {
        const val NUMBER_OF_FLOWS = 4
        const val SLEEP_TIME = 1000L
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode

    @Before
    fun setUpMockNet() {
        mockNet = InternalMockNetwork()
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    private fun restartNode(node: TestStartedNode, smmStartMode: StateMachineManager.StartMode) : TestStartedNode {
        val parameters = InternalMockNodeParameters(configOverrides = {
            conf: NodeConfiguration ->
            doReturn(smmStartMode).whenever(conf).smmStartMode
        })
        return mockNet.restartNode(node, parameters = parameters)
    }

    @Test(timeout = 300_000)
    fun `Hospitalized flow can be paused and resumed`() {
        val flow = aliceNode.services.startFlow(HospitalizingFlow())
        assertEquals(true, waitForHospitalizedCheckpoint(flow.id))
        assertEquals(true, aliceNode.smm.markFlowsAsPaused(flow.id))
        aliceNode.database.transaction {
            val checkpoint = aliceNode.internals.checkpointStorage.getCheckpoint(flow.id)
            assertEquals(Checkpoint.FlowStatus.PAUSED, checkpoint!!.status)
        }
        val restartedAlice = mockNet.restartNode(aliceNode)
        assertEquals(0, restartedAlice.smm.snapshot().size)
        assertEquals(false, restartedAlice.smm.waitForFlowToBeHospitalised(flow.id))
        restartedAlice.smm.unPauseFlow(flow.id)
        assertEquals(true, restartedAlice.smm.waitForFlowToBeHospitalised(flow.id))
    }

    @Test(timeout = 300_000)
    fun `Checkpointing flow can be paused and resumed if the statemachine is stopped`() {
        val flow = aliceNode.services.startFlow(CheckpointingFlow())
        aliceNode.smm.stop(0)
        assertEquals(true, aliceNode.smm.markFlowsAsPaused(flow.id))
        aliceNode.database.transaction {
            val checkpoint = aliceNode.internals.checkpointStorage.getCheckpoint(flow.id)
            assertEquals(Checkpoint.FlowStatus.PAUSED, checkpoint!!.status)
        }
        val restartedAlice = mockNet.restartNode(aliceNode)
        assertEquals(0, restartedAlice.smm.snapshot().size)
        assertEquals(true, restartedAlice.smm.unPauseFlow(flow.id))
        assertEquals(1, restartedAlice.smm.snapshot().size)
        restartedAlice.smm.allStateMachines[0].stateMachine.resultFuture.getOrThrow()
        restartedAlice.database.transaction {
            assertNull(restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id))
        }
    }

    @Test(timeout = 300_000)
    fun `A paused flow cannot be resumed twice`() {
        val flow = aliceNode.services.startFlow(CheckpointingFlow())
        aliceNode.smm.stop(0)
        assertEquals(true, aliceNode.smm.markFlowsAsPaused(flow.id))
        val restartedAlice = mockNet.restartNode(aliceNode)
        assertEquals(true, restartedAlice.smm.unPauseFlow(flow.id))
        assertEquals(false, restartedAlice.smm.unPauseFlow(flow.id))
        assertEquals(1, restartedAlice.smm.snapshot().size)
        restartedAlice.smm.allStateMachines[0].stateMachine.resultFuture.getOrThrow()
        restartedAlice.database.transaction {
            assertNull(restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id))
        }
    }

    @Test(timeout = 300_000)
    fun `All are paused when the node is restarted in safe start mode`() {
        val flows = ArrayList<FlowStateMachine<Unit>>()
        for (i in 1..NUMBER_OF_FLOWS) {
            flows += aliceNode.services.startFlow(CheckpointingFlow())
        }
        //All of the flows must not resume before the node restarts.
        val restartedAlice = restartNode(aliceNode, StateMachineManager.StartMode.Safe)
        assertEquals(0, restartedAlice.smm.snapshot().size)
        //We need to wait long enough here so any running flows would finish.
        Thread.sleep(NUMBER_OF_FLOWS * SLEEP_TIME)
        restartedAlice.database.transaction {
            for (flow in flows) {
                val checkpoint = restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id)
                assertEquals(Checkpoint.FlowStatus.PAUSED, checkpoint!!.status)
            }
        }
    }

    fun StateMachineManager.waitForFlowToBeHospitalised(id: StateMachineRunId) : Boolean {
        for (i in 0..1000) {
            if (this.flowHospital.contains(id)) return true
            Thread.sleep(10)
        }
        return false
    }

    fun waitForHospitalizedCheckpoint(id: StateMachineRunId) : Boolean {
        var paused = false
        for (i in 0..100) {
            aliceNode.database.transaction {
                val status = aliceNode.internals.checkpointStorage.getDBCheckpoint(id)!!.status
                if ( status == Checkpoint.FlowStatus.HOSPITALIZED) {
                    paused = true
                }
            }
            if (!paused) Thread.sleep(10)
            else break
        }
        return paused
    }

    internal class HospitalizingFlow: FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            throw HospitalizeFlowException("Something went wrong.")
        }
    }

    internal class CheckpointingFlow: FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            sleep(Duration.ofMillis(SLEEP_TIME))
        }
    }
}
