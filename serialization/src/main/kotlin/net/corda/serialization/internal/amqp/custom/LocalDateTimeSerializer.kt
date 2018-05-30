package net.corda.serialization.internal.amqp.custom

import net.corda.core.Deterministic
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A serializer for [LocalDateTime] that uses a proxy object to write out the date and time.
 */
class LocalDateTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<LocalDateTime, LocalDateTimeSerializer.LocalDateTimeProxy>(LocalDateTime::class.java, LocalDateTimeProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LocalDateSerializer(factory), LocalTimeSerializer(factory))

    override fun toProxy(obj: LocalDateTime): LocalDateTimeProxy = LocalDateTimeProxy(obj.toLocalDate(), obj.toLocalTime())

    override fun fromProxy(proxy: LocalDateTimeProxy): LocalDateTime = LocalDateTime.of(proxy.date, proxy.time)

    @Deterministic
    data class LocalDateTimeProxy(val date: LocalDate, val time: LocalTime)
}