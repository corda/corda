package net.corda.node.services.api

import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.FlowIORequest
import net.corda.core.serialization.SerializedBytes
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.FlowState
import java.util.stream.Stream

/**
 * Thread-safe storage of fiber checkpoints.
 */
interface CheckpointStorage {
    /**
     * Add a checkpoint for a new id to the store. Will throw if there is already a checkpoint for this id
     */
    fun addCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializedFlowState: SerializedBytes<FlowState>)

    /**
     * Update an existing checkpoint. Will throw if there is not checkpoint for this id.
     */
    fun updateCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializedFlowState: SerializedBytes<FlowState>)

    /**
     * Remove existing checkpoint from the store.
     * @return whether the id matched a checkpoint that was removed.
     */
    fun removeCheckpoint(id: StateMachineRunId): Boolean

    /**
     * Load an existing checkpoint from the store.
     *
     * The checkpoint returned from this function will be a _clean_ checkpoint. No error information is loaded into the checkpoint
     * even if the previous status of the checkpoint was [Checkpoint.FlowStatus.FAILED] or [Checkpoint.FlowStatus.HOSPITALIZED].
     *
     * @return The checkpoint, in a partially serialized form, or null if not found.
     */
    fun getCheckpoint(id: StateMachineRunId): Checkpoint.Serialized?

    /**
     * Stream all checkpoints from the store. If this is backed by a database the stream will be valid until the
     * underlying database connection is closed, so any processing should happen before it is closed.
     */
    fun getAllCheckpoints(): Stream<Pair<StateMachineRunId, Checkpoint.Serialized>>

    /**
     * Update a specific checkpoints ioRequest
     */
    fun updateFlowIoRequest(id: StateMachineRunId, ioRequest: FlowIORequest<*>)
}
