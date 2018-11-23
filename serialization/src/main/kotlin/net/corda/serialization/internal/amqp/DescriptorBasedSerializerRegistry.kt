package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.DefaultCacheProvider

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
}

class DefaultDescriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry {

    private val registry: MutableMap<String, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()

    override fun get(descriptor: String): AMQPSerializer<Any>? = registry[descriptor]

    override fun set(descriptor: String, serializer: AMQPSerializer<Any>) {
        registry.putIfAbsent(descriptor, serializer)
    }

    override fun getOrBuild(descriptor: String, builder: () -> AMQPSerializer<Any>) =
            registry.getOrPut(descriptor) { builder() }
}