package net.corda.nodeapi.internal.serialization

import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes

class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    fun consume(data: ByteSequence): ByteSequence? {
        return if (size <= data.size && this == data.take(size)) data.skip(size) else null
    }
}
