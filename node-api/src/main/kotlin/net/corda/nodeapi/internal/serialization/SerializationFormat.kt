package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializationEncoding
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.nodeapi.internal.serialization.Ord.Bytecode
import org.iq80.snappy.SnappyFramedInputStream
import org.iq80.snappy.SnappyFramedOutputStream
import java.io.OutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    private val bufferView = slice()
    fun consume(data: ByteSequence): ByteBuffer? {
        return if (data.slice(end = size) == bufferView) data.slice(size) else null
    }
}

enum class Instruction : Bytecode {
    /** Serialization data follows, and then discard the rest of the stream (if any) as legacy data may have trailing garbage. */
    DATA_AND_STOP,
    /** Identical behaviour to [DATA_AND_STOP], historically used by kryo. */
    ALT_DATA_AND_STOP,
    /** The ordinal of a [CordaSerializationEncoding] follows, which should be used to decode the remainder of the stream. */
    ENCODING;

    companion object {
        val vals = Vals(values())
    }

    override val ord = Ord(ordinal)
}

enum class CordaSerializationEncoding : SerializationEncoding, Bytecode {
    DEFLATE {
        override fun wrap(stream: OutputStream) = DeflaterOutputStream(stream)
        override fun wrap(stream: InputStream) = InflaterInputStream(stream)
    },
    SNAPPY {
        override fun wrap(stream: OutputStream) = SnappyFramedOutputStream(stream)
        override fun wrap(stream: InputStream) = SnappyFramedInputStream(stream, false)
    };

    companion object {
        val vals = Vals(values())
    }

    override val ord = Ord(ordinal)
    abstract fun wrap(stream: OutputStream): OutputStream
    abstract fun wrap(stream: InputStream): InputStream
}
