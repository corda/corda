package net.corda.djvm.serialization.deserializers

import org.apache.qpid.proton.amqp.UnsignedLong
import java.util.function.Function

class UnsignedLongDeserializer : Function<LongArray, UnsignedLong> {
    override fun apply(underlying: LongArray): UnsignedLong {
        return UnsignedLong(underlying[0])
    }
}
