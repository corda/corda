package net.corda.serialization.internal.amqp

import org.apache.qpid.proton.codec.ReadableBuffer
import org.apache.qpid.proton.codec.WritableBuffer
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * This class is just a wrapper around an [OutputStream] for Proton-J Encoder.  Only the methods
 * we are actively using are implemented and tested.
 */
@Suppress("MagicNumber")
class OutputStreamWritableBuffer(private val stream: OutputStream) : WritableBuffer {
    private val writeBuffer = ByteArray(8)

    override fun put(b: Byte) {
        stream.write(b.toInt())
    }

    override fun put(src: ByteArray, offset: Int, length: Int) {
        stream.write(src, offset, length)
    }

    override fun put(payload: ByteBuffer) {
        throw UnsupportedOperationException()
    }

    override fun put(payload: ReadableBuffer?) {
        throw UnsupportedOperationException()
    }

    override fun putFloat(f: Float) {
        throw UnsupportedOperationException()
    }

    override fun putDouble(d: Double) {
        throw UnsupportedOperationException()
    }

    override fun putShort(s: Short) {
        throw UnsupportedOperationException()
    }

    override fun putInt(i: Int) {
        writeBuffer[0] = (i ushr 24).toByte()
        writeBuffer[1] = (i ushr 16).toByte()
        writeBuffer[2] = (i ushr 8).toByte()
        writeBuffer[3] = (i ushr 0).toByte()
        put(writeBuffer, 0, 4)
    }

    override fun putLong(v: Long) {
        writeBuffer[0] = (v ushr 56).toByte()
        writeBuffer[1] = (v ushr 48).toByte()
        writeBuffer[2] = (v ushr 40).toByte()
        writeBuffer[3] = (v ushr 32).toByte()
        writeBuffer[4] = (v ushr 24).toByte()
        writeBuffer[5] = (v ushr 16).toByte()
        writeBuffer[6] = (v ushr 8).toByte()
        writeBuffer[7] = (v ushr 0).toByte()
        put(writeBuffer, 0, 8)
    }

    override fun hasRemaining(): Boolean {
        return true
    }

    override fun remaining(): Int {
        throw UnsupportedOperationException()
    }

    override fun position(): Int {
        throw UnsupportedOperationException()
    }

    override fun position(position: Int) {
        throw UnsupportedOperationException()
    }

    override fun limit(): Int {
        throw UnsupportedOperationException()
    }
}
