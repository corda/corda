package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class MapSerializer(val declaredType: ParameterizedType) : Serializer() {
    override val type: Type = if (declaredType is DeserializedParameterizedType) declaredType else DeserializedParameterizedType(declaredType.toString())
    private val typeName = declaredType.toString()
    override val typeDescriptor = declaredType.toString()

    private val typeNotation: TypeNotation = RestrictedType(typeName, null, emptyList(), "map", Descriptor(typeDescriptor, null), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        output.writeTypeNotations(typeNotation)
        output.requireSerializer(declaredType.actualTypeArguments[0])
        output.requireSerializer(declaredType.actualTypeArguments[1])
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // Write described
        data.putDescribed()
        data.enter()
        // Write descriptor
        data.putObject(typeNotation.descriptor.name)
        // Write map
        data.putMap()
        data.enter()
        for (entry in obj as Map<*, *>) {
            output.writeObjectOrNull(entry.key, data, declaredType.actualTypeArguments[0])
            output.writeObjectOrNull(entry.value, data, declaredType.actualTypeArguments[1])
        }
        data.exit() // exit map
        data.exit() // exit described
    }

    override fun readObject(obj: Any, envelope: Envelope, input: DeserializationInput): Any {
        // Can we verify the entries in the map?
        return (obj as Map<*, *>).map { input.readObjectOrNull(it.key, envelope, declaredType.actualTypeArguments[0]) to input.readObjectOrNull(it.value, envelope, declaredType.actualTypeArguments[1]) }
    }
}