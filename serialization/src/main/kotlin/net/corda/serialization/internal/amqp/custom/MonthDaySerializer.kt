package net.corda.serialization.internal.amqp.custom

import net.corda.core.KeepForDJVM
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.MonthDay

/**
 * A serializer for [MonthDay] that uses a proxy object to write out the integer form.
 */
class MonthDaySerializer(factory: SerializerFactory)
    : CustomSerializer.Proxy<MonthDay, MonthDaySerializer.MonthDayProxy>(
        MonthDay::class.java, MonthDayProxy::class.java, factory
) {
    override fun toProxy(obj: MonthDay): MonthDayProxy = MonthDayProxy(obj.monthValue.toByte(), obj.dayOfMonth.toByte())

    override fun fromProxy(proxy: MonthDayProxy): MonthDay = MonthDay.of(proxy.month.toInt(), proxy.day.toInt())

    @KeepForDJVM
    data class MonthDayProxy(val month: Byte, val day: Byte)
}