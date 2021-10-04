package net.corda.core.internal

/**
 * A marker for a flow that will return the same result if replayed from the beginning. Any side effects the flow causes
 * must also be idempotent.
 *
 * Flow idempotency allows skipping persisting checkpoints, allowing better performance.
 */
interface IdempotentFlow

/**
 * An idempotent flow that needs to be replayed if it does not complete within a certain timeout.
 *
 * Example use would be the notary client flow: if the client sends a request to an HA notary cluster, it will get
 * accepted by one of the cluster members, but the member might crash before returning a response. The client flow
 * would be stuck waiting for that member to come back up. Retrying the notary flow will re-send the request to the
 * next available notary cluster member.
 *
 * Note that any sub-flows called by a [TimedFlow] are assumed to be [IdempotentFlow] and will NOT have checkpoints
 * persisted. Otherwise, it wouldn't be possible to correctly reset the [TimedFlow]. An implication of this is that
 * idempotent flows must not only return the same final result of the flow, but if a flow returns multiple messages
 * the full set of messages must be returned on subsequent attempts in the same order as the first flow.
 *
 * An example of this would be if a notary returns an ETA message at any point, then any subsequent retries of the
 * flow must also send such a message before returning the actual notarisation result.
 */
// TODO: allow specifying retry settings per flow
interface TimedFlow : IdempotentFlow {
    val isTimeoutEnabled: Boolean
}
