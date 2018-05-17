package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.LocalTime

/**
 * A serializer for [LocalTime] that uses a proxy object to write out the hours, minutes, seconds and the nanos.
 */
class LocalTimeSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<LocalTime, LocalTimeSerializer.LocalTimeProxy>(LocalTime::class.java, LocalTimeProxy::class.java, factory) {
    override fun toProxy(obj: LocalTime): LocalTimeProxy = LocalTimeProxy(obj.hour.toByte(), obj.minute.toByte(), obj.second.toByte(), obj.nano)

    override fun fromProxy(proxy: LocalTimeProxy): LocalTime = LocalTime.of(proxy.hour.toInt(), proxy.minute.toInt(), proxy.second.toInt(), proxy.nano)

    data class LocalTimeProxy(val hour: Byte, val minute: Byte, val second: Byte, val nano: Int)
}