package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.InstantDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.InstantSerializer.InstantProxy
import java.time.Instant
import java.util.Collections.singleton
import java.util.function.Function

class SandboxInstantSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(Instant::class.java),
    proxyClass = classLoader.toSandboxAnyClass(InstantProxy::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, InstantDeserializer::class.java)

    override val deserializationAliases: Set<Class<*>> = singleton(Instant::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
