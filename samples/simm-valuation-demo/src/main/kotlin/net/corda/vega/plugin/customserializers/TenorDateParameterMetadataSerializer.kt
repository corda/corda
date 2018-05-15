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
import com.opengamma.strata.market.param.TenorDateParameterMetadata
import net.corda.core.serialization.*
import java.time.LocalDate

@Suppress("UNUSED")
class TenorDateParameterMetadataSerializer :
        SerializationCustomSerializer<TenorDateParameterMetadata, TenorDateParameterMetadataSerializer.Proxy> {
    data class Proxy(val tenor: Tenor, val date: LocalDate, val identifier: Tenor, val label: String)

    override fun toProxy(obj: TenorDateParameterMetadata) = Proxy(obj.tenor, obj.date, obj.identifier, obj.label)
    override fun fromProxy(proxy: Proxy): TenorDateParameterMetadata = TenorDateParameterMetadata.of(proxy.date, proxy.tenor, proxy.label)
}
