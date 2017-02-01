package net.corda.core.flows

/**
 * Exception which can be thrown by a [FlowLogic] at any point in its logic to unexpectedly bring it to a permanent end.
 * The exception will propagate to all counterparty flows and will be thrown on their end the next time they wait on a
 * [FlowLogic.receive] or [FlowLogic.sendAndReceive]. Any flow which no longer needs to do a receive, or has already ended,
 * will not receive the exception (if this is required then have them wait for a confirmation message).
 *
 * [FlowException] (or a subclass) can be a valid expected response from a flow, particularly ones which act as a service.
 * It is recommended a [FlowLogic] document the [FlowException] types it can throw.
 */
open class FlowException(override val message: String?, override val cause: Throwable?) : Exception() {
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}
