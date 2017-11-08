package net.corda.vega.plugin.customserializers

import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import com.opengamma.strata.market.param.CurrencyParameterSensitivity
import net.corda.core.serialization.CordaCustomSerializer
import net.corda.core.serialization.CordaCustomSerializerProxy
import net.corda.core.serialization.SerializationCustomSerializer
import java.lang.reflect.Type

@CordaCustomSerializer
@Suppress("UNUSED")
class CurrencyParameterSensitivitiesSerializer : SerializationCustomSerializer {
    @CordaCustomSerializerProxy
    data class Proxy(val sensitivities: List<CurrencyParameterSensitivity>)

    override val type: Type get() = CurrencyParameterSensitivities::class.java
    override val ptype: Type get() = Proxy::class.java

    override fun fromProxy(proxy: Any): Any = CurrencyParameterSensitivities.of ((proxy as Proxy).sensitivities)
    override fun toProxy(obj: Any): Any = Proxy ((obj as CurrencyParameterSensitivities).sensitivities)
}