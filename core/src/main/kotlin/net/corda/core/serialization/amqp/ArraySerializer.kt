package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Serialization / deserialization of arrays.
 */
class ArraySerializer(override val type: Type) : AMQPSerializer<Any> {
    private val typeName = type.typeName

    override val typeDescriptor = "$DESCRIPTOR_DOMAIN:${fingerprintForType(type)}"

    private val elementType: Type = makeElementType()

    private val typeNotation: TypeNotation = RestrictedType(typeName, null, emptyList(), "list", Descriptor(typeDescriptor, null), emptyList())

    private fun makeElementType(): Type {
        return (type as? Class<*>)?.componentType ?: (type as GenericArrayType).genericComponentType
    }

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(elementType)
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            withList {
                for (entry in obj as Array<*>) {
                    output.writeObjectOrNull(entry, this, elementType)
                }
            }
        }
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        return (obj as List<*>).map { input.readObjectOrNull(it, schema, elementType) }.toArrayOfType(elementType)
    }

    private fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType: Class<*> = if (type is Class<*>) {
            type
        } else if (type is ParameterizedType) {
            type.rawType as Class<*>
        } else {
            throw NotSerializableException("Unexpected array element type $type")
        }
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            val array = this
            for (i in 0..lastIndex) {
                java.lang.reflect.Array.set(array, i, list[i])
            }
        }
    }
}