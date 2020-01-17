package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.LocalDateTimeDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.LocalDateTimeSerializer.LocalDateTimeProxy
import java.time.LocalDateTime
import java.util.function.Function

class SandboxLocalDateTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(LocalDateTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(LocalDateTimeProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(LocalDateTimeDeserializer::class.java)

    override val deserializationAliases = aliasFor(LocalDateTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
