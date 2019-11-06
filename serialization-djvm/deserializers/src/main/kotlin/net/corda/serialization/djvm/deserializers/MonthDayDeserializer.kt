package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.MonthDaySerializer.MonthDayProxy
import java.time.MonthDay
import java.util.function.Function

class MonthDayDeserializer : Function<MonthDayProxy, MonthDay> {
    override fun apply(proxy: MonthDayProxy): MonthDay {
        return MonthDay.of(proxy.month.toInt(), proxy.day.toInt())
    }
}
