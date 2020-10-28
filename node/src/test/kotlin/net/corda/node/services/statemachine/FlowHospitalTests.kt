package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.serialization.internal.CheckpointSerializationDefaults
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.node.migration.VaultStateMigrationTest.Companion.ALICE
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.statemachine.transitions.StateMachine
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.security.SecureRandom
import java.util.concurrent.Semaphore

class FlowHospitalTests {

    companion object {
        private lateinit var mockNet: InternalMockNetwork
        private lateinit var serviceHub: ServiceHubInternal
        private lateinit var flowMessaging: FlowMessaging

        @BeforeClass
        @JvmStatic
        fun setUp() {
            mockNet = InternalMockNetwork()
            val node = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
            serviceHub = node.internals.services
            flowMessaging = FlowMessagingImpl(serviceHub)
        }

        @AfterClass
        fun tearDown() = mockNet.stopNodes()

        fun newStateMachineState(): Pair<StateMachineRunId, StateMachineState> {
            val id = StateMachineRunId.createRandom()
            val flowLogic: FlowLogic<*> = object: FlowLogic<Unit>() {
                @Suspendable
                override fun call() {}
            }

            return id to StateMachineState(
                newCheckpoint(flowLogic),
                flowLogic,
                emptyList(),
                isFlowResumed = true,
                isWaitingForFuture = false,
                future = openFuture<Unit>(),
                isAnyCheckpointPersisted = true,
                isStartIdempotent = false,
                isRemoved = false,
                isKilled = false,
                senderUUID = "",
                reloadCheckpointAfterSuspendCount = null,
                numberOfCommits = 1,
                lock = Semaphore(0)
            )
        }

        private fun newCheckpoint(flowLogic: FlowLogic<*>, version: Int = 1): Checkpoint {

            val frozenFlowLogic = flowLogic.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)

            return Checkpoint.create(
                InvocationContext.shell(),
                FlowStart.Explicit,
                flowLogic.javaClass,
                frozenFlowLogic,
                ALICE,
                SubFlowVersion.CoreFlow(version),
                false
            )
                .getOrThrow()
        }
    }

    private var flowHospital: StaffedFlowHospital? = null

    @After
    fun cleanUp() {
        flowHospital = null
    }

    @Test(timeout = 300_000)
    fun `Hospital works if not injected with any staff members`() {
        flowHospital = StaffedFlowHospital(flowMessaging, serviceHub.clock, "")//.also { it.staff = listOf() }

        val (id, stateMachineState) = newStateMachineState()

        flowHospital!!.requestTreatment(
            DummyFlowFiber(
                id,
                StateMachineInstanceId(id, 0L),
                StateMachine(id, SecureRandom())
            ),
            stateMachineState,
            listOf(IllegalStateException())
        )

    }
}

class DummyFlowFiber(
    override val id: StateMachineRunId,
    override val instanceId: StateMachineInstanceId,
    override val stateMachine: StateMachine
) : FlowFiber {
    override fun scheduleEvent(event: Event) {

    }

    override fun snapshot(): StateMachineState {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}