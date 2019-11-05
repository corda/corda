package net.corda.serialization.djvm.deserializers

import org.apache.qpid.proton.amqp.Decimal64
import java.util.function.Function

class Decimal64Deserializer : Function<LongArray, Decimal64> {
    override fun apply(underlying: LongArray): Decimal64 {
        return Decimal64(underlying[0])
    }
}
