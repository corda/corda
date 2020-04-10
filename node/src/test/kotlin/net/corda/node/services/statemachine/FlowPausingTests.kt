package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.FlowStateMachine
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.makeUnique
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.reflect
import kotlin.test.assertEquals

class FlowPausingTests {

    companion object {
        const val NUMBER_OF_FLOWS = 10
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
        assertEquals(true, aliceNode.smm.waitForFlowToBeHospitalised(flow.id))
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
        Thread.sleep(2000) //Forgive Me
        restartedAlice.database.transaction {
            val checkpoint = restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id)
            assertEquals(Checkpoint.FlowStatus.COMPLETED, checkpoint!!.status)
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
        Thread.sleep(2000) //Forgive Me
        restartedAlice.database.transaction {
            val checkpoint = restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id)
            assertEquals(Checkpoint.FlowStatus.COMPLETED, checkpoint!!.status)
        }
    }

    @Test(timeout = 300_000)
    fun `All are paused when the node is restarted in safe start mode`() {
        val flows = ArrayList<FlowStateMachine<Unit>>()
        for (i in 1..NUMBER_OF_FLOWS) {
            flows += aliceNode.services.startFlow(CheckpointingFlow())
        }
        val restartedAlice = restartNode(aliceNode, StateMachineManager.StartMode.Safe)
        assertEquals(0, restartedAlice.smm.snapshot().size)
        Thread.sleep(10000) //Forgive Me
        restartedAlice.database.transaction {
            for (flow in flows) {
                val checkpoint = restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id)
                assertEquals(Checkpoint.FlowStatus.PAUSED, checkpoint!!.status)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `All hospitalized flows are paused when the node is restarted in paused hospitalized mode`() {
        val flows = ArrayList<FlowStateMachine<Unit>>()
        for (i in 1..NUMBER_OF_FLOWS) {
            flows += aliceNode.services.startFlow(CheckpointingFlow())
        }
        val hospitalisedFlows = ArrayList<FlowStateMachine<Unit>>()
        for (i in 1..NUMBER_OF_FLOWS) {
            hospitalisedFlows += aliceNode.services.startFlow(HospitalizingFlow())
        }

        val restartedAlice = restartNode(aliceNode, StateMachineManager.StartMode.PauseHospitalised)
        assertEquals(flows.size, restartedAlice.smm.snapshot().size)
        Thread.sleep(10000) //Forgive Me
        restartedAlice.database.transaction {
            for (flow in hospitalisedFlows) {
                val checkpoint = restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id)
                assertEquals(Checkpoint.FlowStatus.PAUSED, checkpoint!!.status)
            }
            for (flow in flows) {
                val checkpoint = restartedAlice.internals.checkpointStorage.getCheckpoint(flow.id)
                assertEquals(Checkpoint.FlowStatus.PAUSED, checkpoint!!.status)
            }
        }
    }

    fun StateMachineManager.waitForFlowToBeHospitalised(id: StateMachineRunId) : Boolean {
        for (i in 0..100) {
            if (this.flowHospital.contains(id)) return true
            Thread.sleep(10)
        }
        return false
    }

    internal class HospitalizingFlow(): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            throw HospitalizeFlowException("Something went wrong.")
        }
    }

    internal class CheckpointingFlow(): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            sleep(Duration.ofSeconds(1))
        }
    }
}
