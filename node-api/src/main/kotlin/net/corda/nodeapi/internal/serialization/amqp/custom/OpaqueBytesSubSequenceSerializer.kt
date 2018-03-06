/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.OpaqueBytesSubSequence
import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory

/**
 * A serializer for [OpaqueBytesSubSequence] that uses a proxy object to write out only content included into sequence
 * to save on network bandwidth
 * Uses [OpaqueBytes] as a proxy
 */
class OpaqueBytesSubSequenceSerializer(factory: SerializerFactory) :
        CustomSerializer.Proxy<OpaqueBytesSubSequence, OpaqueBytes>(OpaqueBytesSubSequence::class.java, OpaqueBytes::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = emptyList()
    override fun toProxy(obj: OpaqueBytesSubSequence): OpaqueBytes = OpaqueBytes(obj.copyBytes())
    override fun fromProxy(proxy: OpaqueBytes): OpaqueBytesSubSequence = OpaqueBytesSubSequence(proxy.bytes, proxy.offset, proxy.size)
}