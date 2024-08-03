package net.corda.serialization.internal.amqp

import org.apache.qpid.proton.codec.ReadableBuffer
import org.apache.qpid.proton.codec.WritableBuffer
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class OutputStreamWritableBuffer(stream: OutputStream) : WritableBuffer {
    private val dataStream = DataOutputStream(stream)
    override fun put(b: Byte) {
        dataStream.writeByte(b.toInt())
    }

    override fun put(src: ByteArray, offset: Int, length: Int) {
        dataStream.write(src, offset, length)
    }

    override fun put(payload: ByteBuffer) {
        throw UnsupportedOperationException()
    }

    override fun put(payload: ReadableBuffer?) {
        throw UnsupportedOperationException()
    }

    override fun putFloat(f: Float) {
        dataStream.writeFloat(f)
    }

    override fun putDouble(d: Double) {
        dataStream.writeDouble(d)
    }

    override fun putShort(s: Short) {
        dataStream.writeShort(s.toInt())
    }

    override fun putInt(i: Int) {
        dataStream.writeInt(i)
    }

    override fun putLong(l: Long) {
        dataStream.writeLong(l)
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
