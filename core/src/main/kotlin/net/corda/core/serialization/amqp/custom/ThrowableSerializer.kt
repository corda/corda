package net.corda.core.serialization.amqp.custom

import net.corda.core.serialization.amqp.CustomSerializer
import net.corda.core.serialization.amqp.SerializerFactory
import net.corda.core.serialization.amqp.constructorForDeserialization
import net.corda.core.serialization.amqp.propertiesForSerialization
import net.corda.core.CordaRuntimeException
import net.corda.core.CordaThrowable
import java.io.NotSerializableException

class ThrowableSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Throwable, ThrowableSerializer.ThrowableProxy>(Throwable::class.java, ThrowableProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(StackTraceElementSerializer(factory))

    override fun toProxy(obj: Throwable): ThrowableProxy {
        val extraProperties: MutableMap<String, Any?> = LinkedHashMap()
        val message = if (obj is CordaThrowable) {
            // Try and find a constructor
            try {
                val constructor = constructorForDeserialization(obj.javaClass)
                val props = propertiesForSerialization(constructor, obj.javaClass, factory)
                for (prop in props) {
                    extraProperties[prop.name] = prop.readMethod.invoke(obj)
                }
            } catch(e: NotSerializableException) {
            }
            obj.originalMessage
        } else {
            obj.message
        }
        return ThrowableProxy(obj.javaClass.name, message, obj.stackTrace, obj.cause, obj.suppressed, extraProperties)
    }

    override fun fromProxy(proxy: ThrowableProxy): Throwable {
        try {
            // TODO: This will need reworking when we have multiple class loaders
            val clazz = Class.forName(proxy.exceptionClass, false, this.javaClass.classLoader)
            // If it is CordaException or CordaRuntimeException, we can seek any constructor and then set the properties
            // Otherwise we just make a CordaRuntimeException
            if (CordaThrowable::class.java.isAssignableFrom(clazz) && Throwable::class.java.isAssignableFrom(clazz)) {
                val constructor = constructorForDeserialization(clazz)!!
                val throwable = constructor.callBy(constructor.parameters.map { it to proxy.additionalProperties[it.name] }.toMap())
                (throwable as CordaThrowable).apply {
                    if (this.javaClass.name != proxy.exceptionClass) this.originalExceptionClassName = proxy.exceptionClass
                    this.setMessage(proxy.message)
                    this.setCause(proxy.cause)
                    this.addSuppressed(proxy.suppressed)
                }
                return (throwable as Throwable).apply {
                    this.stackTrace = proxy.stackTrace
                }
            }
        } catch (e: Exception) {
            // If attempts to rebuild the exact exception fail, we fall through and build a runtime exception.
        }
        // If the criteria are not met or we experience an exception constructing the exception, we fall back to our own unchecked exception.
        return CordaRuntimeException(proxy.exceptionClass).apply {
            this.setMessage(proxy.message)
            this.setCause(proxy.cause)
            this.stackTrace = proxy.stackTrace
            this.addSuppressed(proxy.suppressed)
        }
    }

    class ThrowableProxy(
            val exceptionClass: String,
            val message: String?,
            val stackTrace: Array<StackTraceElement>,
            val cause: Throwable?,
            val suppressed: Array<Throwable>,
            val additionalProperties: Map<String, Any?>)
}

class StackTraceElementSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<StackTraceElement, StackTraceElementSerializer.StackTraceElementProxy>(StackTraceElement::class.java, StackTraceElementProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<Any>> = emptyList()

    override fun toProxy(obj: StackTraceElement): StackTraceElementProxy = StackTraceElementProxy(obj.className, obj.methodName, obj.fileName, obj.lineNumber)

    override fun fromProxy(proxy: StackTraceElementProxy): StackTraceElement = StackTraceElement(proxy.declaringClass, proxy.methodName, proxy.fileName, proxy.lineNumber)

    data class StackTraceElementProxy(val declaringClass: String, val methodName: String, val fileName: String?, val lineNumber: Int)
}