package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.internal.LazyPool
import java.io.*

private val serializationBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) })
private val serializeOutputStreamPool = LazyPool(
        clear = ByteArrayOutputStream::reset,
        shouldReturnToPool = { it.size() < 256 * 1024 }, // Discard if it grew too large
        newInstance = { ByteArrayOutputStream(64 * 1024) })

internal fun <T> kryoInput(underlying: InputStream, task: Input.() -> T): T {
    return serializationBufferPool.run {
        Input(it).use { input ->
            input.inputStream = underlying
            input.task()
        }
    }
}

internal fun <T> kryoOutput(task: Output.() -> T): ByteArray {
    return serializeOutputStreamPool.run { underlying ->
        serializationBufferPool.run {
            Output(it).use { output ->
                output.outputStream = underlying
                output.task()
            }
        }
        underlying.toByteArray() // Must happen after close, to allow ZIP footer to be written for example.
    }
}

internal fun Output.substitute(transform: (OutputStream) -> OutputStream) {
    flush()
    outputStream = transform(outputStream)
}

internal fun Input.substitute(transform: (InputStream) -> InputStream) {
    inputStream = transform(SequenceInputStream(ByteArrayInputStream(buffer.copyOfRange(position(), limit())), inputStream))
}
