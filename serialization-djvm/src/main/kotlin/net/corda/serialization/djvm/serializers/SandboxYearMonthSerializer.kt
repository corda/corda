package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.YearMonthDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.YearMonthSerializer.YearMonthProxy
import java.time.YearMonth
import java.util.function.Function

class SandboxYearMonthSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(YearMonth::class.java),
    proxyClass = classLoader.toSandboxAnyClass(YearMonthProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(YearMonthDeserializer::class.java)

    override val deserializationAliases = aliasFor(YearMonth::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }
}
