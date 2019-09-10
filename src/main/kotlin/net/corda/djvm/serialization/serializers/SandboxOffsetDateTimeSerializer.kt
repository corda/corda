package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.OffsetDateTimeDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.OffsetDateTimeSerializer.OffsetDateTimeProxy
import java.time.OffsetDateTime
import java.util.Collections.singleton
import java.util.function.Function

class SandboxOffsetDateTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(OffsetDateTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(OffsetDateTimeProxy::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, OffsetDateTimeDeserializer::class.java)

    override val deserializationAliases: Set<Class<*>> = singleton(OffsetDateTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
