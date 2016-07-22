package com.r3corda.node.services.api

import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.node.services.statemachine.ProtocolStateMachineImpl
import com.r3corda.node.services.statemachine.StateMachineManager

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

// This class will be serialised, so everything it points to transitively must also be serialisable (with Kryo).
data class Checkpoint(
        val serialisedFiber: SerializedBytes<ProtocolStateMachineImpl<*>>,
        val request: StateMachineManager.FiberRequest?
)