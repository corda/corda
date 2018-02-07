package net.corda.nodeapi.internal.serialization

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class Ord(private val ordinal: Int) {
    interface Bytecode {
        val ord: Ord
        fun writeTo(stream: OutputStream) = stream.write(ord.ordinal)
        fun putTo(buffer: ByteBuffer) = buffer.put(ord.ordinal.toByte())!!
    }

    init {
        require(ordinal >= 0) { "The ordinal must be non-negative." }
        require(ordinal < 128) { "Consider implementing a varint encoding." }
    }
}

class Vals<out E>(private val values: Array<E>) {
    fun readFrom(stream: InputStream) = values[stream.read()]
}
