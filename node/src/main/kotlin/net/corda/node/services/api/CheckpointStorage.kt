package net.corda.node.services.api

import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.SerializedBytes
import net.corda.node.services.statemachine.Checkpoint
import java.util.stream.Stream

/**
 * Thread-safe storage of fiber checkpoints.
 */
interface CheckpointStorage {
    /**
     * Add a new checkpoint to the store.
     */
    fun addCheckpoint(id: StateMachineRunId, checkpoint: SerializedBytes<Checkpoint>)

    /**
     * Remove existing checkpoint from the store.
     * @return whether the id matched a checkpoint that was removed.
     */
    fun removeCheckpoint(id: StateMachineRunId): Boolean

    /**
     * Load an existing checkpoint from the store.
     * @return the checkpoint, still in serialized form, or null if not found.
     */
    fun getCheckpoint(id: StateMachineRunId): SerializedBytes<Checkpoint>?

    /**
     * Stream all checkpoints from the store. If this is backed by a database the stream will be valid until the
     * underlying database connection is closed, so any processing should happen before it is closed.
     */
    fun getAllCheckpoints(): Stream<Pair<StateMachineRunId, SerializedBytes<Checkpoint>>>
}