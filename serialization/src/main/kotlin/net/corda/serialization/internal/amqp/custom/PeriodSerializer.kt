package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.Period

/**
 * A serializer for [Period] that uses a proxy object to write out the integer form.
 */
class PeriodSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Period, PeriodSerializer.PeriodProxy>(Period::class.java, PeriodProxy::class.java, factory) {
    override fun toProxy(obj: Period): PeriodProxy = PeriodProxy(obj.years, obj.months, obj.days)

    override fun fromProxy(proxy: PeriodProxy): Period = Period.of(proxy.years, proxy.months, proxy.days)

    data class PeriodProxy(val years: Int, val months: Int, val days: Int)
}