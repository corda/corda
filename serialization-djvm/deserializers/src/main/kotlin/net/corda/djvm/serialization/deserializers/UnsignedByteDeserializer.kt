package net.corda.djvm.serialization.deserializers

import org.apache.qpid.proton.amqp.UnsignedByte
import java.util.function.Function

class UnsignedByteDeserializer : Function<ByteArray, UnsignedByte> {
    override fun apply(underlying: ByteArray): UnsignedByte {
        return UnsignedByte(underlying[0])
    }
}
