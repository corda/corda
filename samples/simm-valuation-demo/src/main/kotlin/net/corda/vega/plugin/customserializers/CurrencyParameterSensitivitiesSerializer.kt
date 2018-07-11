package net.corda.vega.plugin.customserializers

import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import com.opengamma.strata.market.param.CurrencyParameterSensitivity
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("UNUSED")
class CurrencyParameterSensitivitiesSerializer :
        SerializationCustomSerializer<CurrencyParameterSensitivities, CurrencyParameterSensitivitiesSerializer.Proxy> {
    data class Proxy(val sensitivities: List<CurrencyParameterSensitivity>)

    override fun fromProxy(proxy: Proxy): CurrencyParameterSensitivities = CurrencyParameterSensitivities.of(proxy.sensitivities)
    override fun toProxy(obj: CurrencyParameterSensitivities) = Proxy(obj.sensitivities)
}