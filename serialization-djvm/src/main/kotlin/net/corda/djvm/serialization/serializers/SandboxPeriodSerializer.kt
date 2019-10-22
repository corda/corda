package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.PeriodDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.PeriodSerializer.PeriodProxy
import java.time.Period
import java.util.Collections.singleton
import java.util.function.Function

class SandboxPeriodSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(Period::class.java),
    proxyClass = classLoader.toSandboxAnyClass(PeriodProxy::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, PeriodDeserializer::class.java)

    override val deserializationAliases: Set<Class<*>> = singleton(Period::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
