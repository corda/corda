package net.corda.serialization.internal.amqp.factories

import net.corda.core.internal.reflection.DefaultCacheProvider
import net.corda.serialization.internal.amqp.api.AMQPSerializer
import net.corda.serialization.internal.amqp.api.DescriptorBasedSerializerRegistry

class DefaultDescriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry {

    private val registry: MutableMap<String, AMQPSerializer<Any>> = DefaultCacheProvider.createCache()

    override fun get(descriptor: String): AMQPSerializer<Any>? = registry[descriptor]

    override fun set(descriptor: String, serializer: AMQPSerializer<Any>) {
        registry.putIfAbsent(descriptor, serializer)
    }

    override fun getOrBuild(descriptor: String, builder: () -> AMQPSerializer<Any>) =
            registry.getOrPut(descriptor) { builder() }
}