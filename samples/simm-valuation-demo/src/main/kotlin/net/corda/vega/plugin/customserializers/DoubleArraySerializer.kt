package net.corda.vega.plugin.customserializers

import net.corda.core.serialization.CordaCustomSerializer
import net.corda.core.serialization.SerializationCustomSerializer
import com.opengamma.strata.collect.array.DoubleArray

@CordaCustomSerializer
@Suppress("UNUSED")
class DoubleArraySerializer : SerializationCustomSerializer<DoubleArray, DoubleArraySerializer.Proxy> {
    data class Proxy(val amount: kotlin.DoubleArray)

    override fun fromProxy(proxy: Proxy) = DoubleArray.copyOf(proxy.amount)
    override fun toProxy(obj: DoubleArray) = Proxy(obj.toArray())
}
