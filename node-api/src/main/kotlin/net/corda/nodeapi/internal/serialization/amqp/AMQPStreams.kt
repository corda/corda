package net.corda.nodeapi.internal.serialization.amqp

import com.esotericsoftware.kryo.io.ByteBufferInputStream
import net.corda.nodeapi.internal.serialization.kryo.ByteBufferOutputStream
import net.corda.nodeapi.internal.serialization.kryo.serializeOutputStreamPool
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

fun InputStream.asByteBuffer(): ByteBuffer {
    return if (this is ByteBufferInputStream) {
        byteBuffer // BBIS has no other state, so this is perfectly safe.
    } else {
        ByteBuffer.wrap(serializeOutputStreamPool.run {
            copyTo(it)
            it.toByteArray()
        })
    }
}

fun <T> OutputStream.asByteBuffer(remaining: Int, task: (ByteBuffer) -> T): T {
    return if (this is ByteBufferOutputStream) {
        asByteBuffer(remaining, task)
    } else {
        serializeOutputStreamPool.run {
            val result = it.asByteBuffer(remaining, task)
            it.copyTo(this)
            result
        }
    }
}
