package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.OffsetTimeSerializer.OffsetTimeProxy
import java.time.OffsetTime
import java.util.function.Function

class OffsetTimeDeserializer : Function<OffsetTimeProxy, OffsetTime> {
    override fun apply(proxy: OffsetTimeProxy): OffsetTime {
        return OffsetTime.of(proxy.time, proxy.offset)
    }
}
