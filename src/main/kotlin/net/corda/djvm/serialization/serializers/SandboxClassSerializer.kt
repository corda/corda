package net.corda.djvm.serialization.serializers

import net.corda.djvm.execution.SandboxRuntimeException
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.ClassDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.AMQPNotSerializableException
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.ClassSerializer.ClassProxy
import java.util.function.BiFunction
import java.util.function.Function

@Suppress("unchecked_cast")
class SandboxClassSerializer(
    classLoader: SandboxClassLoader,
    private val executor: BiFunction<in Any, in Any?, out Any?>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = Class::class.java as Class<Any>,
    proxyClass = classLoader.loadClassForSandbox(ClassProxy::class.java),
    factory = factory
) {
    private val task = classLoader.loadClassForSandbox(ClassDeserializer::class.java).newInstance()
    private val nameOf: Function<Any, String>

    init {
        val fetch = proxyClass.getMethod("getClassName")
        nameOf = Function { proxy ->
            fetch(proxy).toString()
        }
    }

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return try {
            executor.apply(task, proxy)!!
        } catch (e: SandboxRuntimeException) {
            val cause = e.cause ?: throw e
            if (cause !is ClassNotFoundException) {
                throw cause
            }

            val className = nameOf.apply(proxy)
            throw AMQPNotSerializableException(type,
                "Could not instantiate $className - not on the classpath",
                "$className was not found by the node, check the Node containing the CorDapp that " +
                        "implements $className is loaded and on the Classpath",
                mutableListOf(className)
            )
        }
    }
}
