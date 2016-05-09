package core.node.storage

import core.crypto.sha256
import core.protocols.ProtocolStateMachine
import core.serialization.SerializedBytes
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.loggerFor
import core.utilities.trace
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.*
import java.util.Collections.synchronizedMap
import javax.annotation.concurrent.ThreadSafe

/**
 * Thread-safe storage of fiber checkpoints.
 */
interface CheckpointStorage {

    /**
     * Add a new checkpoint to the store.
     */
    fun addCheckpoint(checkpoint: Checkpoint)

    /**
     * Remove existing checkpoint from the store. It is an error to attempt to remove a checkpoint which doesn't exist
     * in the store. Doing so will throw an [IllegalArgumentException].
     */
    fun removeCheckpoint(checkpoint: Checkpoint)

    /**
     * Returns a snapshot of all the checkpoints in the store.
     * This may return more checkpoints than were added to this instance of the store; for example if the store persists
     * checkpoints to disk.
     */
    val checkpoints: Iterable<Checkpoint>

}


/**
 * File-based checkpoint storage, storing checkpoints per file.
 */
@ThreadSafe
class PerFileCheckpointStorage(val storeDir: Path) : CheckpointStorage {

    companion object {
        private val logger = loggerFor<PerFileCheckpointStorage>()
        private val fileExtension = ".checkpoint"
    }

    private val checkpointFiles = synchronizedMap(IdentityHashMap<Checkpoint, Path>())

    init {
        logger.trace { "Initialising per file checkpoint storage on $storeDir" }
        Files.createDirectories(storeDir)
        Files.list(storeDir)
                .filter { it.toString().toLowerCase().endsWith(fileExtension) }
                .forEach {
                    val checkpoint = Files.readAllBytes(it).deserialize<Checkpoint>()
                    checkpointFiles[checkpoint] = it
                }
    }

    override fun addCheckpoint(checkpoint: Checkpoint) {
        val serialisedCheckpoint = checkpoint.serialize()
        val fileName = "${serialisedCheckpoint.hash.toString().toLowerCase()}$fileExtension"
        val checkpointFile = storeDir.resolve(fileName)
        atomicWrite(checkpointFile, serialisedCheckpoint)
        logger.trace { "Stored $checkpoint to $checkpointFile" }
        checkpointFiles[checkpoint] = checkpointFile
    }

    private fun atomicWrite(checkpointFile: Path, serialisedCheckpoint: SerializedBytes<Checkpoint>) {
        val tempCheckpointFile = checkpointFile.parent.resolve("${checkpointFile.fileName}.tmp")
        serialisedCheckpoint.writeToFile(tempCheckpointFile)
        Files.move(tempCheckpointFile, checkpointFile, ATOMIC_MOVE)
    }

    override fun removeCheckpoint(checkpoint: Checkpoint) {
        val checkpointFile = checkpointFiles.remove(checkpoint)
        require(checkpointFile != null) { "Trying to removing unknown checkpoint: $checkpoint" }
        Files.delete(checkpointFile)
        logger.trace { "Removed $checkpoint ($checkpointFile)" }
    }

    override val checkpoints: Iterable<Checkpoint>
        get() = synchronized(checkpointFiles) {
            checkpointFiles.keys.toList()
        }

}




// This class will be serialised, so everything it points to transitively must also be serialisable (with Kryo).
data class Checkpoint(
        val serialisedFiber: SerializedBytes<ProtocolStateMachine<*>>,
        val awaitingTopic: String,
        val awaitingObjectOfType: String   // java class name
)
{
    override fun toString(): String {
        return "Checkpoint(#serialisedFiber=${serialisedFiber.sha256()}, awaitingTopic=$awaitingTopic, awaitingObjectOfType=$awaitingObjectOfType)"
    }
}