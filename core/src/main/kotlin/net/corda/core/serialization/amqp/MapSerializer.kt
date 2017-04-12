package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.LinkedHashMap

class MapSerializer(val declaredType: ParameterizedType) : Serializer() {
    override val type: Type = declaredType as? DeserializedParameterizedType ?: DeserializedParameterizedType.make(declaredType.toString())
    private val typeName = declaredType.toString()
    override val typeDescriptor = "${hashType(type)}"

    companion object {
        private val supportedTypes: Map<Class<out Map<*, *>>, (Map<*, *>) -> Map<*, *>> = mapOf(
                Map::class.java to { map -> map },
                SortedMap::class.java to { map -> Collections.unmodifiableSortedMap(TreeMap(map)) },
                NavigableMap::class.java to { map -> Collections.unmodifiableNavigableMap(TreeMap(map)) }
        )
    }

    private val concreteBuilder: (Map<*, *>) -> Map<*, *> = findConcreteType(declaredType.rawType as Class<*>)

    private fun findConcreteType(clazz: Class<*>): (Map<*, *>) -> Map<*, *> {
        return supportedTypes[clazz] ?: throw NotSerializableException("Unsupported map type $clazz.")
    }

    private val typeNotation: TypeNotation = RestrictedType(typeName, null, emptyList(), "map", Descriptor(typeDescriptor, null), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(declaredType.actualTypeArguments[0])
            output.requireSerializer(declaredType.actualTypeArguments[1])
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        if (HashMap::class.java.isAssignableFrom(obj.javaClass) && !LinkedHashMap::class.java.isAssignableFrom(obj.javaClass)) {
            throw NotSerializableException("Map type ${obj.javaClass} is unstable under iteration.")
        }
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
        return concreteBuilder((obj as Map<*, *>).map { input.readObjectOrNull(it.key, envelope, declaredType.actualTypeArguments[0]) to input.readObjectOrNull(it.value, envelope, declaredType.actualTypeArguments[1]) }.toMap())
    }
}