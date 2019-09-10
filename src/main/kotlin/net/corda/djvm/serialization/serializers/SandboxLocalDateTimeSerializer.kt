package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.LocalDateTimeDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.LocalDateTimeSerializer.LocalDateTimeProxy
import java.time.LocalDateTime
import java.util.Collections.singleton
import java.util.function.Function

class SandboxLocalDateTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(LocalDateTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(LocalDateTimeProxy::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, LocalDateTimeDeserializer::class.java)

    override val deserializationAliases: Set<Class<*>> = singleton(LocalDateTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
