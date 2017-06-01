package net.corda.core.serialization.amqp

import com.google.common.base.Throwables
import net.corda.core.serialization.SerializedBytes
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.util.*

/**
 * Main entry point for deserializing an AMQP encoded object.
 *
 * @param serializerFactory This is the factory for [AMQPSerializer] instances and can be shared across multiple
 * instances and threads.
 */
class DeserializationInput(internal val serializerFactory: SerializerFactory = SerializerFactory()) {
    // TODO: we're not supporting object refs yet
    private val objectHistory: MutableList<Any> = ArrayList()

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> deserialize(bytes: SerializedBytes<T>): T = deserialize(bytes, T::class.java)

    /**
     * This is the main entry point for deserialization of AMQP payloads, and expects a byte sequence involving a header
     * indicating what version of Corda serialization was used, followed by an [Envelope] which carries the object to
     * be deserialized and a schema describing the types of the objects.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: SerializedBytes<T>, clazz: Class<T>): T {
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
            // Now pick out the obj and schema from the envelope.
            return clazz.cast(readObjectOrNull(envelope.obj, envelope.schema, clazz))
        } catch(nse: NotSerializableException) {
            throw nse
        } catch(t: Throwable) {
            throw NotSerializableException("Unexpected throwable: ${t.message} ${Throwables.getStackTraceAsString(t)}")
        } finally {
            objectHistory.clear()
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
                throw NotSerializableException("Described type with descriptor ${obj.descriptor} was expected to be of type $type")
            return serializer.readObject(obj.described, schema, this)
        } else {
            return obj
        }
    }

    private fun Type.isSubClassOf(type: Type): Boolean {
        return type == Object::class.java ||
                (this is Class<*> && type is Class<*> && type.isAssignableFrom(this)) ||
                (this is DeserializedParameterizedType && type is Class<*> && this.rawType == type && this.isFullyWildcarded)
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