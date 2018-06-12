/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp.custom

import net.corda.core.CordaRuntimeException
import net.corda.core.CordaThrowable
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.SerializationFactory
import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.amqp.*
import java.io.NotSerializableException

@KeepForDJVM
class ThrowableSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Throwable, ThrowableSerializer.ThrowableProxy>(Throwable::class.java, ThrowableProxy::class.java, factory) {

    companion object {
        private val logger = contextLogger()
    }

    override val revealSubclassesInSchema: Boolean = true

    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(StackTraceElementSerializer(factory))

    override fun toProxy(obj: Throwable): ThrowableProxy {
        val extraProperties: MutableMap<String, Any?> = LinkedHashMap()
        val message = if (obj is CordaThrowable) {
            // Try and find a constructor
            try {
                val constructor = constructorForDeserialization(obj.javaClass)
                propertiesForSerializationFromConstructor(constructor!!, obj.javaClass, factory).forEach { property ->
                    extraProperties[property.serializer.name] = property.serializer.propertyReader.read(obj)
                }
            } catch (e: NotSerializableException) {
                logger.warn("Unexpected exception", e)
            }
            obj.originalMessage
        } else {
            obj.message
        }
        val stackTraceToInclude = if (shouldIncludeInternalInfo()) obj.stackTrace else emptyArray()
        return ThrowableProxy(obj.javaClass.name, message, stackTraceToInclude, obj.cause, obj.suppressed, extraProperties)
    }

    private fun shouldIncludeInternalInfo(): Boolean {
        val currentContext = SerializationFactory.currentFactory?.currentContext
        val includeInternalInfo = currentContext?.properties?.get(CommonPropertyNames.IncludeInternalInfo)
        return true == includeInternalInfo
    }

    override fun fromProxy(proxy: ThrowableProxy): Throwable {
        try {
            // TODO: This will need reworking when we have multiple class loaders
            val clazz = Class.forName(proxy.exceptionClass, false, factory.classloader)
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
            logger.warn("Unexpected exception de-serializing throwable: ${proxy.exceptionClass}. Converting to CordaRuntimeException.", e)
        }
        // If the criteria are not met or we experience an exception constructing the exception, we fall back to our own unchecked exception.
        return CordaRuntimeException(proxy.exceptionClass, null, null).apply {
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
    override fun toProxy(obj: StackTraceElement): StackTraceElementProxy = StackTraceElementProxy(obj.className, obj.methodName, obj.fileName, obj.lineNumber)

    override fun fromProxy(proxy: StackTraceElementProxy): StackTraceElement = StackTraceElement(proxy.declaringClass, proxy.methodName, proxy.fileName, proxy.lineNumber)

    @KeepForDJVM
    data class StackTraceElementProxy(val declaringClass: String, val methodName: String, val fileName: String?, val lineNumber: Int)
}
