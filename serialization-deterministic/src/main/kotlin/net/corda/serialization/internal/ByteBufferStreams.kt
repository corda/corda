@file:JvmName("ByteBufferStreams")
package net.corda.serialization.internal

const val DEFAULT_BYTEBUFFER_SIZE = 64 * 1024

/**
 * Drop-in replacement for [byteArrayOutput] in the serialization module.
 * This version does not use a [net.corda.core.internal.LazyPool].
 */
internal fun <T> byteArrayOutput(task: (ByteBufferOutputStream) -> T): ByteArray {
    return ByteBufferOutputStream(DEFAULT_BYTEBUFFER_SIZE).let { underlying ->
        task(underlying)
        underlying.toByteArray() // Must happen after close, to allow ZIP footer to be written for example.
    }
}
