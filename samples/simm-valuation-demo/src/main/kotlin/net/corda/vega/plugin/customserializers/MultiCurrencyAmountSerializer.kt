package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.*
import java.lang.reflect.Type

@CordaCustomSerializer
@Suppress("UNUSED")
class MultiCurrencyAmountSerializer :
        SerializationCustomSerializer<MultiCurrencyAmount, MultiCurrencyAmountSerializer.Proxy> {
    @CordaCustomSerializerProxy
    data class Proxy(val curencies : Map<Currency, Double>)

    override fun toProxy(obj: MultiCurrencyAmount) = Proxy(obj.toMap())
    override fun fromProxy(proxy: Proxy) = MultiCurrencyAmount.of(proxy.curencies)

    override val type: Type get() = MultiCurrencyAmount::class.java
    override val ptype: Type get() = Proxy::class.java
}



