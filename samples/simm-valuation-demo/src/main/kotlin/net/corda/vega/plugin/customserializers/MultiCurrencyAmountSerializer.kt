package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.*
import java.lang.reflect.Type

@CordaCustomSerializer
@Suppress("UNUSED")
class MultiCurrencyAmountSerializer : SerializationCustomSerializer {
    @CordaCustomSerializerProxy
    data class Proxy(val curencies : Map<Currency, Double>)

    override fun toProxy(obj: Any): Any = Proxy((obj as MultiCurrencyAmount).toMap())
    override fun fromProxy(proxy: Any): Any = MultiCurrencyAmount.of((proxy as Proxy).curencies)

    override val type: Type get() = MultiCurrencyAmount::class.java
    override val ptype: Type get() = Proxy::class.java
}



