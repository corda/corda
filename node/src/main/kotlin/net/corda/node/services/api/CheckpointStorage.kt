package net.corda.node.services.api

import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.SerializedBytes
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.CheckpointState
import net.corda.node.services.statemachine.FlowResultMetadata
import net.corda.node.services.statemachine.FlowState
import java.util.stream.Stream

/**
 * Thread-safe storage of fiber checkpoints.
 */
interface CheckpointStorage {
    /**
     * Add a checkpoint for a new id to the store. Will throw if there is already a checkpoint for this id
     */
    fun addCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializedFlowState: SerializedBytes<FlowState>,
                      serializedCheckpointState: SerializedBytes<CheckpointState>)

    /**
     * Update an existing checkpoint. Will throw if there is not checkpoint for this id.
     */
    fun updateCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializedFlowState: SerializedBytes<FlowState>?,
                         serializedCheckpointState: SerializedBytes<CheckpointState>)

    /**
     * Update an existing checkpoints status ([Checkpoint.status]).
     */
    fun updateStatus(runId: StateMachineRunId, flowStatus: Checkpoint.FlowStatus)

    /**
     * Update an existing checkpoints compatibility flag ([Checkpoint.compatible]).
     */
    fun updateCompatible(runId: StateMachineRunId, compatible: Boolean)

    /**
     * Update all persisted checkpoints with status [Checkpoint.FlowStatus.RUNNABLE] or [Checkpoint.FlowStatus.HOSPITALIZED],
     * changing the status to [Checkpoint.FlowStatus.PAUSED].
     */
    fun markAllPaused()

    /**
     * Remove existing checkpoint from the store.
     *
     * [mayHavePersistentResults] is used for optimization. If set to [false] it will not attempt to delete the database result or the database exception.
     * Please note that if there is a doubt on whether a flow could be finished or not [mayHavePersistentResults] should be set to [true].
     * @return whether the id matched a checkpoint that was removed.
     */
    fun removeCheckpoint(id: StateMachineRunId, mayHavePersistentResults: Boolean = true): Boolean

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
     * Stream all checkpoints with statuses [statuses] from the store. If this is backed by a database the stream will be valid
     * until the underlying database connection is closed, so any processing should happen before it is closed.
     */
    fun getCheckpoints(
        statuses: Collection<Checkpoint.FlowStatus> = Checkpoint.FlowStatus.values().toSet()
        ): Stream<Pair<StateMachineRunId, Checkpoint.Serialized>>

    /**
     * Stream runnable checkpoints from the store. If this is backed by a database the stream will be valid
     * until the underlying database connection is closed, so any processing should happen before it is closed.
     */
    fun getCheckpointsToRun(): Stream<Pair<StateMachineRunId, Checkpoint.Serialized>>

    /**
     * Stream paused checkpoints from the store. If this is backed by a database the stream will be valid
     * until the underlying database connection is closed, so any processing should happen before it is closed.
     * This method does not fetch [Checkpoint.Serialized.serializedFlowState] to save memory.
     */
    fun getPausedCheckpoints(): Stream<Triple<StateMachineRunId, Checkpoint.Serialized, Boolean>>

    fun getFinishedFlowsResultsMetadata(): Stream<Pair<StateMachineRunId, FlowResultMetadata>>

    /**
     * Load a flow result from the store. If [throwIfMissing] is true then it throws an [IllegalStateException]
     * if the flow result is missing in the database.
     */
    fun getFlowResult(id: StateMachineRunId, throwIfMissing: Boolean = false): Any?

    /**
     * Load a flow exception from the store. If [throwIfMissing] is true then it throws an [IllegalStateException]
     * if the flow exception is missing in the database.
     */
    fun getFlowException(id: StateMachineRunId, throwIfMissing: Boolean = false): Any?

    fun removeFlowException(id: StateMachineRunId): Boolean
}
