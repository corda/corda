package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.ZonedDateTimeSerializer.ZonedDateTimeProxy
import java.util.function.Function

class ZonedDateTimeDeserializer : Function<ZonedDateTimeProxy, Array<out Any>?> {
    override fun apply(proxy: ZonedDateTimeProxy): Array<out Any>? {
        return arrayOf(proxy.dateTime, proxy.offset, proxy.zone)
    }
}
