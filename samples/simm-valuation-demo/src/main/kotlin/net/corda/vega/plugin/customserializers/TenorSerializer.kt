/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.date.Tenor
import net.corda.core.serialization.*
import java.time.Period

@Suppress("UNUSED")
class TenorSerializer : SerializationCustomSerializer<Tenor, TenorSerializer.Proxy> {
    data class Proxy(val years: Int, val months: Int, val days: Int, val name: String)

    override fun toProxy(obj: Tenor) = Proxy(obj.period.years, obj.period.months, obj.period.days, obj.toString())
    override fun fromProxy(proxy: Proxy) = Tenor.of (Period.of(proxy.years, proxy.months, proxy.days))
}
