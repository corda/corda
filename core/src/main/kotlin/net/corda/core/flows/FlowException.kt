package net.corda.core.flows

import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException

// DOCSTART 1
/**
 * Exception which can be thrown by a [FlowLogic] at any point in its logic to unexpectedly bring it to a permanent end.
 * The exception will propagate to all counterparty flows and will be thrown on their end the next time they wait on a
 * [FlowLogic.receive] or [FlowLogic.sendAndReceive]. Any flow which no longer needs to do a receive, or has already ended,
 * will not receive the exception (if this is required then have them wait for a confirmation message).
 *
 * [FlowException] (or a subclass) can be a valid expected response from a flow, particularly ones which act as a service.
 * It is recommended a [FlowLogic] document the [FlowException] types it can throw.
 */
open class FlowException(message: String?, cause: Throwable?) : CordaException(message, cause) {
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
    constructor() : this(null, null)
}
// DOCEND 1

/**
 * Thrown when a flow session ends unexpectedly due to a type mismatch (the other side sent an object of a type
 * that we were not expecting), or the other side had an internal error, or the other side terminated when we
 * were waiting for a response.
 */
class FlowSessionException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause) {
    constructor(msg: String) : this(msg, null)
}