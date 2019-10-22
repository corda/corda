package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.LocalTimeSerializer.LocalTimeProxy
import java.time.LocalTime
import java.util.function.Function

class LocalTimeDeserializer : Function<LocalTimeProxy, LocalTime> {
    override fun apply(proxy: LocalTimeProxy): LocalTime {
        return LocalTime.of(proxy.hour.toInt(), proxy.minute.toInt(), proxy.second.toInt(), proxy.nano)
    }
}
