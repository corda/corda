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
import java.util.*

@Suppress("UNUSED")
class DoubleArraySerializer : SerializationCustomSerializer<DoubleArray, DoubleArraySerializer.Proxy> {
    data class Proxy(val amount: kotlin.DoubleArray) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Proxy

            if (!Arrays.equals(amount, other.amount)) return false

            return true
        }

        override fun hashCode(): Int {
            return Arrays.hashCode(amount)
        }
    }

    override fun fromProxy(proxy: Proxy): DoubleArray = DoubleArray.copyOf(proxy.amount)
    override fun toProxy(obj: DoubleArray) = Proxy(obj.toArray())
}
