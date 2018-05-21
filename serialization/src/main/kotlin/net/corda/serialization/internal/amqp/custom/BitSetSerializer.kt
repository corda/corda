/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.util.*

/**
 * A serializer that writes out a [BitSet] as an integer number of bits, plus the necessary number of bytes to encode that
 * many bits.
 */
class BitSetSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<BitSet, BitSetSerializer.BitSetProxy>(BitSet::class.java, BitSetProxy::class.java, factory) {
    override fun toProxy(obj: BitSet): BitSetProxy = BitSetProxy(obj.toByteArray())

    override fun fromProxy(proxy: BitSetProxy): BitSet = BitSet.valueOf(proxy.bytes)

    data class BitSetProxy(val bytes: ByteArray)
}