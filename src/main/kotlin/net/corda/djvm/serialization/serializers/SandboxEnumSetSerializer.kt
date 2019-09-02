package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.EnumSetDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.EnumSetSerializer.EnumSetProxy
import java.util.*
import java.util.Collections.singleton
import java.util.function.BiFunction

class SandboxEnumSetSerializer(
    classLoader: SandboxClassLoader,
    private val executor: BiFunction<in Any, in Any?, out Any?>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.loadClassForSandbox(EnumSet::class.java),
    proxyClass = classLoader.loadClassForSandbox(EnumSetProxy::class.java),
    factory = factory
) {
    private val task = classLoader.loadClassForSandbox(EnumSetDeserializer::class.java).newInstance()

    override val additionalSerializers: Set<CustomSerializer<out Any>> = singleton(
        SandboxClassSerializer(classLoader, executor, factory)
    )

    override val deserializationAliases: Set<Class<*>> = singleton(EnumSet::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return executor.apply(task, proxy)!!
    }
}
