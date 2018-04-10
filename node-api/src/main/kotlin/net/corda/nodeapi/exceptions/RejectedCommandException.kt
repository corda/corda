package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException

/**
 * Thrown to indicate that the command was rejected by the node, typically due to a special temporary mode.
 */
class RejectedCommandException(msg: String) : CordaRuntimeException(msg)