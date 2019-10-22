package net.corda.serialization.djvm.deserializers

import org.apache.qpid.proton.amqp.UnsignedShort
import java.util.function.Function

class UnsignedShortDeserializer : Function<ShortArray, UnsignedShort> {
    override fun apply(underlying: ShortArray): UnsignedShort {
        return UnsignedShort(underlying[0])
    }
}
