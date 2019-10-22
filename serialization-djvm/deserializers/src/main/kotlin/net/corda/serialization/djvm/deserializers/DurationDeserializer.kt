package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.DurationSerializer.DurationProxy
import java.time.Duration
import java.util.function.Function

class DurationDeserializer : Function<DurationProxy, Duration> {
    override fun apply(proxy: DurationProxy): Duration {
        return Duration.ofSeconds(proxy.seconds, proxy.nanos.toLong())
    }
}
