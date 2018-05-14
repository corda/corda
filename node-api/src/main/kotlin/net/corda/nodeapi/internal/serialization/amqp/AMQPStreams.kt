@file:JvmName("AMQPStreams")

package net.corda.nodeapi.internal.serialization.amqp

import net.corda.nodeapi.internal.serialization.ByteBufferInputStream
import net.corda.nodeapi.internal.serialization.ByteBufferOutputStream
import net.corda.nodeapi.internal.serialization.serializeOutputStreamPool
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

fun <T> OutputStream.alsoAsByteBuffer(remaining: Int, task: (ByteBuffer) -> T): T {
    return if (this is ByteBufferOutputStream) {
        alsoAsByteBuffer(remaining, task)
    } else {
        serializeOutputStreamPool.run {
            val result = it.alsoAsByteBuffer(remaining, task)
            it.copyTo(this)
            result
        }
    }
}
