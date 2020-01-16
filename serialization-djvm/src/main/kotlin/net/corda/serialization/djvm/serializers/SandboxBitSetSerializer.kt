package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.BitSetDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.BitSetSerializer.BitSetProxy
import java.util.BitSet
import java.util.function.Function

class SandboxBitSetSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(BitSet::class.java),
    proxyClass = classLoader.toSandboxAnyClass(BitSetProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(BitSetDeserializer::class.java)

    override val deserializationAliases = aliasFor(BitSet::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
