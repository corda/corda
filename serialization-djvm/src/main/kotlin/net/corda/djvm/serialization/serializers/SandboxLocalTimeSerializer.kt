package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.LocalTimeDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.LocalTimeSerializer.LocalTimeProxy
import java.time.LocalTime
import java.util.Collections.singleton
import java.util.function.Function

class SandboxLocalTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(LocalTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(LocalTimeProxy::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, LocalTimeDeserializer::class.java)

    override val deserializationAliases: Set<Class<*>> = singleton(LocalTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
