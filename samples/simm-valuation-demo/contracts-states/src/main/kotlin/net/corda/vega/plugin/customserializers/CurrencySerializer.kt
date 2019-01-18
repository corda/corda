package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("UNUSED")
class CurrencySerializer : SerializationCustomSerializer<Currency, CurrencySerializer.Proxy> {
    data class Proxy(val currency: String)

    override fun fromProxy(proxy: Proxy): Currency = Currency.parse(proxy.currency)
    override fun toProxy(obj: Currency) = Proxy(obj.toString())
}
