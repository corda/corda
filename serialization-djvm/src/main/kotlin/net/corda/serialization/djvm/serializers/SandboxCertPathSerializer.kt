package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.DESERIALIZATION_CACHE_PROPERTY
import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.CertPathDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializationSchemas
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.CertPathSerializer.CertPathProxy
import java.security.cert.CertPath
import java.util.function.Function

class SandboxCertPathSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.toSandboxAnyClass(CertPath::class.java),
    proxyClass = classLoader.toSandboxAnyClass(CertPathProxy::class.java),
    factory = factory
) {
    private val task = taskFactory.apply(CertPathDeserializer::class.java)

    override val deserializationAliases = aliasFor(CertPath::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return task.apply(proxy)!!
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Any {
        val proxy = readProxy(obj, schemas, input, context)
        // This requires [CertPathProxy] to have correct
        // implementations for [equals] and [hashCode].
        @Suppress("unchecked_cast")
        return (context.properties[DESERIALIZATION_CACHE_PROPERTY] as? MutableMap<Any, Any>)
            ?.computeIfAbsent(proxy, ::fromProxy)
            ?: fromProxy(proxy)
    }
}
