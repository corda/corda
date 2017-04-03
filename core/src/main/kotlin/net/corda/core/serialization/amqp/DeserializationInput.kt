package net.corda.core.serialization.amqp

import com.google.common.base.Throwables
import net.corda.core.serialization.SerializedBytes
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.util.*


class DeserializationInput {
    // TODO: we're not supporting object refs yet
    private val objectHistory: MutableList<Any> = ArrayList()

    // TODO: we wouldn't create this fresh each time for performance.
    private val serializerFactory: SerializerFactory = SerializerFactory()


    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> deserialize(bytes: SerializedBytes<T>): T = deserialize(bytes, T::class.java)

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
            println(envelope)
            // Now pick out the obj and schema from the envelope.
            return clazz.cast(readObjectOrNull(envelope.obj, envelope, clazz))
        } catch(nse: NotSerializableException) {
            throw nse
        } catch(t: Throwable) {
            throw NotSerializableException("Unexpected throwable: ${t.message} ${Throwables.getStackTraceAsString(t)}")
        } finally {
            objectHistory.clear()
        }
    }

    internal fun readObjectOrNull(obj: Any?, envelope: Envelope, type: Type): Any? {
        if (obj == null) {
            return null
        } else {
            return readObject(obj, envelope, type)
        }
    }

    internal fun readObject(obj: Any, envelope: Envelope, type: Type): Any {
        if (obj is DescribedType) {
            // Look up serializer in factory by descriptor
            val serializer = serializerFactory.get(obj.descriptor, envelope)
            // The ordering of this type comparison is important.  See equals() implementation in [DeserializedParameterizedType].
            if (serializer.type != type && !serializer.type.isSubClassOf(type)) throw NotSerializableException("Described type with descriptor ${obj.descriptor} was expected to be of type $type")
            return serializer.readObject(obj.described, envelope, this)
        } else {
            return obj
        }
    }

    private fun Type.isSubClassOf(type: Type): Boolean {
        return this is Class<*> && type is Class<*> && type.isAssignableFrom(this)
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