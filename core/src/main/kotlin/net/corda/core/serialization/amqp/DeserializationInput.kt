package net.corda.core.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.nio.ByteBuffer
import java.util.*


class DeserializationInput {
    // TODO: we're not supporting object refs yet
    private val objectHistory: MutableList<Any> = ArrayList()

    // TODO: we wouldn't create this fresh each time for performance.
    private val serializerFactory: SerializerFactory = SerializerFactory()

    fun <T : Any> deserialize(bytes: SerializedBytes<T>): T {
        // TODO: catch non serialization exceptions and wrap them up.
        try {
            // Check that the lead bytes match expected header
            if (!subArraysEqual(bytes.bytes, 0, 8, AmqpHeaderV1_0.bytes, 0)) {
                throw NotSerializableException("Serialization header does not match.")
            }
            val data = Data.Factory.create()
            val size = data.decode(ByteBuffer.wrap(bytes.bytes, 8, bytes.size - 8))
            if (size.toInt() != bytes.size - 8) {
                throw NotSerializableException("Unexpected size of data")
            }
            val envelope = Envelope.get(data)
            println(envelope)
            // Now pick out the obj and schema from the envelope.
            TODO()

        } finally {
            objectHistory.clear()
        }
    }

    private fun subArraysEqual(a: ByteArray, aOffset: Int, length: Int, b: ByteArray, bOffset: Int): Boolean {
        if (aOffset + length > a.size || bOffset + length > b.size) throw IndexOutOfBoundsException()
        var bytesRemaining = length
        var aPos = aOffset
        var bPos = bOffset
        while (bytesRemaining-- > 0) {
            if (a[aPos++] != b[bPos++]) return false
        }
        return true
    }
}