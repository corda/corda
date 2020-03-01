package net.corda.node.services.persistence

import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationDefaults
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.node.internal.CheckpointIncompatibleException
import net.corda.node.internal.CheckpointVerifier
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.CheckpointState
import net.corda.node.services.statemachine.ErrorState
import net.corda.node.services.statemachine.FlowError
import net.corda.node.services.statemachine.FlowStart
import net.corda.node.services.statemachine.FlowState
import net.corda.node.services.statemachine.SubFlowVersion
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.IllegalStateException
import java.time.Instant
import kotlin.streams.toList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun CheckpointStorage.checkpoints(): List<Checkpoint.Serialized> {
    return getAllCheckpoints().use {
        it.map { it.second }.toList()
    }
}

class DBCheckpointStorageTests {
    private companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private lateinit var checkpointStorage: DBCheckpointStorage
    private lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })
        newCheckpointStorage()
    }

    @After
    fun cleanUp() {
        database.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test(timeout = 300_000)
    fun `add new checkpoint`() {
        val (id, checkpoint) = newCheckpoint()
        val serializedFlowState =
            checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(checkpoint)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
        }
        database.transaction {
            assertEquals(
                checkpoint,
                checkpointStorage.checkpoints().single().deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            )
        }
        newCheckpointStorage()
        database.transaction {
            assertEquals(
                checkpoint,
                checkpointStorage.checkpoints().single().deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            )
            session.get(DBCheckpointStorage.DBFlowCheckpoint::class.java, id.uuid.toString()).also {
                assertNotNull(it)
                assertNotNull(it.blob)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `remove checkpoint`() {
        val (id, checkpoint) = newCheckpoint()
        val serializedFlowState =
            checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(checkpoint)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(id)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
    }

    @Test(timeout = 300_000)
    fun `add and remove checkpoint in single commit operation`() {
        val (id, checkpoint) = newCheckpoint()
        val serializedFlowState = checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        val (id2, checkpoint2) = newCheckpoint()
        val serializedFlowState2 =
            checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(checkpoint)
            createMetadataRecord(checkpoint2)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
            checkpointStorage.addCheckpoint(id2, checkpoint2, serializedFlowState2)
            checkpointStorage.removeCheckpoint(id)
        }
        database.transaction {
            assertEquals(
                checkpoint2,
                checkpointStorage.checkpoints().single().deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            )
        }
        newCheckpointStorage()
        database.transaction {
            assertEquals(
                checkpoint2,
                checkpointStorage.checkpoints().single().deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            )
        }
    }

    @Test(timeout = 300_000)
    fun `add two checkpoints then remove first one`() {
        val (id, firstCheckpoint) = newCheckpoint()
        val serializedFirstFlowState =
            firstCheckpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)

        database.transaction {
            createMetadataRecord(firstCheckpoint)
            checkpointStorage.addCheckpoint(id, firstCheckpoint, serializedFirstFlowState)
        }
        val (id2, secondCheckpoint) = newCheckpoint()
        val serializedSecondFlowState =
            secondCheckpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(secondCheckpoint)
            checkpointStorage.addCheckpoint(id2, secondCheckpoint, serializedSecondFlowState)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(id)
        }
        database.transaction {
            assertEquals(
                secondCheckpoint,
                checkpointStorage.checkpoints().single().deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            )
        }
        newCheckpointStorage()
        database.transaction {
            assertEquals(
                secondCheckpoint,
                checkpointStorage.checkpoints().single().deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            )
        }
    }

    @Test(timeout = 300_000)
    fun `add checkpoint and then remove after 'restart'`() {
        val (id, originalCheckpoint) = newCheckpoint()
        val serializedOriginalFlowState =
            originalCheckpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(originalCheckpoint)
            checkpointStorage.addCheckpoint(id, originalCheckpoint, serializedOriginalFlowState)
        }
        newCheckpointStorage()
        val reconstructedCheckpoint = database.transaction {
            checkpointStorage.checkpoints().single()
        }
        database.transaction {
            assertEquals(originalCheckpoint, reconstructedCheckpoint.deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT))
            assertThat(reconstructedCheckpoint.serializedFlowState).isEqualTo(serializedOriginalFlowState)
                .isNotSameAs(serializedOriginalFlowState)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(id)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
    }

    @Test(timeout = 300_000)
    fun `verify checkpoints compatible`() {
        val mockServices = MockServices(emptyList(), ALICE.name)
        database.transaction {
            val (id, checkpoint) = newCheckpoint(1)
            val serializedFlowState =
                checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            createMetadataRecord(checkpoint)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
        }

        database.transaction {
            CheckpointVerifier.verifyCheckpointsCompatible(checkpointStorage, emptyList(), 1, mockServices, emptyList())
        }

        database.transaction {
            val (id1, checkpoint1) = newCheckpoint(2)
            val serializedFlowState1 =
                checkpoint1.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
            createMetadataRecord(checkpoint1)
            checkpointStorage.addCheckpoint(id1, checkpoint1, serializedFlowState1)
        }

        assertThatThrownBy {
            database.transaction {
                CheckpointVerifier.verifyCheckpointsCompatible(checkpointStorage, emptyList(), 1, mockServices, emptyList())
            }
        }.isInstanceOf(CheckpointIncompatibleException::class.java)
    }

    @Test(timeout = 300_000)
    fun `checkpoint can be recreated from database record`() {
        val (id, checkpoint) = newCheckpoint()
        val serializedFlowState =
            checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(checkpoint)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
        }
        database.transaction {
            assertEquals(serializedFlowState, checkpointStorage.checkpoints().single().serializedFlowState)
        }
        database.transaction {
            assertEquals(checkpoint, checkpointStorage.getCheckpoint(id)!!.deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT))
        }
    }

    @Test(timeout = 300_000)
    fun `update checkpoint with result information`() {
        val result = "This is the result"
        val (id, checkpoint) = newCheckpoint()
        val serializedFlowState =
            checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(checkpoint)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
        }
        val updatedCheckpoint = checkpoint.copy(result = result)
        val updatedSerializedFlowState =
            updatedCheckpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            checkpointStorage.updateCheckpoint(id, updatedCheckpoint, updatedSerializedFlowState)
        }
        database.transaction {
            assertEquals(
                result,
                checkpointStorage.getCheckpoint(id)!!.deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT).result
            )
            assertNotNull(session.get(DBCheckpointStorage.DBFlowCheckpoint::class.java, id.uuid.toString()).result)
        }
    }

    @Test(timeout = 300_000)
    fun `update checkpoint with error information`() {
        val exception = IllegalStateException("I am a naughty exception")
        val (id, checkpoint) = newCheckpoint()
        val serializedFlowState =
            checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(checkpoint)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
        }
        val updatedCheckpoint = checkpoint.copy(
            errorState = ErrorState.Errored(
                listOf(
                    FlowError(
                        0,
                        exception
                    )
                ), 0, false
            )
        )
        val updatedSerializedFlowState = updatedCheckpoint.flowState.checkpointSerialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction { checkpointStorage.updateCheckpoint(id, updatedCheckpoint, updatedSerializedFlowState) }
        database.transaction {
            // Checkpoint always returns clean error state when retrieved via [getCheckpoint]
            assertTrue(checkpointStorage.getCheckpoint(id)!!.deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT).errorState is ErrorState.Clean)
            assertNotNull(session.get(DBCheckpointStorage.DBFlowCheckpoint::class.java, id.uuid.toString()).exceptionDetails)
        }
    }

    @Test(timeout = 300_000)
    fun `clean checkpoints clear out error information from the database`() {
        val exception = IllegalStateException("I am a naughty exception")
        val (id, checkpoint) = newCheckpoint()
        val serializedFlowState =
            checkpoint.flowState.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction {
            createMetadataRecord(checkpoint)
            checkpointStorage.addCheckpoint(id, checkpoint, serializedFlowState)
        }
        val updatedCheckpoint = checkpoint.copy(
            errorState = ErrorState.Errored(
                listOf(
                    FlowError(
                        0,
                        exception
                    )
                ), 0, false
            )
        )
        val updatedSerializedFlowState = updatedCheckpoint.flowState.checkpointSerialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        database.transaction { checkpointStorage.updateCheckpoint(id, updatedCheckpoint, updatedSerializedFlowState) }
        database.transaction {
            // Checkpoint always returns clean error state when retrieved via [getCheckpoint]
            assertTrue(checkpointStorage.getCheckpoint(id)!!.deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT).errorState is ErrorState.Clean)
        }
        // Set back to clean
        database.transaction { checkpointStorage.updateCheckpoint(id, checkpoint, serializedFlowState) }
        database.transaction {
            assertTrue(checkpointStorage.getCheckpoint(id)!!.deserialize(CheckpointSerializationDefaults.CHECKPOINT_CONTEXT).errorState is ErrorState.Clean)
            assertNull(session.get(DBCheckpointStorage.DBFlowCheckpoint::class.java, id.uuid.toString()).exceptionDetails)
        }
    }

    private fun newCheckpointStorage() {
        database.transaction {
            checkpointStorage = DBCheckpointStorage(object : CheckpointPerformanceRecorder {
                override fun record(
                    serializedCheckpointState: SerializedBytes<CheckpointState>,
                    serializedFlowState: SerializedBytes<FlowState>
                ) {
                    // do nothing
                }
            })
        }
    }

    private fun newCheckpoint(version: Int = 1): Pair<StateMachineRunId, Checkpoint> {
        val id = StateMachineRunId.createRandom()
        val logic: FlowLogic<*> = object : FlowLogic<Unit>() {
            override fun call() {}
        }
        val frozenLogic = logic.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        val checkpoint = Checkpoint.create(
            InvocationContext.shell(),
            FlowStart.Explicit,
            logic.javaClass,
            frozenLogic,
            ALICE,
            SubFlowVersion.CoreFlow(version),
            false
        )
            .getOrThrow()
        return id to checkpoint
    }

    private fun DatabaseTransaction.createMetadataRecord(checkpoint: Checkpoint) {
        val metadata = DBCheckpointStorage.DBFlowMetadata(
            invocationId = checkpoint.checkpointState.invocationContext.trace.invocationId.value,
            flowId = null,
            flowName = "random.flow",
            userSuppliedIdentifier = null,
            startType = DBCheckpointStorage.StartReason.RPC,
            launchingCordapp = "this cordapp",
            platformVersion = PLATFORM_VERSION,
            rpcUsername = "Batman",
            invocationInstant = checkpoint.checkpointState.invocationContext.trace.invocationId.timestamp,
            receivedInstant = Instant.now(),
            startInstant = null,
            finishInstant = null
        )
        session.save(metadata)
    }
}
