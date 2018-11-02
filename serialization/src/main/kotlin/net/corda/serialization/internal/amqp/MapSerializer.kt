package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.StubOutForDJVM
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.LinkedHashMap

private typealias MapCreationFunction = (Map<*, *>) -> Map<*, *>

/**
 * Serialization / deserialization of certain supported [Map] types.
 */
@KeepForDJVM
class MapSerializer(private val declaredType: ParameterizedType, factory: LocalSerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType

    override val typeDescriptor: Symbol = factory.createDescriptor(type)

    companion object {
        // NB: Order matters in this map, the most specific classes should be listed at the end
        private val supportedTypes: Map<Class<out Map<*, *>>, MapCreationFunction> = Collections.unmodifiableMap(linkedMapOf(
                // Interfaces
                Map::class.java to { map -> Collections.unmodifiableMap(map) },
                SortedMap::class.java to { map -> Collections.unmodifiableSortedMap(TreeMap(map)) },
                NavigableMap::class.java to { map -> Collections.unmodifiableNavigableMap(TreeMap(map)) },
                // concrete classes for user convenience
                LinkedHashMap::class.java to { map -> LinkedHashMap(map) },
                TreeMap::class.java to { map -> TreeMap(map) },
                EnumMap::class.java to { map ->
                    EnumMap(uncheckedCast<Map<*, *>, Map<EnumJustUsedForCasting, Any>>(map))
                }
        ))

        private val supportedTypeIdentifiers = supportedTypes.keys.asSequence()
                .map { TypeIdentifier.forGenericType(it) }.toSet()

        private fun findConcreteType(clazz: Class<*>): MapCreationFunction {
            return supportedTypes[clazz] ?: throw AMQPNotSerializableException(clazz, "Unsupported map type $clazz.")
        }

        fun resolveDeclared(declaredTypeInformation: LocalTypeInformation.AMap): LocalTypeInformation.AMap {
            declaredTypeInformation.observedType.asClass().checkSupportedMapType()
            if (supportedTypeIdentifiers.contains(declaredTypeInformation.typeIdentifier.erased))
                return if (!declaredTypeInformation.isErased) declaredTypeInformation
                else declaredTypeInformation.withParameters(LocalTypeInformation.Unknown, LocalTypeInformation.Unknown)

            throw NotSerializableException("Cannot derive map type for declared type " +
                    declaredTypeInformation.prettyPrint(false))
        }

        fun resolveActual(actualClass: Class<*>, declaredTypeInformation: LocalTypeInformation.AMap): LocalTypeInformation.AMap {
            declaredTypeInformation.observedType.asClass().checkSupportedMapType()
            if (supportedTypeIdentifiers.contains(declaredTypeInformation.typeIdentifier.erased)) {
                return if (!declaredTypeInformation.isErased) declaredTypeInformation
                else declaredTypeInformation.withParameters(LocalTypeInformation.Unknown, LocalTypeInformation.Unknown)
            }

            val mapClass = findMostSuitableMapType(actualClass)
            val erasedInformation = LocalTypeInformation.AMap(
                    mapClass,
                    TypeIdentifier.forClass(mapClass),
                    LocalTypeInformation.Unknown, LocalTypeInformation.Unknown)

            return when(declaredTypeInformation.typeIdentifier) {
                is TypeIdentifier.Parameterised -> erasedInformation.withParameters(
                        declaredTypeInformation.keyType,
                        declaredTypeInformation.valueType)
                else -> erasedInformation.withParameters(LocalTypeInformation.Unknown, LocalTypeInformation.Unknown)
            }
        }

        private fun findMostSuitableMapType(actualClass: Class<*>): Class<out Map<*, *>> =
                MapSerializer.supportedTypes.keys.findLast { it.isAssignableFrom(actualClass) }!!
    }

    private val concreteBuilder: MapCreationFunction = findConcreteType(declaredType.rawType as Class<*>)

    private val typeNotation: TypeNotation = RestrictedType(SerializerFactory.nameForType(declaredType), null, emptyList(), "map", Descriptor(typeDescriptor), emptyList())

    private val inboundKeyType = declaredType.actualTypeArguments[0]
    private val outboundKeyType = resolveTypeVariables(inboundKeyType, null)
    private val inboundValueType = declaredType.actualTypeArguments[1]
    private val outboundValueType = resolveTypeVariables(inboundValueType, null)

    override fun writeClassInfo(output: SerializationOutput) = ifThrowsAppend({ declaredType.typeName }) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(outboundKeyType)
            output.requireSerializer(outboundValueType)
        }
    }

    override fun writeObject(
            obj: Any,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int
    ) = ifThrowsAppend({ declaredType.typeName }) {
        obj.javaClass.checkSupportedMapType()
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            // Write map
            data.putMap()
            data.enter()
            for ((key, value) in obj as Map<*, *>) {
                output.writeObjectOrNull(key, data, outboundKeyType, context, debugIndent)
                output.writeObjectOrNull(value, data, outboundValueType, context, debugIndent)
            }
            data.exit() // exit map
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Any = ifThrowsAppend({ declaredType.typeName }) {
        // TODO: General generics question. Do we need to validate that entries in Maps and Collections match the generic type?  Is it a security hole?
        val entries: Iterable<Pair<Any?, Any?>> = (obj as Map<*, *>).map { readEntry(schemas, input, it, context) }
        concreteBuilder(entries.toMap())
    }

    private fun readEntry(schemas: SerializationSchemas, input: DeserializationInput, entry: Map.Entry<Any?, Any?>,
                          context: SerializationContext
    ) = input.readObjectOrNull(entry.key, schemas, inboundKeyType, context) to
            input.readObjectOrNull(entry.value, schemas, inboundValueType, context)

    // Cannot use * as a bound for EnumMap and EnumSet since * is not an enum.  So, we use a sample enum instead.
    // We don't actually care about the type, we just need to make the compiler happier.
    internal enum class EnumJustUsedForCasting { NOT_USED }
}

internal fun Class<*>.checkSupportedMapType() {
    checkHashMap()
    checkWeakHashMap()
    checkDictionary()
}

private fun Class<*>.checkHashMap() {
    if (HashMap::class.java.isAssignableFrom(this) && !LinkedHashMap::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException(
                "Map type $this is unstable under iteration. Suggested fix: use java.util.LinkedHashMap instead.")
    }
}

/**
 * The [WeakHashMap] class does not exist within the DJVM, and so we need
 * to isolate this reference.
 */
@StubOutForDJVM
private fun Class<*>.checkWeakHashMap() {
    if (WeakHashMap::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException("Weak references with map types not supported. Suggested fix: "
                + "use java.util.LinkedHashMap instead.")
    }
}

private fun Class<*>.checkDictionary() {
    if (Dictionary::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException(
                "Unable to serialise deprecated type $this. Suggested fix: prefer java.util.map implementations")
    }
}

