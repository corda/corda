package com.r3corda.node.services.persistence

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.google.common.primitives.Ints
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.node.services.api.Checkpoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

class PerFileCheckpointStorageTests {

    val fileSystem: FileSystem = Jimfs.newFileSystem(unix())
    val storeDir: Path = fileSystem.getPath("store")
    lateinit var checkpointStorage: PerFileCheckpointStorage

    @Before
    fun setUp() {
        newCheckpointStorage()
    }

    @After
    fun cleanUp() {
        fileSystem.close()
    }

    @Test
    fun `add new checkpoint`() {
        val checkpoint = newCheckpoint()
        checkpointStorage.addCheckpoint(checkpoint)
        assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint)
        newCheckpointStorage()
        assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint)
    }

    @Test
    fun `remove checkpoint`() {
        val checkpoint = newCheckpoint()
        checkpointStorage.addCheckpoint(checkpoint)
        checkpointStorage.removeCheckpoint(checkpoint)
        assertThat(checkpointStorage.checkpoints()).isEmpty()
        newCheckpointStorage()
        assertThat(checkpointStorage.checkpoints()).isEmpty()
    }

    @Test
    fun `remove unknown checkpoint`() {
        val checkpoint = newCheckpoint()
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            checkpointStorage.removeCheckpoint(checkpoint)
        }
    }

    @Test
    fun `add two checkpoints then remove first one`() {
        val firstCheckpoint = newCheckpoint()
        checkpointStorage.addCheckpoint(firstCheckpoint)
        val secondCheckpoint = newCheckpoint()
        checkpointStorage.addCheckpoint(secondCheckpoint)
        checkpointStorage.removeCheckpoint(firstCheckpoint)
        assertThat(checkpointStorage.checkpoints()).containsExactly(secondCheckpoint)
        newCheckpointStorage()
        assertThat(checkpointStorage.checkpoints()).containsExactly(secondCheckpoint)
    }

    @Test
    fun `add checkpoint and then remove after 'restart'`() {
        val originalCheckpoint = newCheckpoint()
        checkpointStorage.addCheckpoint(originalCheckpoint)
        newCheckpointStorage()
        val reconstructedCheckpoint = checkpointStorage.checkpoints().single()
        assertThat(reconstructedCheckpoint).isEqualTo(originalCheckpoint).isNotSameAs(originalCheckpoint)
        checkpointStorage.removeCheckpoint(reconstructedCheckpoint)
        assertThat(checkpointStorage.checkpoints()).isEmpty()
    }

    @Test
    fun `non-checkpoint files are ignored`() {
        val checkpoint = newCheckpoint()
        checkpointStorage.addCheckpoint(checkpoint)
        Files.write(storeDir.resolve("random-non-checkpoint-file"), "this is not a checkpoint!!".toByteArray())
        newCheckpointStorage()
        assertThat(checkpointStorage.checkpoints()).containsExactly(checkpoint)
    }

    private fun newCheckpointStorage() {
        checkpointStorage = PerFileCheckpointStorage(storeDir)
    }

    private var checkpointCount = 1
    private fun newCheckpoint() = Checkpoint(SerializedBytes(Ints.toByteArray(checkpointCount++)))

}