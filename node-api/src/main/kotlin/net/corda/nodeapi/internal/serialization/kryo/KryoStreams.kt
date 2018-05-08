/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.internal.LazyPool
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer

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

private val serializationBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) })
internal val serializeOutputStreamPool = LazyPool(
        clear = ByteBufferOutputStream::reset,
        shouldReturnToPool = { it.size() < 256 * 1024 }, // Discard if it grew too large
        newInstance = { ByteBufferOutputStream(64 * 1024) })

internal fun <T> kryoInput(underlying: InputStream, task: Input.() -> T): T {
    return serializationBufferPool.run {
        Input(it).use { input ->
            input.inputStream = underlying
            input.task()
        }
    }
}

internal fun <T> kryoOutput(task: Output.() -> T): ByteArray {
    return byteArrayOutput { underlying ->
        serializationBufferPool.run {
            Output(it).use { output ->
                output.outputStream = underlying
                output.task()
            }
        }
    }
}

internal fun <T> byteArrayOutput(task: (ByteBufferOutputStream) -> T): ByteArray {
    return serializeOutputStreamPool.run { underlying ->
        task(underlying)
        underlying.toByteArray() // Must happen after close, to allow ZIP footer to be written for example.
    }
}

internal fun Output.substitute(transform: (OutputStream) -> OutputStream) {
    flush()
    outputStream = transform(outputStream)
}

internal fun Input.substitute(transform: (InputStream) -> InputStream) {
    inputStream = transform(SequenceInputStream(buffer.copyOfRange(position(), limit()).inputStream(), inputStream))
}
