package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.LocalDateSerializer.LocalDateProxy
import java.time.LocalDate
import java.util.function.Function

class LocalDateDeserializer : Function<LocalDateProxy, LocalDate> {
    override fun apply(proxy: LocalDateProxy): LocalDate {
        return LocalDate.of(proxy.year, proxy.month.toInt(), proxy.day.toInt())
    }
}
