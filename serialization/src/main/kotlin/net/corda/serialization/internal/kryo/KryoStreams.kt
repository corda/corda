/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("KryoStreams")

package net.corda.serialization.internal.kryo

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.internal.LazyPool
import net.corda.serialization.internal.byteArrayOutput
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream

private val serializationBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) })

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

internal fun Output.substitute(transform: (OutputStream) -> OutputStream) {
    flush()
    outputStream = transform(outputStream)
}

internal fun Input.substitute(transform: (InputStream) -> InputStream) {
    inputStream = transform(SequenceInputStream(buffer.copyOfRange(position(), limit()).inputStream(), inputStream))
}
