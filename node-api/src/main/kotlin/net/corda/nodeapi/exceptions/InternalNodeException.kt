package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException
import net.corda.core.flows.RpcSerializableError
import net.corda.core.flows.ContextAware

/**
 * An [Exception] to signal RPC clients that something went wrong within a Corda node.
 */
class InternalNodeException(override val additionalContext: Map<String, Any> = emptyMap()) : CordaRuntimeException(message), RpcSerializableError, ContextAware {

    companion object {
        /**
         * Message for the exception.
         */
        const val message = "Something went wrong within the Corda node."
    }
}