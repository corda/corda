package net.corda.core.utilities

import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
open class CordaException internal constructor(val originalExceptionClassName: String?,
                                               message: String?,
                                               cause: Throwable?,
                                               stackTrace: Array<StackTraceElement>?,
                                               suppressed: Array<Throwable>) : Exception(message, cause, true, true) {
    constructor(message: String?,
                cause: Throwable?,
                stackTrace: Array<StackTraceElement>?,
                suppressed: Array<Throwable>) : this(null, message, cause, stackTrace, suppressed)

    init {
        if (stackTrace != null) setStackTrace(stackTrace)
        for (suppress in suppressed) {
            addSuppressed(suppress)
        }
    }

    override val message: String?
        get() = if (originalExceptionClassName == null) originalMessage else {
            if (originalMessage == null) "$originalExceptionClassName" else "$originalExceptionClassName: ${super.message}"
        }

    val originalMessage: String?
        get() = super.message

    override fun hashCode(): Int {
        return Arrays.deepHashCode(stackTrace) xor Objects.hash(originalExceptionClassName, originalMessage)
    }

    override fun equals(other: Any?): Boolean {
        return other is CordaException &&
                originalExceptionClassName == other.originalExceptionClassName &&
                message == other.message &&
                cause == other.cause &&
                Arrays.equals(stackTrace, other.stackTrace) &&
                Arrays.equals(suppressed, other.suppressed)
    }
}

@CordaSerializable
open class CordaRuntimeException internal constructor(val originalExceptionClassName: String?,
                                                      message: String?,
                                                      cause: Throwable?,
                                                      stackTrace: Array<StackTraceElement>?,
                                                      suppressed: Array<Throwable>) : RuntimeException(message, cause, true, true) {
    constructor(message: String?,
                cause: Throwable?,
                stackTrace: Array<StackTraceElement>?,
                suppressed: Array<Throwable>) : this(null, message, cause, stackTrace, suppressed)

    init {
        if (stackTrace != null) setStackTrace(stackTrace)
        for (suppress in suppressed) {
            addSuppressed(suppress)
        }
    }

    override val message: String?
        get() = if (originalExceptionClassName == null) originalMessage else {
            if (originalMessage == null) "$originalExceptionClassName" else "$originalExceptionClassName: ${super.message}"
        }

    val originalMessage: String?
        get() = super.message

    override fun hashCode(): Int {
        return Arrays.deepHashCode(stackTrace) xor Objects.hash(originalExceptionClassName, originalMessage)
    }

    override fun equals(other: Any?): Boolean {
        return other is CordaRuntimeException &&
                originalExceptionClassName == other.originalExceptionClassName &&
                message == other.message &&
                cause == other.cause &&
                Arrays.equals(stackTrace, other.stackTrace) &&
                Arrays.equals(suppressed, other.suppressed)
    }
}