package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.DefaultCacheProvider

/**
 * The quickest way to find a serializer, if one has already been generated, is to look it up by type descriptor.
 *
 * This registry gets shared around between various participants that might want to use it as a lookup, or register
 * serialisers that they have created with it.
 */
interface DescriptorBasedSerializerRegistry<T> {
    operator fun get(descriptor: String): T?
    operator fun set(descriptor: String, serializer: T)
    fun getOrBuild(descriptor: String, builder: () -> T): T
    val size: Int
}

class AMQPDescriptorBasedSerializerLookupRegistry: DescriptorBasedSerializerRegistry<AMQPSerializer<Any>> {

    private val registry: MutableMap<String, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()

    override val size: Int get() = registry.size

    override fun get(descriptor: String): AMQPSerializer<Any>? = registry[descriptor]

    override fun set(descriptor: String, serializer: AMQPSerializer<Any>) {
        registry.putIfAbsent(descriptor, serializer)
    }

    override fun getOrBuild(descriptor: String, builder: () -> AMQPSerializer<Any>) =
            get(descriptor) ?: builder().also { newSerializer -> this[descriptor] = newSerializer }
}