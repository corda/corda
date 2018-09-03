package net.corda.nodeapi.internal.protonwrapper.engine

import io.netty.buffer.ByteBuf
import org.apache.qpid.proton.codec.ReadableBuffer
import org.apache.qpid.proton.codec.WritableBuffer
import java.nio.ByteBuffer

/**
 *  NettyWritable is a utility class allow proton-j encoders to write directly into a
 *  netty ByteBuf, without any need to materialize a ByteArray copy.
 */
internal class NettyWritable(val nettyBuffer: ByteBuf) : WritableBuffer {
    override fun put(b: Byte) {
        nettyBuffer.writeByte(b.toInt())
    }

    override fun putFloat(f: Float) {
        nettyBuffer.writeFloat(f)
    }

    override fun putDouble(d: Double) {
        nettyBuffer.writeDouble(d)
    }

    override fun put(src: ByteArray, offset: Int, length: Int) {
        nettyBuffer.writeBytes(src, offset, length)
    }

    override fun putShort(s: Short) {
        nettyBuffer.writeShort(s.toInt())
    }

    override fun putInt(i: Int) {
        nettyBuffer.writeInt(i)
    }

    override fun putLong(l: Long) {
        nettyBuffer.writeLong(l)
    }

    override fun hasRemaining(): Boolean {
        return nettyBuffer.writerIndex() < nettyBuffer.capacity()
    }

    override fun remaining(): Int {
        return nettyBuffer.capacity() - nettyBuffer.writerIndex()
    }

    override fun position(): Int {
        return nettyBuffer.writerIndex()
    }

    override fun position(position: Int) {
        nettyBuffer.writerIndex(position)
    }

    override fun put(payload: ByteBuffer) {
        nettyBuffer.writeBytes(payload)
    }

    override fun put(payload: ReadableBuffer) {
        nettyBuffer.writeBytes(payload.byteBuffer())
    }

    override fun limit(): Int {
        return nettyBuffer.capacity()
    }
}