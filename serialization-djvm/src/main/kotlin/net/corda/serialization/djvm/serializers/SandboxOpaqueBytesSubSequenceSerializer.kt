package net.corda.serialization.djvm.serializers

import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.OpaqueBytesSubSequence
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.OpaqueBytesSubSequenceDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.util.function.Function

class SandboxOpaqueBytesSubSequenceSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(OpaqueBytesSubSequence::class.java),
    proxyClass = classLoader.toSandboxAnyClass(OpaqueBytes::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(OpaqueBytesSubSequenceDeserializer::class.java)

    override val deserializationAliases = aliasFor(OpaqueBytesSubSequence::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
