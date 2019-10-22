package net.corda.serialization.djvm.deserializers

import org.apache.qpid.proton.amqp.Decimal32
import java.util.function.Function

class Decimal32Deserializer : Function<IntArray, Decimal32> {
    override fun apply(underlying: IntArray): Decimal32 {
        return Decimal32(underlying[0])
    }
}
