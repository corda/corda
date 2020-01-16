package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.OffsetDateTimeDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.OffsetDateTimeSerializer.OffsetDateTimeProxy
import java.time.OffsetDateTime
import java.util.function.Function

class SandboxOffsetDateTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(OffsetDateTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(OffsetDateTimeProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(OffsetDateTimeDeserializer::class.java)

    override val deserializationAliases = aliasFor(OffsetDateTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
