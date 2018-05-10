@file:JvmName("ByteBufferStreams")
package net.corda.nodeapi.internal.serialization

import net.corda.core.internal.LazyPool
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.min

internal val serializeOutputStreamPool = LazyPool(
    clear = ByteBufferOutputStream::reset,
    shouldReturnToPool = { it.size() < 256 * 1024 }, // Discard if it grew too large
    newInstance = { ByteBufferOutputStream(64 * 1024) })

internal fun <T> byteArrayOutput(task: (ByteBufferOutputStream) -> T): ByteArray {
    return serializeOutputStreamPool.run { underlying ->
        task(underlying)
        underlying.toByteArray() // Must happen after close, to allow ZIP footer to be written for example.
    }
}

class ByteBufferInputStream(val byteBuffer: ByteBuffer) : InputStream() {
    @Throws(IOException::class)
    override fun read(): Int {
        return if (byteBuffer.hasRemaining()) byteBuffer.get().toInt() else -1
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || length > b.size - offset) {
            throw IndexOutOfBoundsException()
        } else if (length == 0) {
            return 0
        } else if (!byteBuffer.hasRemaining()) {
            return -1
        }
        val size = min(length, byteBuffer.remaining())
        byteBuffer.get(b, offset, size)
        return size
    }
}

class ByteBufferOutputStream(size: Int) : ByteArrayOutputStream(size) {
    companion object {
        private val ensureCapacity = ByteArrayOutputStream::class.java.getDeclaredMethod("ensureCapacity", Int::class.java).apply {
            isAccessible = true
        }
    }

    fun <T> alsoAsByteBuffer(remaining: Int, task: (ByteBuffer) -> T): T {
        ensureCapacity.invoke(this, count + remaining)
        val buffer = ByteBuffer.wrap(buf, count, remaining)
        val result = task(buffer)
        count = buffer.position()
        return result
    }

    fun copyTo(stream: OutputStream) {
        stream.write(buf, 0, count)
    }
}
