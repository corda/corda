package net.corda.node.services.rpc

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationDefaults
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.FlowStart
import net.corda.node.services.statemachine.SubFlowVersion
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestClock
import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant

class CheckpointDumperTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    private lateinit var database: CordaPersistence
    private lateinit var services: ServiceHub
    private lateinit var checkpointStorage: DBCheckpointStorage

    @Before
    fun setUp() {
        val (db, mockServices) = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = emptyList(),
                initialIdentity = myself,
                moreIdentities = emptySet(),
                moreKeys = emptySet()
        )
        database = db
        services = object : ServiceHub by mockServices {
            // Set fixed point in time
            override val clock: Clock
                get() = TestClock(mock<Clock>().also {
                    doReturn(Instant.parse("2019-12-25T10:15:30.00Z")).whenever(it).instant()
                })
        }
        newCheckpointStorage()
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun testDumpCheckpoints() {
        val baseDirectory = Paths.get(".")
        val dumper = CheckpointDumper(checkpointStorage, database, services, baseDirectory)
        dumper.start(emptyList())

        // add a checkpoint
        val (id, checkpoint) = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint)
        }

        dumper.dump()
        //FileUtils.
        // check existence of output zip file: checkpoints_dump-<data>.zip
    }

    // This test will only succeed when the VM startup includes the "checkpoint-agent":
    // -javaagent:tools/checkpoint-agent/build/libs/checkpoint-agent.jar
    @Test
    fun testDumpCheckpointsAndAgentDiagnostics() {
        val dumper = CheckpointDumper(checkpointStorage, database, services, Paths.get("."))
        dumper.start(emptyList())

        // add a checkpoint
        val (id, checkpoint) = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint)
        }

        dumper.dump()
        // check existence of output zip file: checkpoints_dump-<date>.zip
        // check existence of output agent log:  checkpoints_agent-<data>.log
    }

    private fun newCheckpointStorage() {
        database.transaction {
            checkpointStorage = DBCheckpointStorage()
        }
    }

    private fun newCheckpoint(version: Int = 1): Pair<StateMachineRunId, SerializedBytes<Checkpoint>> {
        val id = StateMachineRunId.createRandom()
        val logic: FlowLogic<*> = object : FlowLogic<Unit>() {
            override fun call() {}
        }
        val frozenLogic = logic.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        val checkpoint = Checkpoint.create(InvocationContext.shell(), FlowStart.Explicit, logic.javaClass, frozenLogic, myself.identity.party, SubFlowVersion.CoreFlow(version), false)
                .getOrThrow()
        return id to checkpoint.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
    }
}