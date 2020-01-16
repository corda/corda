package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.EnumSetDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.EnumSetSerializer.EnumSetProxy
import java.util.Collections.singleton
import java.util.EnumSet
import java.util.function.Function

class SandboxEnumSetSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(EnumSet::class.java),
    proxyClass = classLoader.toSandboxAnyClass(EnumSetProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(EnumSetDeserializer::class.java)

    override val additionalSerializers: Set<CustomSerializer<out Any>> = singleton(
        SandboxClassSerializer(classLoader, taskFactory, factory)
    )

    override val deserializationAliases = aliasFor(EnumSet::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
