package net.corda.vega.plugin.customserializers

import net.corda.core.serialization.CordaCustomSerializer
import net.corda.core.serialization.CordaCustomSerializerProxy
import net.corda.core.serialization.SerializationCustomSerializer
import com.opengamma.strata.collect.array.DoubleArray
import java.lang.reflect.Type

@CordaCustomSerializer
@Suppress("UNUSED")
class DoubleArraySerializer : SerializationCustomSerializer {
    @CordaCustomSerializerProxy
    data class Proxy(val amount: kotlin.DoubleArray)

    override val type: Type get() = DoubleArray::class.java
    override val ptype: Type get() = Proxy::class.java

    override fun fromProxy(proxy: Any): Any = DoubleArray.copyOf((proxy as Proxy).amount)
    override fun toProxy(obj: Any): Any = Proxy((obj as DoubleArray).toArray())
}
