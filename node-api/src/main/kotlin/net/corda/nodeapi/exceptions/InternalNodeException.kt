package net.corda.nodeapi.exceptions

import net.corda.core.serialization.CordaSerializable

/**
 * An [Exception] to signal RPC clients that something went wrong within a Corda node.
 */
@CordaSerializable
class InternalNodeException private constructor(message: String) : Exception(message) {

    companion object {

        private const val DEFAULT_MESSAGE = "Something went wrong within the Corda node."

        fun defaultMessage(): String = DEFAULT_MESSAGE

        fun wrap(cause: Throwable): InternalNodeException {

            return when (cause) {
                is WithClientRelevantMessage -> InternalNodeException(cause.messageForClient)
                else -> InternalNodeException(DEFAULT_MESSAGE)
            }
        }
    }
}