package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.TransactionVerificationException
import java.io.InvalidClassException

// could change to use package name matching but trying to avoid reflection for now
private val whitelisted = setOf(
        InvalidClassException::class,
        RpcSerializableError::class,
        TransactionVerificationException::class
)

/**
 * An [Exception] to signal RPC clients that something went wrong within a Corda node.
 */
class InternalNodeException(message: String) : CordaRuntimeException(message) {

    companion object {

        private const val DEFAULT_MESSAGE = "Something went wrong within the Corda node."

        fun defaultMessage(): String = DEFAULT_MESSAGE

        fun obfuscateIfInternal(wrapped: Throwable): Throwable {

            (wrapped as? CordaRuntimeException)?.setCause(null)
            return when {
                whitelisted.any { it.isInstance(wrapped) } -> wrapped
                else -> InternalNodeException(DEFAULT_MESSAGE)
            }
        }
    }
}