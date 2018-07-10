package net.corda.serialization.internal.amqp.custom

import net.corda.core.KeepForDJVM
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.OffsetTime
import java.util.*

/**
 * A serializer for [OffsetTime] that uses a proxy object to write out the time and zone offset.
 */
class OptionalSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Optional<*>, OptionalSerializer.OptionalProxy>(Optional::class.java, OptionalProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalTimeSerializer(factory), ZoneIdSerializer(factory))

    override fun toProxy(obj: java.util.Optional<*>): OptionalProxy {
        return OptionalProxy(obj.orElse(null))
    }

    override fun fromProxy(proxy: OptionalProxy): Optional<*>{
        return Optional.ofNullable(proxy.item)
    }

    @KeepForDJVM
    data class OptionalProxy(val item: Any?)
}