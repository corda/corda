package net.corda.djvm.serialization.serializers

import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.OpaqueBytesSubSequence
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.OpaqueBytesSubSequenceDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.util.Collections.singleton
import java.util.function.Function

class SandboxOpaqueBytesSubSequenceSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(OpaqueBytesSubSequence::class.java),
    proxyClass = classLoader.toSandboxAnyClass(OpaqueBytes::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, OpaqueBytesSubSequenceDeserializer::class.java)

    override val deserializationAliases: Set<Class<*>> = singleton(OpaqueBytesSubSequence::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
