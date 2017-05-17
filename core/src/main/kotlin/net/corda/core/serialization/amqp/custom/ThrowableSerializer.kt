package net.corda.core.serialization.amqp.custom

import net.corda.core.serialization.amqp.CustomSerializer
import net.corda.core.serialization.amqp.SerializerFactory
import java.util.*

class ThrowableSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Throwable, ThrowableSerializer.ThrowableProxy>(Throwable::class.java, ThrowableProxy::class.java, factory) {
    override fun registerAdditionalSerializers(factory: SerializerFactory) {
        factory.register(StackTraceElementSerializer(factory))
        factory.register(StackTraceElementArraySerializer(factory))
        factory.register(ThrowableArraySerializer(factory))
    }

    override fun toProxy(obj: Throwable): ThrowableProxy {
        return ThrowableProxy(obj.javaClass.name, obj.message, obj.stackTrace, obj.cause, obj.suppressed)
    }

    override fun fromProxy(proxy: ThrowableProxy): Throwable {
        return CordaException(proxy.exceptionClass,
                proxy.message,
                proxy.cause,
                proxy.stackTrace,
                proxy.suppressed)
    }

    data class ThrowableProxy(
            val exceptionClass: String,
            val message: String?,
            val stackTrace: Array<StackTraceElement>,
            val cause: Throwable?,
            val suppressed: Array<Throwable>)
}

class StackTraceElementSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<StackTraceElement, StackTraceElementSerializer.StackTraceElementProxy>(StackTraceElement::class.java, StackTraceElementProxy::class.java, factory) {
    override fun registerAdditionalSerializers(factory: SerializerFactory) {
    }

    override fun toProxy(obj: StackTraceElement): StackTraceElementProxy = StackTraceElementProxy(obj.className, obj.methodName, obj.fileName, obj.lineNumber)

    override fun fromProxy(proxy: StackTraceElementProxy): StackTraceElement = StackTraceElement(proxy.declaringClass, proxy.methodName, proxy.fileName, proxy.lineNumber)

    data class StackTraceElementProxy(val declaringClass: String, val methodName: String, val fileName: String, val lineNumber: Int)
}

class StackTraceElementArraySerializer(factory: SerializerFactory) : CustomSerializer.PredefinedArray<StackTraceElement>(StackTraceElement::class.java, factory)
class ThrowableArraySerializer(factory: SerializerFactory) : CustomSerializer.PredefinedArray<Throwable>(Throwable::class.java, factory)

class CordaException(val originalExceptionClassName: String,
                     message: String?,
                     cause: Throwable?,
                     stackTrace: Array<StackTraceElement>?,
                     suppressed: Array<Throwable>) : RuntimeException(message, cause, true, true) {
    init {
        setStackTrace(stackTrace)
        for (suppress in suppressed) {
            addSuppressed(suppress)
        }
    }

    override val message: String?
        get() = if (originalMessage == null) "$originalExceptionClassName" else "$originalExceptionClassName: ${super.message}"

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