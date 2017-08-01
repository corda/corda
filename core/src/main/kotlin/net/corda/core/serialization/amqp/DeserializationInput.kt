package net.corda.core.serialization.amqp

import net.corda.core.internal.getStackTraceAsString
import net.corda.core.serialization.SerializedBytes
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.UnsignedByte
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.util.*

data class objectAndEnvelope<T>(val obj: T, val envelope: Envelope)

/**
 * Main entry point for deserializing an AMQP encoded object.
 *
 * @param serializerFactory This is the factory for [AMQPSerializer] instances and can be shared across multiple
 * instances and threads.
 */
class DeserializationInput(internal val serializerFactory: SerializerFactory = SerializerFactory()) {
    // TODO: we're not supporting object refs yet
    private val objectHistory: MutableList<Any> = ArrayList()

    internal companion object {
        val BYTES_NEEDED_TO_PEEK: Int = 23

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

        fun peekSize(bytes: ByteArray): Int {
            // There's an 8 byte header, and then a 0 byte plus descriptor followed by constructor
            val eighth = bytes[8].toInt()
            check(eighth == 0x0) { "Expected to find a descriptor in the AMQP stream" }
            // We should always have an Envelope, so the descriptor should be a 64-bit long (0x80)
            val ninth = UnsignedByte.valueOf(bytes[9]).toInt()
            check(ninth == 0x80) { "Expected to find a ulong in the AMQP stream" }
            // Skip 8 bytes
            val eighteenth = UnsignedByte.valueOf(bytes[18]).toInt()
            check(eighteenth == 0xd0 || eighteenth == 0xc0) { "Expected to find a list8 or list32 in the AMQP stream" }
            val size = if (eighteenth == 0xc0) {
                // Next byte is size
                UnsignedByte.valueOf(bytes[19]).toInt() - 3 // Minus three as PEEK_SIZE assumes 4 byte unsigned integer.
            } else {
                // Next 4 bytes is size
                UnsignedByte.valueOf(bytes[19]).toInt().shl(24) + UnsignedByte.valueOf(bytes[20]).toInt().shl(16) + UnsignedByte.valueOf(bytes[21]).toInt().shl(8) + UnsignedByte.valueOf(bytes[22]).toInt()
            }
            return size + BYTES_NEEDED_TO_PEEK
        }
    }

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> deserialize(bytes: SerializedBytes<T>): T =
            deserialize(bytes, T::class.java)


    @Throws(NotSerializableException::class)
    inline internal fun <reified T : Any> deserializeAndReturnEnvelope(bytes: SerializedBytes<T>): objectAndEnvelope<T> =
            deserializeAndReturnEnvelope(bytes, T::class.java)


    @Throws(NotSerializableException::class)
    private fun <T : Any> getEnvelope(bytes: SerializedBytes<T>): Envelope {
        // Check that the lead bytes match expected header
        if (!subArraysEqual(bytes.bytes, 0, 8, AmqpHeaderV1_0.bytes, 0)) {
            throw NotSerializableException("Serialization header does not match.")
        }

        val data = Data.Factory.create()
        val size = data.decode(ByteBuffer.wrap(bytes.bytes, 8, bytes.size - 8))
        if (size.toInt() != bytes.size - 8) {
            throw NotSerializableException("Unexpected size of data")
        }

        return Envelope.get(data)
    }


    @Throws(NotSerializableException::class)
    private fun <T : Any, R> des(bytes: SerializedBytes<T>, clazz: Class<T>, generator: (SerializedBytes<T>, Class<T>) -> R): R {
        try {
            return generator(bytes, clazz)
        } catch(nse: NotSerializableException) {
            throw nse
        } catch(t: Throwable) {
            throw NotSerializableException("Unexpected throwable: ${t.message} ${t.getStackTraceAsString()}")
        } finally {
            objectHistory.clear()
        }
    }

    /**
     * This is the main entry point for deserialization of AMQP payloads, and expects a byte sequence involving a header
     * indicating what version of Corda serialization was used, followed by an [Envelope] which carries the object to
     * be deserialized and a schema describing the types of the objects.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: SerializedBytes<T>, clazz: Class<T>): T {
        return des<T, T>(bytes, clazz) { bytes, clazz ->
            var envelope = getEnvelope(bytes)
            clazz.cast(readObjectOrNull(envelope.obj, envelope.schema, clazz))
        }
    }

    @Throws(NotSerializableException::class)
    internal fun <T : Any> deserializeAndReturnEnvelope(bytes: SerializedBytes<T>, clazz: Class<T>): objectAndEnvelope<T> {
        return des<T, objectAndEnvelope<T>>(bytes, clazz) { bytes, clazz ->
            val envelope = getEnvelope(bytes)
            // Now pick out the obj and schema from the envelope.
            objectAndEnvelope(clazz.cast(readObjectOrNull(envelope.obj, envelope.schema, clazz)), envelope)
        }
    }

    internal fun readObjectOrNull(obj: Any?, schema: Schema, type: Type): Any? {
        if (obj == null) {
            return null
        } else {
            return readObject(obj, schema, type)
        }
    }

    internal fun readObject(obj: Any, schema: Schema, type: Type): Any {
        if (obj is DescribedType) {
            // Look up serializer in factory by descriptor
            val serializer = serializerFactory.get(obj.descriptor, schema)
            if (serializer.type != type && !serializer.type.isSubClassOf(type))
                throw NotSerializableException("Described type with descriptor ${obj.descriptor} was " +
                        "expected to be of type $type but was ${serializer.type}")
            return serializer.readObject(obj.described, schema, this)
        } else if (obj is Binary) {
            return obj.array
        } else {
            return obj
        }
    }
}
