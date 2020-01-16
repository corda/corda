package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.LocalTimeDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.LocalTimeSerializer.LocalTimeProxy
import java.time.LocalTime
import java.util.function.Function

class SandboxLocalTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(LocalTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(LocalTimeProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(LocalTimeDeserializer::class.java)

    override val deserializationAliases = aliasFor(LocalTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
