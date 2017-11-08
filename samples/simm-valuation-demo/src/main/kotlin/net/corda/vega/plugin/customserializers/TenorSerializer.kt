package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.date.Tenor
import net.corda.core.serialization.*
import java.lang.reflect.Type
import java.time.Period

@CordaCustomSerializer
@Suppress("UNUSED")
class TenorSerializer : SerializationCustomSerializer {
    @CordaCustomSerializerProxy
    data class Proxy(val years: Int, val months: Int, val days: Int, val name: String)

    override val type: Type get() = Tenor::class.java
    override val ptype: Type get() = Proxy::class.java

    override fun toProxy(obj: Any): Any = Proxy(
            (obj as Tenor).period.years, obj.period.months, obj.period.days, obj.toString())

    override fun fromProxy(proxy: Any): Any = Tenor.of (Period.of((proxy as Proxy).years, proxy.months, proxy.days))
}
