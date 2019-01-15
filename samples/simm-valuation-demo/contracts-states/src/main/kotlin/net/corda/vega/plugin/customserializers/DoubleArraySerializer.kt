package net.corda.vega.plugin.customserializers

import net.corda.core.serialization.SerializationCustomSerializer
import com.opengamma.strata.collect.array.DoubleArray
import java.util.*

@Suppress("UNUSED")
class DoubleArraySerializer : SerializationCustomSerializer<DoubleArray, DoubleArraySerializer.Proxy> {
    data class Proxy(val amount: kotlin.DoubleArray) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Proxy

            if (!Arrays.equals(amount, other.amount)) return false

            return true
        }

        override fun hashCode(): Int {
            return Arrays.hashCode(amount)
        }
    }

    override fun fromProxy(proxy: Proxy): DoubleArray = DoubleArray.copyOf(proxy.amount)
    override fun toProxy(obj: DoubleArray) = Proxy(obj.toArray())
}
