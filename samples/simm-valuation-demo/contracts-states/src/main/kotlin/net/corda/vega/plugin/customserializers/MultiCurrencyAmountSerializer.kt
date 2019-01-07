package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.*

@Suppress("UNUSED")
class MultiCurrencyAmountSerializer :
        SerializationCustomSerializer<MultiCurrencyAmount, MultiCurrencyAmountSerializer.Proxy> {
    data class Proxy(val curencies: Map<Currency, Double>)

    override fun toProxy(obj: MultiCurrencyAmount) = Proxy(obj.toMap())
    override fun fromProxy(proxy: Proxy): MultiCurrencyAmount = MultiCurrencyAmount.of(proxy.curencies)
}



