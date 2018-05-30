package net.corda.serialization.internal.amqp.custom

import net.corda.core.Deterministic
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.time.Instant

/**
 * A serializer for [Instant] that uses a proxy object to write out the seconds since the epoch and the nanos.
 */
class InstantSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Instant, InstantSerializer.InstantProxy>(Instant::class.java, InstantProxy::class.java, factory) {
    override fun toProxy(obj: Instant): InstantProxy = InstantProxy(obj.epochSecond, obj.nano)

    override fun fromProxy(proxy: InstantProxy): Instant = Instant.ofEpochSecond(proxy.epochSeconds, proxy.nanos.toLong())

    @Deterministic
    data class InstantProxy(val epochSeconds: Long, val nanos: Int)
}