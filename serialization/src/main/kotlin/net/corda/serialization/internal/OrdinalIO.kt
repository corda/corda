package net.corda.serialization.internal

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class OrdinalBits(private val ordinal: Int) {
    interface OrdinalWriter {
        val bits: OrdinalBits
        @JvmDefault val encodedSize get() = 1
        @JvmDefault fun writeTo(stream: OutputStream) = stream.write(bits.ordinal)
        @JvmDefault fun putTo(buffer: ByteBuffer) = buffer.put(bits.ordinal.toByte())!!
    }

    init {
        require(ordinal >= 0) { "The ordinal must be non-negative." }
        require(ordinal < 128) { "Consider implementing a varint encoding." }
    }
}

class OrdinalReader<out E : Any>(private val values: Array<E>) {
    private val enumName = values[0].javaClass.simpleName
    private val range = 0 until values.size
    fun readFrom(stream: InputStream): E {
        val ordinal = stream.read()
        if (ordinal == -1) throw EOFException("Expected a $enumName ordinal.")
        if (ordinal !in range) throw NoSuchElementException("No $enumName with ordinal: $ordinal")
        return values[ordinal]
    }
}
