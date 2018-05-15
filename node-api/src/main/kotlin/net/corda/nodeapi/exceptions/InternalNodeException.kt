package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException
import net.corda.core.flows.ClientRelevantError

/**
 * An [Exception] to signal RPC clients that something went wrong within a Corda node.
 */
class InternalNodeException : CordaRuntimeException(message), ClientRelevantError {

    companion object {
        /**
         * Message for the exception.
         */
        const val message = "Something went wrong within the Corda node."
    }
}