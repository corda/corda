package net.corda.node.services.persistence

import com.google.common.primitives.Ints
import net.corda.core.serialization.SerializedBytes
import net.corda.testing.LogHelper
import net.corda.node.services.api.Checkpoint
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test

internal fun CheckpointStorage.checkpoints(): List<Checkpoint> {
    val checkpoints = mutableListOf<Checkpoint>()
    forEach {
        checkpoints += it
        true
    }
    return checkpoints
}

class DBCheckpointStorageTests {
    lateinit var checkpointStorage: DBCheckpointStorage
    lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties())
        newCheckpointStorage()
    }

    @After
    fun cleanUp() {
        database.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `add new checkpoint`() {
        val checkpoint = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(checkpoint)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint)
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint)
        }
    }

    @Test
    fun `remove checkpoint`() {
        val checkpoint = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(checkpoint)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(checkpoint)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
    }

    @Test
    fun `add and remove checkpoint in single commit operate`() {
        val checkpoint = newCheckpoint()
        val checkpoint2 = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(checkpoint)
            checkpointStorage.addCheckpoint(checkpoint2)
            checkpointStorage.removeCheckpoint(checkpoint)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint2)
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint2)
        }
    }

    @Test
    fun `remove unknown checkpoint`() {
        val checkpoint = newCheckpoint()
        database.transaction {
            assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
                checkpointStorage.removeCheckpoint(checkpoint)
            }
        }
    }

    @Test
    fun `add two checkpoints then remove first one`() {
        val firstCheckpoint = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(firstCheckpoint)
        }
        val secondCheckpoint = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(secondCheckpoint)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(firstCheckpoint)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(secondCheckpoint)
        }
        newCheckpointStorage()
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).containsExactly(secondCheckpoint)
        }
    }

    @Test
    fun `add checkpoint and then remove after 'restart'`() {
        val originalCheckpoint = newCheckpoint()
        database.transaction {
            checkpointStorage.addCheckpoint(originalCheckpoint)
        }
        newCheckpointStorage()
        val reconstructedCheckpoint = database.transaction {
            checkpointStorage.checkpoints().single()
        }
        database.transaction {
            assertThat(reconstructedCheckpoint).isEqualTo(originalCheckpoint).isNotSameAs(originalCheckpoint)
        }
        database.transaction {
            checkpointStorage.removeCheckpoint(reconstructedCheckpoint)
        }
        database.transaction {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
    }

    private fun newCheckpointStorage() {
        database.transaction {
            checkpointStorage = DBCheckpointStorage()
        }
    }

    private var checkpointCount = 1
    private fun newCheckpoint() = Checkpoint(SerializedBytes(Ints.toByteArray(checkpointCount++)))

}
