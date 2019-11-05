package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.PeriodSerializer.PeriodProxy
import java.time.Period
import java.util.function.Function

class PeriodDeserializer : Function<PeriodProxy, Period> {
    override fun apply(proxy: PeriodProxy): Period {
        return Period.of(proxy.years, proxy.months, proxy.days)
    }
}
