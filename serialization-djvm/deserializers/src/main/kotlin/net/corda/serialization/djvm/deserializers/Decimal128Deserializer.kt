package net.corda.serialization.djvm.deserializers

import org.apache.qpid.proton.amqp.Decimal128
import java.util.function.Function

class Decimal128Deserializer : Function<LongArray, Decimal128> {
    override fun apply(underlying: LongArray): Decimal128 {
        return Decimal128(underlying[0], underlying[1])
    }
}
