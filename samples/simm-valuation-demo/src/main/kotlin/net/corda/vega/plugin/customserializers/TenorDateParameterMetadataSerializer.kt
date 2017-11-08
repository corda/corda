package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.date.Tenor
import com.opengamma.strata.market.param.TenorDateParameterMetadata
import net.corda.core.serialization.*
import java.lang.reflect.Type
import java.time.LocalDate

@CordaCustomSerializer
@Suppress("UNUSED")
class TenorDateParameterMetadataSerializer : SerializationCustomSerializer {
    @CordaCustomSerializerProxy
    data class Proxy(val tenor: Tenor, val date: LocalDate, val identifier: Tenor, val label: String)

    override val type: Type get() = TenorDateParameterMetadata::class.java
    override val ptype: Type get() = Proxy::class.java

    override fun toProxy(obj: Any): Any = Proxy(
            (obj as TenorDateParameterMetadata).tenor, obj.date, obj.identifier, obj.label)

    override fun fromProxy(proxy: Any): Any = TenorDateParameterMetadata.of(
            (proxy as Proxy).date, proxy.tenor, proxy.label)
}
