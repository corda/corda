package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.YearMonth

/**
 * A serializer for [YearMonth] that uses a proxy object to write out the integer form.
 */
class YearMonthSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<YearMonth, YearMonthSerializer.YearMonthProxy>(YearMonth::class.java, YearMonthProxy::class.java, factory) {
    override fun toProxy(obj: YearMonth): YearMonthProxy = YearMonthProxy(obj.year, obj.monthValue.toByte())

    override fun fromProxy(proxy: YearMonthProxy): YearMonth = YearMonth.of(proxy.year, proxy.month.toInt())

    data class YearMonthProxy(val year: Int, val month: Byte)
}