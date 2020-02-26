package net.corda.node.services.persistence

import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationDefaults
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.node.internal.CheckpointIncompatibleException
import net.corda.node.internal.CheckpointVerifier
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.FlowStart
import net.corda.node.services.statemachine.SubFlowVersion
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
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
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import kotlin.streams.toList

internal fun CheckpointStorage.checkpoints(): List<SerializedBytes<Checkpoint>> {
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

    private val checkpointSerializationContext = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT

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

    @Test(timeout=300_000)
	fun `add new checkpoint`() {
        val (id, checkpoint) = newCheckpoint()
        val serializedCheckpoint = checkpoint.checkpointSerialize(context = checkpointSerializationContext)
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint, checkpointSerializationContext)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(serializedCheckpoint)
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(serializedCheckpoint)
        }
    }

    @Test(timeout=300_000)
	fun `remove checkpoint`() {
        val (id, checkpoint) = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint, checkpointSerializationContext)
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

    @Test(timeout=300_000)
    fun `add and remove checkpoint in single commit operate`() {
        val (id, checkpoint) = newCheckpoint()
        val serializedCheckpoint = checkpoint.checkpointSerialize(context = checkpointSerializationContext)
        val (id2, checkpoint2) = newCheckpoint()
        val serializedCheckpoint2 = checkpoint.checkpointSerialize(context = checkpointSerializationContext)
        database.transaction {
            checkpointStorage.addCheckpoint(id, checkpoint, checkpointSerializationContext)
            checkpointStorage.addCheckpoint(id2, checkpoint2, checkpointSerializationContext)
            checkpointStorage.removeCheckpoint(id)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(serializedCheckpoint2)
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(serializedCheckpoint2)
        }
    }

    @Test(timeout=300_000)
    fun `add two checkpoints then remove first one`() {
        val (id, firstCheckpoint) = newCheckpoint()
        val serializedFirstCheckpoint = firstCheckpoint.checkpointSerialize(context = checkpointSerializationContext)

        database.transaction {
            checkpointStorage.addCheckpoint(id, firstCheckpoint, checkpointSerializationContext)
        }
        val (id2, secondCheckpoint) = newCheckpoint()
        val serializedSecondCheckpoint = secondCheckpoint.checkpointSerialize(context = checkpointSerializationContext)
        database.transaction {
            checkpointStorage.addCheckpoint(id2, secondCheckpoint, checkpointSerializationContext)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(id)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(serializedSecondCheckpoint)
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(serializedSecondCheckpoint)
        }
    }

    @Test(timeout=300_000)
    fun `add checkpoint and then remove after 'restart'`() {
        val (id, originalCheckpoint) = newCheckpoint()
        val serializedOriginalCheckpoint = originalCheckpoint.checkpointSerialize(context = checkpointSerializationContext)
        database.transaction {
            checkpointStorage.addCheckpoint(id, originalCheckpoint, checkpointSerializationContext)
        }
        newCheckpointStorage()
        val reconstructedCheckpoint = database.transaction {
            checkpointStorage.checkpoints().single()
        }
        database.transaction {
            assertThat(reconstructedCheckpoint).isEqualTo(serializedOriginalCheckpoint).isNotSameAs(serializedOriginalCheckpoint)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(id)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
    }

    @Test(timeout=300_000)
    fun `verify checkpoints compatible`() {
        val mockServices = MockServices(emptyList(), ALICE.name)
        database.transaction {
            val (id, checkpoint) = newCheckpoint(1)
            checkpointStorage.addCheckpoint(id, checkpoint, checkpointSerializationContext)
        }

        database.transaction {
            CheckpointVerifier.verifyCheckpointsCompatible(checkpointStorage, emptyList(), 1, mockServices, emptyList())
        }

        database.transaction {
            val (id1, checkpoint1) = newCheckpoint(2)
            checkpointStorage.addCheckpoint(id1, checkpoint1, checkpointSerializationContext)
        }

        assertThatThrownBy {
            database.transaction {
                CheckpointVerifier.verifyCheckpointsCompatible(checkpointStorage, emptyList(), 1, mockServices, emptyList())
            }
        }.isInstanceOf(CheckpointIncompatibleException::class.java)
    }

    private fun newCheckpointStorage() {
        database.transaction {
            checkpointStorage = DBCheckpointStorage()
        }
    }

    private fun newCheckpoint(version: Int = 1): Pair<StateMachineRunId, Checkpoint> {
        val id = StateMachineRunId.createRandom()
        val logic: FlowLogic<*> = object : FlowLogic<Unit>() {
            override fun call() {}
        }
        val frozenLogic = logic.checkpointSerialize(context = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT)
        val checkpoint = Checkpoint.create(InvocationContext.shell(), FlowStart.Explicit, logic.javaClass, frozenLogic, ALICE, SubFlowVersion.CoreFlow(version), false)
                .getOrThrow()
        return id to checkpoint
    }

}
