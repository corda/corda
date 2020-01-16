package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.ClassDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.AMQPNotSerializableException
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.ClassSerializer.ClassProxy
import java.util.function.Function

@Suppress("unchecked_cast")
class SandboxClassSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = Class::class.java as Class<Any>,
    proxyClass = classLoader.toSandboxAnyClass(ClassProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(ClassDeserializer::class.java)
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
            task.apply(proxy)!!
        } catch (e: ClassNotFoundException) {
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
