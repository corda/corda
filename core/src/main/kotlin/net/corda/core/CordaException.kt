package net.corda.core

import net.corda.annotations.serialization.Serializable
import java.util.*

@Serializable
interface CordaThrowable {
    var originalExceptionClassName: String?
    val originalMessage: String?
    fun setMessage(message: String?)
    fun setCause(cause: Throwable?)
    fun addSuppressed(suppressed: Array<Throwable>)
}

open class CordaException internal constructor(override var originalExceptionClassName: String? = null,
                                               private var _message: String? = null,
                                               private var _cause: Throwable? = null) : Exception(null, null, true, true), CordaThrowable {
    constructor(message: String?,
                cause: Throwable?) : this(null, message, cause)

    constructor(message: String?) : this(null, message, null)

    override val message: String?
        get() = if (originalExceptionClassName == null) originalMessage else {
            if (originalMessage == null) "$originalExceptionClassName" else "$originalExceptionClassName: $originalMessage"
        }

    override val cause: Throwable?
        get() = _cause ?: super.cause

    override fun setMessage(message: String?) {
        _message = message
    }

    override fun setCause(cause: Throwable?) {
        _cause = cause
    }

    override fun addSuppressed(suppressed: Array<Throwable>) {
        for (suppress in suppressed) {
            addSuppressed(suppress)
        }
    }

    override val originalMessage: String?
        get() = _message

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

open class CordaRuntimeException(override var originalExceptionClassName: String?,
                                 private var _message: String?,
                                 private var _cause: Throwable?) : RuntimeException(null, null, true, true), CordaThrowable {
    constructor(message: String?, cause: Throwable?) : this(null, message, cause)

    constructor(message: String?) : this(null, message, null)

    override val message: String?
        get() = if (originalExceptionClassName == null) originalMessage else {
            if (originalMessage == null) "$originalExceptionClassName" else "$originalExceptionClassName: $originalMessage"
        }

    override val cause: Throwable?
        get() = _cause ?: super.cause

    override fun setMessage(message: String?) {
        _message = message
    }

    override fun setCause(cause: Throwable?) {
        _cause = cause
    }

    override fun addSuppressed(suppressed: Array<Throwable>) {
        for (suppress in suppressed) {
            addSuppressed(suppress)
        }
    }

    override val originalMessage: String?
        get() = _message

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