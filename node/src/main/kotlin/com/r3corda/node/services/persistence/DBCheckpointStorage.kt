package com.r3corda.node.services.persistence

import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.node.services.api.Checkpoint
import com.r3corda.node.services.api.CheckpointStorage
import com.r3corda.node.utilities.JDBCHashMap
import java.util.*

/**
 * Simple checkpoint key value storage in DB using the underlying JDBCHashMap and transactional context of the call sites.
 */
class DBCheckpointStorage : CheckpointStorage {
    private val checkpointStorage = Collections.synchronizedMap(JDBCHashMap<SecureHash, SerializedBytes<Checkpoint>>("checkpoints", loadOnInit = false))

    override fun addCheckpoint(checkpoint: Checkpoint) {
        val serialisedCheckpoint = checkpoint.serialize()
        val id = serialisedCheckpoint.hash
        checkpointStorage.put(id, serialisedCheckpoint)
    }

    override fun removeCheckpoint(checkpoint: Checkpoint) {
        val serialisedCheckpoint = checkpoint.serialize()
        val id = serialisedCheckpoint.hash
        checkpointStorage.remove(id) ?: throw IllegalArgumentException("Checkpoint not found")
    }

    override fun forEach(block: (Checkpoint) -> Boolean) {
        synchronized(checkpointStorage) {
            for (checkpoint in checkpointStorage.values) {
                if (!block(checkpoint.deserialize())) {
                    break
                }
            }
        }
    }
}