package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.MonthDayDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.MonthDaySerializer.MonthDayProxy
import java.time.MonthDay
import java.util.function.Function

class SandboxMonthDaySerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(MonthDay::class.java),
    proxyClass = classLoader.toSandboxAnyClass(MonthDayProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(MonthDayDeserializer::class.java)

    override val deserializationAliases = aliasFor(MonthDay::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
