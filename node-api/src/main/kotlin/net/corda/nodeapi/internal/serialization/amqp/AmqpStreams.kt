package net.corda.nodeapi.internal.serialization.amqp

import com.esotericsoftware.kryo.io.ByteBufferInputStream
import net.corda.nodeapi.internal.serialization.kryo.ByteBufferOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

// TODO: Make ByteBuffer subclasses instead of new arrays, it must be possible as Mockito can do it.

fun InputStream.asByteBuffer(): ByteBuffer {
    if (this is ByteBufferInputStream) {
        return byteBuffer
    } else {
        return ByteBuffer.wrap(readBytes())
    }
}

fun <T> OutputStream.asByteBuffer(remaining: Int, task: (ByteBuffer) -> T): T {
    return if (this is ByteBufferOutputStream) {
        asByteBuffer(remaining, task)
    } else {
        val bytes = ByteArray(remaining)
        val buffer = ByteBuffer.wrap(bytes)
        val result = task(buffer)
        write(bytes, 0, buffer.position())
        result
    }
}
