package net.corda.nodeapi.internal.serialization

import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import java.io.OutputStream
import java.nio.ByteBuffer

class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    private val bufferView = slice()
    fun consume(data: ByteSequence): ByteBuffer? {
        return if (data.slice(end = size) == bufferView) data.slice(size) else null
    }
}

enum class Instruction {
    /** Serialization data follows, and then discard the rest of the stream (if any) as legacy data may have trailing garbage. */
    DATA_AND_STOP,
    /** Identical behaviour to [DATA_AND_STOP], historically used by kryo. */
    ALT_DATA_AND_STOP;

    init {
        check(ordinal < 128) { "Consider implementing a varint encoding." }
    }

    fun writeTo(stream: OutputStream) = stream.write(ordinal)
    fun putTo(buffer: ByteBuffer) = buffer.put(ordinal.toByte())!!
}
