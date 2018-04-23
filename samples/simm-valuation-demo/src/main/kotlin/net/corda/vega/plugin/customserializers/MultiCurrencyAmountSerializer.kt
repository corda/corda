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

import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.*

@Suppress("UNUSED")
class MultiCurrencyAmountSerializer :
        SerializationCustomSerializer<MultiCurrencyAmount, MultiCurrencyAmountSerializer.Proxy> {
    data class Proxy(val curencies : Map<Currency, Double>)

    override fun toProxy(obj: MultiCurrencyAmount) = Proxy(obj.toMap())
    override fun fromProxy(proxy: Proxy) = MultiCurrencyAmount.of(proxy.curencies)
}



