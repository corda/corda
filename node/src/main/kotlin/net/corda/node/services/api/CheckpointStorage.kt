package net.corda.node.services.api

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.SerializedBytes
import net.corda.node.services.statemachine.FlowStateMachineImpl

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
     * Allows the caller to process safely in a thread safe fashion the set of all checkpoints.
     * The checkpoints are only valid during the lifetime of a single call to the block, to allow memory management.
     * Return false from the block to terminate further iteration.
     */
    fun forEach(block: (Checkpoint) -> Boolean)

}

// This class will be serialised, so everything it points to transitively must also be serialisable (with Kryo).
class Checkpoint(val serializedFiber: SerializedBytes<FlowStateMachineImpl<*>>) {

    val id: SecureHash get() = serializedFiber.hash

    override fun equals(other: Any?): Boolean = other === this || other is Checkpoint && other.id == this.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "${javaClass.simpleName}(id=$id)"
}
