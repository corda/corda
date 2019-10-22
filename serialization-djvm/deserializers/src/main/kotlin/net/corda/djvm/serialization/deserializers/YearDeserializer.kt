package net.corda.djvm.serialization.deserializers

import net.corda.serialization.internal.amqp.custom.YearSerializer.YearProxy
import java.time.Year
import java.util.function.Function

class YearDeserializer : Function<YearProxy, Year> {
    override fun apply(proxy: YearProxy): Year {
        return Year.of(proxy.year)
    }
}
