package com.r3corda.node.services.persistence

import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.node.services.api.Checkpoint
import com.r3corda.node.services.api.CheckpointStorage
import com.r3corda.node.utilities.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.util.Collections.synchronizedMap

/**
 * Simple checkpoint key value storage in DB using the underlying JDBCHashMap and transactional context of the call sites.
 */
class DBCheckpointStorage : CheckpointStorage {

    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}checkpoints") {
        val checkpointId = secureHash("checkpoint_id")
        val checkpoint = blob("checkpoint")
    }

    private class CheckpointMap : AbstractJDBCHashMap<SecureHash, SerializedBytes<Checkpoint>, Table>(Table, loadOnInit = false) {
        override fun keyFromRow(row: ResultRow): SecureHash = row[table.checkpointId]

        override fun valueFromRow(row: ResultRow): SerializedBytes<Checkpoint> = bytesFromBlob(row[table.checkpoint])

        override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, SerializedBytes<Checkpoint>>, finalizables: MutableList<() -> Unit>) {
            insert[table.checkpointId] = entry.key
        }

        override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, SerializedBytes<Checkpoint>>, finalizables: MutableList<() -> Unit>) {
            insert[table.checkpoint] = bytesToBlob(entry.value, finalizables)
        }
    }

    private val checkpointStorage = synchronizedMap(CheckpointMap())

    override fun addCheckpoint(checkpoint: Checkpoint) {
        checkpointStorage.put(checkpoint.id, checkpoint.serialize())
    }

    override fun removeCheckpoint(checkpoint: Checkpoint) {
        checkpointStorage.remove(checkpoint.id) ?: throw IllegalArgumentException("Checkpoint not found")
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