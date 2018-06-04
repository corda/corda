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
 * Note that any sub-flows called by a [RetryableFlow] are assumed to be [IdempotentFlow] and will NOT have checkpoints
 * persisted. Otherwise, it wouldn't be possible to correctly reset the [RetryableFlow].
 */
// TODO: allow specifying retry settings per flow
interface RetryableFlow : IdempotentFlow

/** This exception is fired once the retry timeout expires. It will indicate to the flow hospital to restart the flow. */
data class FlowRetryException(val maxRetries: Int) : Exception()