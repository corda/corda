package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class ArraySerializer(override val type: Type) : Serializer() {
    private val typeName = type.typeName

    override val typeDescriptor = "${hashType(type)}"

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
        data.putDescribed()
        data.enter()
        // Write descriptor
        data.putObject(typeNotation.descriptor.name)
        // Write list
        data.putList()
        data.enter()
        for (entry in obj as Array<*>) {
            output.writeObjectOrNull(entry, data, elementType)
        }
        data.exit() // exit list
        data.exit() // exit described
    }

    override fun readObject(obj: Any, envelope: Envelope, input: DeserializationInput): Any {
        return (obj as List<*>).map { input.readObjectOrNull(it, envelope, elementType) }.toArrayOfType(elementType)
    }

    private fun <T> List<T>.toArrayOfType(type: Type): Any {
        return if (type is Class<*>) {
            return java.lang.reflect.Array.newInstance(type, this.size).apply {
                for (i in 0..lastIndex) {
                    java.lang.reflect.Array.set(this@apply, i, this@toArrayOfType.get(i))
                }
            }
        } else if (type is ParameterizedType) {
            return java.lang.reflect.Array.newInstance(type.rawType as Class<*>, this.size).apply {
                for (i in 0..lastIndex) {
                    java.lang.reflect.Array.set(this@apply, i, this@toArrayOfType.get(i))
                }
            }
        } else {
            throw NotSerializableException("Unexpected array element type $type")
        }
    }
}