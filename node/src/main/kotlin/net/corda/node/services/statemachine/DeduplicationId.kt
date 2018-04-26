package net.corda.node.services.statemachine

import java.security.SecureRandom

/**
 * A deduplication ID of a flow message.
 */
data class DeduplicationId(val toString: String) {
    companion object {
        /**
         * Create a random deduplication ID. Note that this isn't deterministic, which means we will never dedupe it,
         * unless we persist the ID somehow.
         */
        fun createRandom(random: SecureRandom) = DeduplicationId("R-${random.nextLong()}")

        /**
         * Create a deduplication ID for a normal clean state message. This is used to have a deterministic way of
         * creating IDs in case the message-generating flow logic is replayed on hard failure.
         *
         * A normal deduplication ID consists of:
         * 1. A deduplication seed set per flow. This is either the flow's ID or in case of an initated flow the
         *   initiator's session ID.
         * 2. The number of *clean* suspends since the start of the flow.
         * 3. An optional additional index, for cases where several messages are sent as part of the state transition.
         *   Note that care must be taken with this index, it must be a deterministic counter. For example a naive
         *   iteration over a HashMap will produce a different list of indeces than a previous run, causing the
         *   message-id map to change, which means deduplication will not happen correctly.
         */
        fun createForNormal(checkpoint: Checkpoint, index: Int): DeduplicationId {
            return DeduplicationId("N-${checkpoint.deduplicationSeed}-${checkpoint.numberOfSuspends}-$index")
        }

        /**
         * Create a deduplication ID for an error message. Note that these IDs live in a different namespace than normal
         * IDs, as we don't want error conditions to affect the determinism of clean deduplication IDs. This allows the
         * dirtiness state to be thrown away for resumption.
         *
         * An error deduplication ID consists of:
         * 1. The error's ID. This is a unique value per "source" of error and is propagated.
         *   See [net.corda.core.flows.IdentifiableException].
         * 2. The recipient's session ID.
         */
        fun createForError(errorId: Long, recipientSessionId: SessionId): DeduplicationId {
            return DeduplicationId("E-$errorId-${recipientSessionId.toLong}")
        }
    }
}
