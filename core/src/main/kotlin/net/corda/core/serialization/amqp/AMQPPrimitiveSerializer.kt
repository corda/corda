package net.corda.core.serialization.amqp

import com.google.common.primitives.Primitives
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Serializer / deserializer for native AMQP types (Int, Float, String etc).
 */
class AMQPPrimitiveSerializer(clazz: Class<*>) : AMQPSerializer {
    override val typeDescriptor: String = SerializerFactory.primitiveTypeName(Primitives.wrap(clazz))!!
    override val type: Type = clazz

    // NOOP since this is a primitive type.
    override fun writeClassInfo(output: SerializationOutput) {
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        data.putObject(obj)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any = obj
}