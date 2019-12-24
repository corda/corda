package net.corda.node.services.rpc

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
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
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationDefaults
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.node.internal.NodeStartup
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.FlowStart
import net.corda.node.services.statemachine.SubFlowVersion
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

class CheckpointDumperTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val organisation = "MeTheMyself"
    private val myself = TestIdentity(CordaX500Name(organisation, "London", "GB"))
    private val currentTimestamp = Instant.parse("2019-12-25T10:15:30.00Z")
    private val baseDirectory = Files.createTempDirectory("CheckpointDumperTest")
    private val file = baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME /
            "checkpoints_dump-${CheckpointDumper.TIME_FORMATTER.format(currentTimestamp)}.zip"

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

    @Test
    fun testDumpCheckpoints() {
        val dumper = CheckpointDumper(checkpointStorage, database, services, baseDirectory)
        dumper.start(emptyList())

        // add a checkpoint
        val (id, checkpoint) = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint)
        }

        dumper.dump()
        checkDumpFile()
    }

    private fun checkDumpFile() {
        ZipInputStream(file.inputStream()).use { zip ->
            val entry = zip.nextEntry
            assertThat(entry.name, containsSubstring("json"))
            val content = zip.readFully()
            assertThat(String(content), containsSubstring(organisation))
        }
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