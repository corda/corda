package net.corda.serialization.djvm.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.ZonedDateTimeDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.ZonedDateTimeSerializer.ZonedDateTimeProxy
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Collections.singleton
import java.util.function.Function

class SandboxZonedDateTimeSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(ZonedDateTime::class.java),
    proxyClass = classLoader.toSandboxAnyClass(ZonedDateTimeProxy::class.java),
    factory = factory
) {
    private val task = classLoader.createTaskFor(taskFactory, ZonedDateTimeDeserializer::class.java)
    private val creator: Function<in Any?, out Any?>

    init {
        val createTask = clazz.getMethod(
            "createDJVM",
            classLoader.toSandboxClass(LocalDateTime::class.java),
            classLoader.toSandboxClass(ZoneOffset::class.java),
            classLoader.toSandboxClass(ZoneId::class.java)
        )
        creator = task.andThen { input ->
            @Suppress("unchecked_cast", "SpreadOperator")
            createTask(null, *(input as Array<Any?>))!!
        }
    }

    override val deserializationAliases = aliasFor(ZonedDateTime::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return creator.apply(proxy)!!
    }
}
