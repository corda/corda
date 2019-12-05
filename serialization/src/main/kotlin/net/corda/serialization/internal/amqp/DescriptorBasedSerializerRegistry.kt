package net.corda.serialization.internal.amqp

import net.corda.core.serialization.internal.MissingSerializerException
import net.corda.serialization.internal.model.DefaultCacheProvider
import java.net.URL

/**
 * The quickest way to find a serializer, if one has already been generated, is to look it up by type descriptor.
 *
 * This registry gets shared around between various participants that might want to use it as a lookup, or register
 * serialisers that they have created with it.
 */
interface DescriptorBasedSerializerRegistry {
    operator fun get(descriptor: String): AMQPSerializer<Any>?
    operator fun set(descriptor: String, serializer: AMQPSerializer<Any>)
    fun getOrBuild(descriptor: String, builder: () -> AMQPSerializer<Any>): AMQPSerializer<Any>
    fun setDisabled(descriptor: String, serializerLocation: URL)
}

class DefaultDescriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry {

    private val registry: MutableMap<String, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()
    private val disabled: MutableMap<String, URL> = DefaultCacheProvider.createCache()

    override fun get(descriptor: String): AMQPSerializer<Any>? = registry[descriptor]

    override fun set(descriptor: String, serializer: AMQPSerializer<Any>) {
        registry.putIfAbsent(descriptor, serializer)
    }

    override fun setDisabled(descriptor: String, serializerLocation: URL) {
        // Serializers inside the registry MUST take precedence
        // over any disabled serializers.
        if (!registry.containsKey(descriptor)) {
            disabled.putIfAbsent(descriptor, serializerLocation)
        }
    }

    override fun getOrBuild(descriptor: String, builder: () -> AMQPSerializer<Any>) =
            registry.getOrPut(descriptor) {
                // We disable serializers that are not defined by the context's
                // deserialization classloader because the JVM will almost certainly be
                // unable to link them with any class within the serialization context.
                val disabledSerializerLocation = disabled[descriptor]
                if (disabledSerializerLocation != null) {
                    throw MissingSerializerException(
                        message = "Serializer for descriptor $descriptor is outside context",
                        typeDescriptor = descriptor,
                        serializerLocation =  disabledSerializerLocation
                    )
                }
                builder()
            }
}