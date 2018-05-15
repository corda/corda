package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowException
import net.corda.core.serialization.CordaSerializable
import net.corda.core.setFieldToNull
import java.io.InvalidClassException
import kotlin.reflect.full.findAnnotation

// could change to use package name matching but trying to avoid reflection for now
private val whitelisted = setOf(
        FlowException::class,
        InvalidClassException::class,
        RpcSerializableError::class,
        TransactionVerificationException::class,
        CordaRuntimeException::class
)

/**
 * An [Exception] to signal RPC clients that something went wrong within a Corda node.
 */
class InternalNodeException : CordaRuntimeException(DEFAULT_MESSAGE) {

    companion object {

        private const val DEFAULT_MESSAGE = "Something went wrong within the Corda node."

        fun defaultMessage(): String = DEFAULT_MESSAGE

        fun obfuscate(error: Throwable): Throwable {
            val exposed = if (error.isWhitelisted()) error else InternalNodeException()
            removeDetails(exposed)
            return exposed
        }

        private fun removeDetails(error: Throwable) {
            error.stackTrace = arrayOf<StackTraceElement>()
            error.setFieldToNull("cause")
            error.setFieldToNull("suppressedExceptions")
        }

        private fun Throwable.isWhitelisted(): Boolean {
            return whitelisted.any { it.isInstance(this) } || this::class.findAnnotation<CordaSerializable>() != null
        }
    }
}