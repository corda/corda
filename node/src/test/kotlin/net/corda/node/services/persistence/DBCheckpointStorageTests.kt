package net.corda.node.services.persistence

import com.google.common.primitives.Ints
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.LogHelper
import net.corda.node.services.api.Checkpoint
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable

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
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        newCheckpointStorage()
    }

    @After
    fun cleanUp() {
        dataSource.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `add new checkpoint`() {
        val checkpoint = newCheckpoint()
        databaseTransaction(database) {
            checkpointStorage.addCheckpoint(checkpoint)
        }
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint)
        }
        newCheckpointStorage()
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint)
        }
    }

    @Test
    fun `remove checkpoint`() {
        val checkpoint = newCheckpoint()
        databaseTransaction(database) {
            checkpointStorage.addCheckpoint(checkpoint)
        }
        databaseTransaction(database) {
            checkpointStorage.removeCheckpoint(checkpoint)
        }
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
        newCheckpointStorage()
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
    }

    @Test
    fun `add and remove checkpoint in single commit operate`() {
        val checkpoint = newCheckpoint()
        val checkpoint2 = newCheckpoint()
        databaseTransaction(database) {
            checkpointStorage.addCheckpoint(checkpoint)
            checkpointStorage.addCheckpoint(checkpoint2)
            checkpointStorage.removeCheckpoint(checkpoint)
        }
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint2)
        }
        newCheckpointStorage()
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint2)
        }
    }

    @Test
    fun `remove unknown checkpoint`() {
        val checkpoint = newCheckpoint()
        databaseTransaction(database) {
            assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
                checkpointStorage.removeCheckpoint(checkpoint)
            }
        }
    }

    @Test
    fun `add two checkpoints then remove first one`() {
        val firstCheckpoint = newCheckpoint()
        databaseTransaction(database) {
            checkpointStorage.addCheckpoint(firstCheckpoint)
        }
        val secondCheckpoint = newCheckpoint()
        databaseTransaction(database) {
            checkpointStorage.addCheckpoint(secondCheckpoint)
        }
        databaseTransaction(database) {
            checkpointStorage.removeCheckpoint(firstCheckpoint)
        }
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).containsExactly(secondCheckpoint)
        }
        newCheckpointStorage()
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).containsExactly(secondCheckpoint)
        }
    }

    @Test
    fun `add checkpoint and then remove after 'restart'`() {
        val originalCheckpoint = newCheckpoint()
        databaseTransaction(database) {
            checkpointStorage.addCheckpoint(originalCheckpoint)
        }
        newCheckpointStorage()
        val reconstructedCheckpoint = databaseTransaction(database) {
            checkpointStorage.checkpoints().single()
        }
        databaseTransaction(database) {
            assertThat(reconstructedCheckpoint).isEqualTo(originalCheckpoint).isNotSameAs(originalCheckpoint)
        }
        databaseTransaction(database) {
            checkpointStorage.removeCheckpoint(reconstructedCheckpoint)
        }
        databaseTransaction(database) {
            assertThat(checkpointStorage.checkpoints()).isEmpty()
        }
    }

    private fun newCheckpointStorage() {
        databaseTransaction(database) {
            checkpointStorage = DBCheckpointStorage()
        }
    }

    private var checkpointCount = 1
    private fun newCheckpoint() = Checkpoint(SerializedBytes(Ints.toByteArray(checkpointCount++)))

}
