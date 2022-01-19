package net.corda.node.services.rpc

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import junit.framework.TestCase.assertNull
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.deleteIfExists
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.internal.inputStream
import net.corda.core.internal.readFully
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationDefaults
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.node.internal.NodeStartup
import net.corda.node.services.persistence.CheckpointPerformanceRecorder
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.CheckpointState
import net.corda.node.services.statemachine.FlowStart
import net.corda.node.services.statemachine.FlowState
import net.corda.node.services.statemachine.SubFlowVersion
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleEvent
import net.corda.nodeapi.internal.lifecycle.NodeServicesContext
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.util.zip.ZipInputStream

class CheckpointDumperImplTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val organisation = "MeTheMyself"
    private val myself = TestIdentity(CordaX500Name(organisation, "London", "GB"))
    private val currentTimestamp = Instant.parse("2019-12-25T10:15:30.00Z")
    private val baseDirectory = Files.createTempDirectory("CheckpointDumperTest")
    private val corDappDirectories = listOf(baseDirectory.resolve("cordapps"))
    private val file = baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME /
            "checkpoints_dump-${CheckpointDumperImpl.TIME_FORMATTER.format(currentTimestamp)}.zip"

    private lateinit var database: CordaPersistence
    private lateinit var services: ServiceHub
    private lateinit var checkpointStorage: DBCheckpointStorage

    private val mockAfterStartEvent = {
        val nodeServicesContextMock = mock<NodeServicesContext>()
        whenever(nodeServicesContextMock.tokenizableServices).doReturn(emptyList<SerializeAsToken>())
        val eventMock = mock<NodeLifecycleEvent.AfterNodeStart<*>>()
        whenever(eventMock.nodeServicesContext).doReturn(nodeServicesContextMock)
        eventMock
    }()

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
                get() {
                    return TestClock(mock<Clock>().also {
                        doReturn(currentTimestamp).whenever(it).instant()
                    })
                }
        }
        newCheckpointStorage()
        file.parent.createDirectories()
        file.deleteIfExists()
    }

    @After
    fun cleanUp() {
        database.close()
        baseDirectory.deleteRecursively()
    }

    @Test(timeout=300_000)
	fun testDumpCheckpoints() {
        val dumper = CheckpointDumperImpl(checkpointStorage, database, services, baseDirectory, corDappDirectories)
        dumper.update(mockAfterStartEvent)

        // add a checkpoint
        val (id, checkpoint) = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint, serializeFlowState(checkpoint), serializeCheckpointState(checkpoint))
        }

        dumper.dumpCheckpoints()
	    checkDumpFile()
    }

    @Test(timeout=300_000)
    fun `Checkpoint dumper doesn't output completed checkpoints`() {
        val dumper = CheckpointDumperImpl(checkpointStorage, database, services, baseDirectory, corDappDirectories)
        dumper.update(mockAfterStartEvent)

        // add a checkpoint
        val (id, checkpoint) = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint, serializeFlowState(checkpoint), serializeCheckpointState(checkpoint))
        }
        val newCheckpoint = checkpoint.copy(
            flowState = FlowState.Finished,
            status = Checkpoint.FlowStatus.COMPLETED
        )
        database.transaction {
            checkpointStorage.updateCheckpoint(id, newCheckpoint, null, serializeCheckpointState(newCheckpoint))
        }

        dumper.dumpCheckpoints()
        checkDumpFileEmpty()
    }

    private fun checkDumpFile() {
        ZipInputStream(file.inputStream()).use { zip ->
            val entry = zip.nextEntry
            assertThat(entry.name, containsSubstring("json"))
            val content = zip.readFully()
            assertThat(String(content), containsSubstring(organisation))
        }
    }

    private fun checkDumpFileEmpty() {
        ZipInputStream(file.inputStream()).use { zip ->
            val entry = zip.nextEntry
            assertNull(entry)
        }
    }

    // This test will only succeed when the VM startup includes the "checkpoint-agent":
    // -javaagent:tools/checkpoint-agent/build/libs/checkpoint-agent.jar
    @Test(timeout=300_000)
	fun testDumpCheckpointsAndAgentDiagnostics() {
        val dumper = CheckpointDumperImpl(checkpointStorage, database, services, Paths.get("."), Paths.get("cordapps"))
        dumper.update(mockAfterStartEvent)

        // add a checkpoint
        val (id, checkpoint) = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint, serializeFlowState(checkpoint), serializeCheckpointState(checkpoint))
        }

        dumper.dumpCheckpoints()
        // check existence of output zip file: checkpoints_dump-<date>.zip
        // check existence of output agent log:  checkpoints_agent-<data>.log
    }

    private fun newCheckpointStorage() {
        database.transaction {
            checkpointStorage = DBCheckpointStorage(
                object : CheckpointPerformanceRecorder {
                    override fun record(
                        serializedCheckpointState: SerializedBytes<CheckpointState>,
                        serializedFlowState: SerializedBytes<FlowState>?
                    ) {
                        // do nothing
                    }
                },
                Clock.systemUTC()
            )
        }
    }

    private fun newCheckpoint(version: Int = 1): Pair<StateMachineRunId, Checkpoint> {
        val id = StateMachineRunId.createRandom()
        val logic: FlowLogic<*> = object : FlowLogic<Unit>() {
            override fun call() {}
        }
        val frozenLogic = logic.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        val checkpoint = Checkpoint.create(InvocationContext.shell(), FlowStart.Explicit, logic.javaClass, frozenLogic, myself.identity.party, SubFlowVersion.CoreFlow(version), false)
                .getOrThrow()
        return id to checkpoint
    }

    private fun serializeFlowState(checkpoint: Checkpoint): SerializedBytes<FlowState> {
        return checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
    }

    private fun serializeCheckpointState(checkpoint: Checkpoint): SerializedBytes<CheckpointState> {
        return checkpoint.checkpointState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
    }
}
