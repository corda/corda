package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.LocalDateTimeSerializer.LocalDateTimeProxy
import java.time.LocalDateTime
import java.util.function.Function

class LocalDateTimeDeserializer : Function<LocalDateTimeProxy, LocalDateTime> {
    override fun apply(proxy: LocalDateTimeProxy): LocalDateTime {
        return LocalDateTime.of(proxy.date, proxy.time)
    }
}
