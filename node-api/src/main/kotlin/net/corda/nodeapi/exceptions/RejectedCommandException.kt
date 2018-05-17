package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException
import net.corda.core.flows.RpcSerializableError

/**
 * Thrown to indicate that the command was rejected by the node, typically due to a special temporary mode.
 */
class RejectedCommandException(message: String) : CordaRuntimeException(message), RpcSerializableError