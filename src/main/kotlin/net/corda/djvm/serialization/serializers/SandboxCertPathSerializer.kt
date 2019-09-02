package net.corda.djvm.serialization.serializers

import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.CertPathDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.CertPathSerializer.CertPathProxy
import java.security.cert.CertPath
import java.util.Collections.singleton
import java.util.function.BiFunction

class SandboxCertPathSerializer(
    classLoader: SandboxClassLoader,
    private val executor: BiFunction<in Any, in Any?, out Any?>,
    factory: SerializerFactory
) : CustomSerializer.Proxy<Any, Any>(
    clazz = classLoader.loadClassForSandbox(CertPath::class.java),
    proxyClass = classLoader.loadClassForSandbox(CertPathProxy::class.java),
    factory = factory
) {
    private val task = classLoader.loadClassForSandbox(CertPathDeserializer::class.java).newInstance()

    override val deserializationAliases: Set<Class<*>> = singleton(CertPath::class.java)

    override fun toProxy(obj: Any): Any = abortReadOnly()

    override fun fromProxy(proxy: Any): Any {
        return executor.apply(task, proxy)!!
    }
}
