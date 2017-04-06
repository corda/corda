package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class ListSerializer(val declaredType: ParameterizedType) : Serializer() {
    override val type: Type = declaredType as? DeserializedParameterizedType ?: DeserializedParameterizedType.make(declaredType.toString())
    private val typeName = declaredType.toString()
    override val typeDescriptor = "${hashType(type)}"

    private val typeNotation: TypeNotation = RestrictedType(typeName, null, emptyList(), "list", Descriptor(typeDescriptor, null), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(declaredType.actualTypeArguments[0])
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
        for (entry in obj as List<*>) {
            output.writeObjectOrNull(entry, data, declaredType.actualTypeArguments[0])
        }
        data.exit() // exit list
        data.exit() // exit described
    }

    override fun readObject(obj: Any, envelope: Envelope, input: DeserializationInput): Any {
        // TODO: Can we verify the entries in the list?
        return (obj as List<*>).map { input.readObjectOrNull(it, envelope, declaredType.actualTypeArguments[0]) }
    }
}