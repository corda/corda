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

import net.corda.core.serialization.SerializationCustomSerializer
import com.opengamma.strata.collect.array.DoubleArray

@Suppress("UNUSED")
class DoubleArraySerializer : SerializationCustomSerializer<DoubleArray, DoubleArraySerializer.Proxy> {
    data class Proxy(val amount: kotlin.DoubleArray)

    override fun fromProxy(proxy: Proxy) = DoubleArray.copyOf(proxy.amount)
    override fun toProxy(obj: DoubleArray) = Proxy(obj.toArray())
}
