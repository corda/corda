package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.ZoneIdDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.ZoneIdSerializer.ZoneIdProxy
import java.time.ZoneId
import java.util.function.Function

class SandboxZoneIdSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(ZoneId::class.java),
    proxyClass = classLoader.toSandboxAnyClass(ZoneIdProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(ZoneIdDeserializer::class.java)

    override val revealSubclassesInSchema: Boolean = true

    override val deserializationAliases = aliasFor(ZoneId::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
