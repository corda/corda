package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.OffsetTimeDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.OffsetTimeSerializer.OffsetTimeProxy
import java.time.OffsetTime
import java.util.function.Function

class SandboxOffsetTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(OffsetTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(OffsetTimeProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(OffsetTimeDeserializer::class.java)

    override val deserializationAliases = aliasFor(OffsetTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
