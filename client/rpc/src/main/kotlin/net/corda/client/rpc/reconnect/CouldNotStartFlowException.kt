package net.corda.client.rpc.reconnect

import net.corda.client.rpc.RPCException

/**
 * Thrown when a flow start command died before receiving a [net.corda.core.messaging.FlowHandle].
 * On catching this exception, the typical behaviour is to run a "logical retry", meaning only retry the flow if the expected outcome did not occur.
 */
class CouldNotStartFlowException(cause: Throwable? = null) : RPCException("Could not start flow as connection failed", cause)

