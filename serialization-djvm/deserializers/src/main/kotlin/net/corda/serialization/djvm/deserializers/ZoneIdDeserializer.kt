package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.ZoneIdSerializer.ZoneIdProxy
import java.time.ZoneId
import java.util.function.Function

class ZoneIdDeserializer : Function<ZoneIdProxy, ZoneId> {
    override fun apply(proxy: ZoneIdProxy): ZoneId {
        return ZoneId.of(proxy.id)
    }
}
