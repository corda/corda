package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.InstantSerializer.InstantProxy
import java.time.Instant
import java.util.function.Function

class InstantDeserializer : Function<InstantProxy, Instant> {
    override fun apply(proxy: InstantProxy): Instant {
        return Instant.ofEpochSecond(proxy.epochSeconds, proxy.nanos.toLong())
    }
}
