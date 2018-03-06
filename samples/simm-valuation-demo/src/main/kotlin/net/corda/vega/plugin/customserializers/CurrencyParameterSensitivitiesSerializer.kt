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

import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import com.opengamma.strata.market.param.CurrencyParameterSensitivity
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("UNUSED")
class CurrencyParameterSensitivitiesSerializer :
        SerializationCustomSerializer<CurrencyParameterSensitivities, CurrencyParameterSensitivitiesSerializer.Proxy> {
    data class Proxy(val sensitivities: List<CurrencyParameterSensitivity>)

    override fun fromProxy(proxy: Proxy) = CurrencyParameterSensitivities.of(proxy.sensitivities)
    override fun toProxy(obj: CurrencyParameterSensitivities) = Proxy(obj.sensitivities)
}