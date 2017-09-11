package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
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