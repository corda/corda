package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.YearMonthDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.YearMonthSerializer.YearMonthProxy
import java.time.YearMonth
import java.util.Collections.singleton
import java.util.function.BiFunction

class SandboxYearMonthSerializer(
    classLoader: SandboxClassLoader,
    private val executor: BiFunction<in Any, in Any?, out Any?>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.loadClassForSandbox(YearMonth::class.java),
    proxyClass = classLoader.loadClassForSandbox(YearMonthProxy::class.java),
    factory = factory
) {
    private val task = classLoader.loadClassForSandbox(YearMonthDeserializer::class.java).newInstance()

    override val deserializationAliases: Set<Class<*>> = singleton(YearMonth::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return executor.apply(task, proxy)!!
    }
}
