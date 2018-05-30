package net.corda.serialization.internal.amqp.custom

import net.corda.core.Deterministic
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

/**
 * A serializer for [OffsetTime] that uses a proxy object to write out the time and zone offset.
 */
class OffsetTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<OffsetTime, OffsetTimeSerializer.OffsetTimeProxy>(OffsetTime::class.java, OffsetTimeProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalTimeSerializer(factory), ZoneIdSerializer(factory))

    override fun toProxy(obj: OffsetTime): OffsetTimeProxy = OffsetTimeProxy(obj.toLocalTime(), obj.offset)

    override fun fromProxy(proxy: OffsetTimeProxy): OffsetTime = OffsetTime.of(proxy.time, proxy.offset)

    @Deterministic
    data class OffsetTimeProxy(val time: LocalTime, val offset: ZoneOffset)
}