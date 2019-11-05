package net.corda.serialization.djvm.deserializers

import org.apache.qpid.proton.amqp.UnsignedInteger
import java.util.function.Function

class UnsignedIntegerDeserializer : Function<IntArray, UnsignedInteger> {
    override fun apply(underlying: IntArray): UnsignedInteger {
        return UnsignedInteger(underlying[0])
    }
}
