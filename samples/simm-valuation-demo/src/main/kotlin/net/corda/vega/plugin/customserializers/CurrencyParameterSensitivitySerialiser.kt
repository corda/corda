package net.corda.vega.plugin.customserializers

import com.opengamma.strata.market.param.CurrencyParameterSensitivity
import com.opengamma.strata.market.param.ParameterMetadata
import com.opengamma.strata.data.MarketDataName
import com.opengamma.strata.collect.array.DoubleArray
import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.CordaCustomSerializer
import net.corda.core.serialization.CordaCustomSerializerProxy
import net.corda.core.serialization.SerializationCustomSerializer
import java.lang.reflect.Type

@CordaCustomSerializer
@Suppress("UNUSED")
class CurrencyParameterSensitivitySerializer :
        SerializationCustomSerializer<CurrencyParameterSensitivity, CurrencyParameterSensitivitySerializer.Proxy> {
    @CordaCustomSerializerProxy
    data class Proxy(val currency: Currency, val marketDataName: MarketDataName<*>,
                     val parameterMetadata: List<ParameterMetadata>,
                     val sensitivity: DoubleArray)

    override val type: Type get() = CurrencyParameterSensitivity::class.java
    override val ptype: Type get() = Proxy::class.java

    override fun fromProxy(proxy: CurrencyParameterSensitivitySerializer.Proxy) =
            CurrencyParameterSensitivity.of(
                    proxy.marketDataName,
                    proxy.parameterMetadata,
                    proxy.currency,
                    proxy.sensitivity)

    override fun toProxy(obj: CurrencyParameterSensitivity) = Proxy((obj as CurrencyParameterSensitivity).currency,
            obj.marketDataName, obj.parameterMetadata, obj.sensitivity)
}
