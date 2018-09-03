package net.corda.serialization.internal.amqp.custom

import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.OpaqueBytesSubSequence
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory

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