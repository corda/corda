package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.MonthDayDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.MonthDaySerializer.MonthDayProxy
import java.time.MonthDay
import java.util.Collections.singleton
import java.util.function.Function

class SandboxMonthDaySerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(MonthDay::class.java),
    proxyClass = classLoader.toSandboxAnyClass(MonthDayProxy::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, MonthDayDeserializer::class.java)

    override val deserializationAliases: Set<Class<*>> = singleton(MonthDay::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
