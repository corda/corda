@file:JvmName("KryoStreams")

package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.internal.LazyPool
import net.corda.serialization.internal.byteArrayOutput
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream

private val serializationBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) })

fun <T> kryoInput(underlying: InputStream, task: Input.() -> T): T {
    return serializationBufferPool.run {
        Input(it).use { input ->
            input.inputStream = underlying
            input.task()
        }
    }
}

fun <T> kryoOutput(task: Output.() -> T): ByteArray {
    return byteArrayOutput { underlying ->
        serializationBufferPool.run {
            Output(it).use { output ->
                output.outputStream = underlying
                output.task()
            }
        }
    }
}

fun Output.substitute(transform: (OutputStream) -> OutputStream) {
    flush()
    outputStream = transform(outputStream)
}

fun Input.substitute(transform: (InputStream) -> InputStream) {
    inputStream = transform(SequenceInputStream(buffer.copyOfRange(position(), limit()).inputStream(), inputStream))
}
