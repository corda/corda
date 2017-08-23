package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import java.time.Duration

/**
 * A serializer for [Duration] that uses a proxy object to write out the seconds and the nanos.
 */
class DurationSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Duration, DurationSerializer.DurationProxy>(Duration::class.java, DurationProxy::class.java, factory) {
    override fun toProxy(obj: Duration): DurationProxy = DurationProxy(obj.seconds, obj.nano)

    override fun fromProxy(proxy: DurationProxy): Duration = Duration.ofSeconds(proxy.seconds, proxy.nanos.toLong())

    data class DurationProxy(val seconds: Long, val nanos: Int)
}