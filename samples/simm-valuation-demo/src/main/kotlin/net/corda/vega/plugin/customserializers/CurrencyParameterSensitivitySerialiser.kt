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

import com.opengamma.strata.market.param.CurrencyParameterSensitivity
import com.opengamma.strata.market.param.ParameterMetadata
import com.opengamma.strata.data.MarketDataName
import com.opengamma.strata.collect.array.DoubleArray
import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("UNUSED")
class CurrencyParameterSensitivitySerializer :
        SerializationCustomSerializer<CurrencyParameterSensitivity, CurrencyParameterSensitivitySerializer.Proxy> {
    data class Proxy(val currency: Currency, val marketDataName: MarketDataName<*>,
                     val parameterMetadata: List<ParameterMetadata>,
                     val sensitivity: DoubleArray)

    override fun fromProxy(proxy: CurrencyParameterSensitivitySerializer.Proxy): CurrencyParameterSensitivity =
            CurrencyParameterSensitivity.of(
                    proxy.marketDataName,
                    proxy.parameterMetadata,
                    proxy.currency,
                    proxy.sensitivity)

    override fun toProxy(obj: CurrencyParameterSensitivity) = Proxy(obj.currency, obj.marketDataName, obj.parameterMetadata, obj.sensitivity)
}
