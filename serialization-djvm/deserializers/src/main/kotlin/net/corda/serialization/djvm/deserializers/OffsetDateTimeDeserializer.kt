package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.OffsetDateTimeSerializer.OffsetDateTimeProxy
import java.time.OffsetDateTime
import java.util.function.Function

class OffsetDateTimeDeserializer : Function<OffsetDateTimeProxy, OffsetDateTime> {
    override fun apply(proxy: OffsetDateTimeProxy): OffsetDateTime {
        return OffsetDateTime.of(proxy.dateTime, proxy.offset)
    }
}
