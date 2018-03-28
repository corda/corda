package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException

/**
 * An [Exception] to signal RPC clients that something went wrong within a Corda node.
 */
class InternalNodeException(message: String) : CordaClientException(message) {

    companion object {

        private const val DEFAULT_MESSAGE = "Something went wrong within the Corda node."

        fun defaultMessage(): String = DEFAULT_MESSAGE

        fun wrap(wrapped: Throwable): Throwable {

            return when {
                wrapped is CordaRuntimeException && wrapped is WithClientRelevantMessage -> {
                    wrapped.setCause(null)
                    wrapped
                }
                else -> {
                    when (wrapped) {
                        is WithClientRelevantMessage -> InternalNodeException(wrapped.message ?: DEFAULT_MESSAGE)
                        else -> InternalNodeException(DEFAULT_MESSAGE)
                    }
                }
            }
        }
    }
}