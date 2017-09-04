package net.corda.nodeapi.internal.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.Map
import kotlin.collections.iterator
import kotlin.collections.map

/**
 * Serialization / deserialization of certain supported [Map] types.
 */
class MapSerializer(private val declaredType: ParameterizedType, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType as? DeserializedParameterizedType ?: DeserializedParameterizedType.make(declaredType.toString())
    override val typeDescriptor = "$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}"

    companion object {
        private val supportedTypes: Map<Class<out Any?>, (Map<*, *>) -> Map<*, *>> = mapOf(
                // Interfaces
                Map::class.java to { map -> Collections.unmodifiableMap(map) },
                SortedMap::class.java to { map -> Collections.unmodifiableSortedMap(TreeMap(map)) },
                NavigableMap::class.java to { map -> Collections.unmodifiableNavigableMap(TreeMap(map)) },
                // concrete classes for user convenience
                LinkedHashMap::class.java to { map -> LinkedHashMap(map) },
                TreeMap::class.java to { map -> TreeMap(map) }
        )
        private fun findConcreteType(clazz: Class<*>): (Map<*, *>) -> Map<*, *> {
            return supportedTypes[clazz] ?: throw NotSerializableException("Unsupported map type $clazz.")
        }
    }

    private val concreteBuilder: (Map<*, *>) -> Map<*, *> = findConcreteType(declaredType.rawType as Class<*>)

    private val typeNotation: TypeNotation = RestrictedType(SerializerFactory.nameForType(declaredType), null, emptyList(), "map", Descriptor(typeDescriptor, null), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(declaredType.actualTypeArguments[0])
            output.requireSerializer(declaredType.actualTypeArguments[1])
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        obj.javaClass.checkNotUnsupportedHashMap()
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            // Write map
            data.putMap()
            data.enter()
            for ((key, value) in obj as Map<*, *>) {
                output.writeObjectOrNull(key, data, declaredType.actualTypeArguments[0])
                output.writeObjectOrNull(value, data, declaredType.actualTypeArguments[1])
            }
            data.exit() // exit map
        }
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        // TODO: General generics question. Do we need to validate that entries in Maps and Collections match the generic type?  Is it a security hole?
        val entries: Iterable<Pair<Any?, Any?>> = (obj as Map<*, *>).map { readEntry(schema, input, it) }
        return concreteBuilder(entries.toMap())
    }

    private fun readEntry(schema: Schema, input: DeserializationInput, entry: Map.Entry<Any?, Any?>) =
            input.readObjectOrNull(entry.key, schema, declaredType.actualTypeArguments[0]) to
                    input.readObjectOrNull(entry.value, schema, declaredType.actualTypeArguments[1])
}

internal fun Class<*>.checkNotUnsupportedHashMap() {
    if (HashMap::class.java.isAssignableFrom(this) && !LinkedHashMap::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException(
                "Map type $this is unstable under iteration. Suggested fix: use java.util.LinkedHashMap instead.")
    }
    else if (WeakHashMap::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException ("Weak references with map types not supported. Suggested fix: "
                                        + "use java.util.LinkedHashMap instead.")
    }
    else if (Dictionary::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException (
                "Unable to serialise deprecated type $this. Suggested fix: prefer java.util.map implementations")
    }
}