package net.corda.nodeapi.internal.serialization

import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import java.nio.ByteBuffer

class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    private val bufferView = slice()
    fun consume(data: ByteSequence): ByteBuffer? {
        return if (data.slice(end = size) == bufferView) data.slice(size) else null
    }
}
