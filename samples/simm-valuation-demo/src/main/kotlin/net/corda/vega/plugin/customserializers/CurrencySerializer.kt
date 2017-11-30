package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.CordaCustomSerializer
import net.corda.core.serialization.CordaCustomSerializerProxy
import net.corda.core.serialization.SerializationCustomSerializer
import java.lang.reflect.Type

@CordaCustomSerializer
@Suppress("UNUSED")
class CurrencySerializer : SerializationCustomSerializer<Currency, CurrencySerializer.Proxy> {
    @CordaCustomSerializerProxy
    data class Proxy(val currency: String)

    override val type: Type get() = Currency::class.java
    override val ptype: Type get() = Proxy::class.java

    override fun fromProxy(proxy: Proxy) = Currency.parse(proxy.currency)
    override fun toProxy(obj: Currency) = Proxy(obj.toString())
}
