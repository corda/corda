package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.YearMonthSerializer.YearMonthProxy
import java.time.YearMonth
import java.util.function.Function

class YearMonthDeserializer : Function<YearMonthProxy, YearMonth> {
    override fun apply(proxy: YearMonthProxy): YearMonth {
        return YearMonth.of(proxy.year, proxy.month.toInt())
    }
}
