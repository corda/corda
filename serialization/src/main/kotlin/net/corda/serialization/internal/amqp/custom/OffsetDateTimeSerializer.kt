package net.corda.serialization.internal.amqp.custom

import net.corda.core.KeepForDJVM
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A serializer for [OffsetDateTime] that uses a proxy object to write out the date and zone offset.
 */
class OffsetDateTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<OffsetDateTime, OffsetDateTimeSerializer.OffsetDateTimeProxy>(OffsetDateTime::class.java, OffsetDateTimeProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalDateTimeSerializer(factory), ZoneIdSerializer(factory))

    override fun toProxy(obj: OffsetDateTime): OffsetDateTimeProxy = OffsetDateTimeProxy(obj.toLocalDateTime(), obj.offset)

    override fun fromProxy(proxy: OffsetDateTimeProxy): OffsetDateTime = OffsetDateTime.of(proxy.dateTime, proxy.offset)

    @KeepForDJVM
    data class OffsetDateTimeProxy(val dateTime: LocalDateTime, val offset: ZoneOffset)
}