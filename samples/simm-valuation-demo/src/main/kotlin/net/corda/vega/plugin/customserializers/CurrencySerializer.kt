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

import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("UNUSED")
class CurrencySerializer : SerializationCustomSerializer<Currency, CurrencySerializer.Proxy> {
    data class Proxy(val currency: String)

    override fun fromProxy(proxy: Proxy): Currency = Currency.parse(proxy.currency)
    override fun toProxy(obj: Currency) = Proxy(obj.toString())
}
