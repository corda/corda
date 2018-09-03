package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.date.Tenor
import net.corda.core.serialization.*
import java.time.Period

@Suppress("UNUSED")
class TenorSerializer : SerializationCustomSerializer<Tenor, TenorSerializer.Proxy> {
    data class Proxy(val years: Int, val months: Int, val days: Int, val name: String)

    override fun toProxy(obj: Tenor) = Proxy(obj.period.years, obj.period.months, obj.period.days, obj.toString())
    override fun fromProxy(proxy: Proxy): Tenor = Tenor.of(Period.of(proxy.years, proxy.months, proxy.days))
}
