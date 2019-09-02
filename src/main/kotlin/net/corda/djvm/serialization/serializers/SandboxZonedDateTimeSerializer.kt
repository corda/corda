package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.ZonedDateTimeDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.ZonedDateTimeSerializer.ZonedDateTimeProxy
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Collections.singleton
import java.util.function.BiFunction

class SandboxZonedDateTimeSerializer(
    classLoader: SandboxClassLoader,
    executor: BiFunction<in Any, in Any?, out Any?>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.loadClassForSandbox(ZonedDateTime::class.java),
    proxyClass = classLoader.loadClassForSandbox(ZonedDateTimeProxy::class.java),
    factory = factory
) {
    private val task = classLoader.loadClassForSandbox(ZonedDateTimeDeserializer::class.java).newInstance()
    private val creator: BiFunction<in Any?, in Any?, out Any?>

    init {
        val createTask = clazz.getMethod(
            "createDJVM",
            classLoader.loadClassForSandbox(LocalDateTime::class.java),
            classLoader.loadClassForSandbox(ZoneOffset::class.java),
            classLoader.loadClassForSandbox(ZoneId::class.java)
        )
        creator = executor.andThen { input ->
            @Suppress("unchecked_cast")
            createTask(null, *(input as Array<Any>))!!
        }
    }

    override val deserializationAliases: Set<Class<*>> = singleton(ZonedDateTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return creator.apply(task, proxy)!!
    }
}
