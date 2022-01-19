package net.corda.client.rpc.internal

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.internal.createInstancesOfClassesImplementing
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import java.util.ServiceLoader

internal object SerializationEnvironmentHelper {

    internal fun ensureEffectiveSerializationEnvSet(classLoader: ClassLoader?,
                                                   customSerializers: Set<SerializationCustomSerializer<*, *>>?) {
        try {
            effectiveSerializationEnv
        } catch (e: IllegalStateException) {
            try {
                val cache = Caffeine.newBuilder().maximumSize(128)
                        .build<SerializationFactoryCacheKey, SerializerFactory>().asMap()

                // If the client has explicitly provided a classloader use this one to scan for custom serializers,
                // otherwise use the current one.
                val serializationClassLoader = classLoader ?: this.javaClass.classLoader
                // If the client has explicitly provided a set of custom serializers, avoid performing any scanning and use these instead.
                val discoveredCustomSerializers = customSerializers ?: createInstancesOfClassesImplementing(
                        serializationClassLoader,
                        SerializationCustomSerializer::class.java
                )

                val serializationWhitelists = ServiceLoader.load(
                        SerializationWhitelist::class.java,
                        serializationClassLoader
                ).toSet()

                AMQPClientSerializationScheme.initialiseSerialization(
                        serializationClassLoader,
                        discoveredCustomSerializers,
                        serializationWhitelists,
                        cache
                )
            } catch (e: IllegalStateException) {
                // Race e.g. two of these constructed in parallel, ignore.
            }
        }
    }
}