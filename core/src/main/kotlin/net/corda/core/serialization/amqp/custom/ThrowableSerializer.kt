package net.corda.core.serialization.amqp.custom

import net.corda.core.serialization.amqp.CustomSerializer
import net.corda.core.serialization.amqp.SerializerFactory
import net.corda.core.utilities.CordaRuntimeException
import java.lang.reflect.Constructor

class ThrowableSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Throwable, ThrowableSerializer.ThrowableProxy>(Throwable::class.java, ThrowableProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> =
            listOf(StackTraceElementSerializer(factory), StackTraceElementArraySerializer(factory), ThrowableArraySerializer(factory))

    override fun toProxy(obj: Throwable): ThrowableProxy {
        return ThrowableProxy(obj.javaClass.name, obj.message, obj.stackTrace, obj.cause, obj.suppressed)
    }

    override fun fromProxy(proxy: ThrowableProxy): Throwable {
        with(proxy) {
            var exceptionConstructor: Constructor<out Throwable>? = null
            try {
                // TODO: This will need reworking when we have multiple class loaders
                val clazz = Class.forName(exceptionClass, false, this.javaClass.classLoader)
                if (factory.hasAnnotationInHierarchy(clazz)) {
                    // Now see if we have the necessary constructor
                    exceptionConstructor = clazz.getDeclaredConstructor(String::class.java, Throwable::class.java, Array<StackTraceElement>::class.java, Array<Throwable>::class.java) as Constructor<out Throwable>
                }
            } catch (e: Exception) {
            }
            return exceptionConstructor?.newInstance(message, cause, stackTrace, suppressed) ?: CordaRuntimeException(exceptionClass, message, cause, stackTrace, suppressed)
        }
    }

    data class ThrowableProxy(
            val exceptionClass: String,
            val message: String?,
            val stackTrace: Array<StackTraceElement>,
            val cause: Throwable?,
            val suppressed: Array<Throwable>)
}

class StackTraceElementSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<StackTraceElement, StackTraceElementSerializer.StackTraceElementProxy>(StackTraceElement::class.java, StackTraceElementProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<Any>> = emptyList()

    override fun toProxy(obj: StackTraceElement): StackTraceElementProxy = StackTraceElementProxy(obj.className, obj.methodName, obj.fileName, obj.lineNumber)

    override fun fromProxy(proxy: StackTraceElementProxy): StackTraceElement = StackTraceElement(proxy.declaringClass, proxy.methodName, proxy.fileName, proxy.lineNumber)

    data class StackTraceElementProxy(val declaringClass: String, val methodName: String, val fileName: String?, val lineNumber: Int)
}

class StackTraceElementArraySerializer(factory: SerializerFactory) : CustomSerializer.PredefinedArray<StackTraceElement>(StackTraceElement::class.java, factory)
class ThrowableArraySerializer(factory: SerializerFactory) : CustomSerializer.PredefinedArray<Throwable>(Throwable::class.java, factory)

