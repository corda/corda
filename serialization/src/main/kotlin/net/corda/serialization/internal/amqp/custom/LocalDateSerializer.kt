package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.LocalDate

/**
 * A serializer for [LocalDate] that uses a proxy object to write out the year, month and day.
 */
class LocalDateSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<LocalDate, LocalDateSerializer.LocalDateProxy>(LocalDate::class.java, LocalDateProxy::class.java, factory) {
    override fun toProxy(obj: LocalDate): LocalDateProxy = LocalDateProxy(obj.year, obj.monthValue.toByte(), obj.dayOfMonth.toByte())

    override fun fromProxy(proxy: LocalDateProxy): LocalDate = LocalDate.of(proxy.year, proxy.month.toInt(), proxy.day.toInt())

    data class LocalDateProxy(val year: Int, val month: Byte, val day: Byte)
}