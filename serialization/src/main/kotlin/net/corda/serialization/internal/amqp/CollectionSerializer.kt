package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.NonEmptySet
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.LinkedHashSet

/**
 * Serialization / deserialization of predefined set of supported [Collection] types covering mostly [List]s and [Set]s.
 */
@KeepForDJVM
class CollectionSerializer(private val declaredType: ParameterizedType, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType as? DeserializedParameterizedType
            ?: DeserializedParameterizedType.make(SerializerFactory.nameForType(declaredType))
    override val typeDescriptor: Symbol by lazy {
        Symbol.valueOf("$DESCRIPTOR_DOMAIN:${factory.fingerPrinter.fingerprint(type)}")
    }

    companion object {
        // NB: Order matters in this map, the most specific classes should be listed at the end
        private val supportedTypes: Map<Class<out Collection<*>>, (List<*>) -> Collection<*>> = Collections.unmodifiableMap(linkedMapOf(
                Collection::class.java to { list -> Collections.unmodifiableCollection(list) },
                List::class.java to { list -> Collections.unmodifiableList(list) },
                Set::class.java to { list -> Collections.unmodifiableSet(LinkedHashSet(list)) },
                SortedSet::class.java to { list -> Collections.unmodifiableSortedSet(TreeSet(list)) },
                NavigableSet::class.java to { list -> Collections.unmodifiableNavigableSet(TreeSet(list)) },
                NonEmptySet::class.java to { list -> NonEmptySet.copyOf(list) }
        ))

        private fun findConcreteType(clazz: Class<*>): (List<*>) -> Collection<*> {
            return supportedTypes[clazz] ?: throw NotSerializableException("Unsupported collection type $clazz.")
        }

        fun deriveParameterizedType(declaredType: Type, declaredClass: Class<*>, actualClass: Class<*>?): ParameterizedType {
            if (supportedTypes.containsKey(declaredClass)) {
                // Simple case - it is already known to be a collection.
                return deriveParametrizedType(declaredType, uncheckedCast(declaredClass))
            } else if (actualClass != null && Collection::class.java.isAssignableFrom(actualClass)) {
                // Declared class is not collection, but [actualClass] is - represent it accordingly.
                val collectionClass = findMostSuitableCollectionType(actualClass)
                return deriveParametrizedType(declaredType, collectionClass)
            }

            throw NotSerializableException("Cannot derive collection type for declaredType: '$declaredType', declaredClass: '$declaredClass', actualClass: '$actualClass'")
        }

        private fun deriveParametrizedType(declaredType: Type, collectionClass: Class<out Collection<*>>): ParameterizedType =
                (declaredType as? ParameterizedType)
                        ?: DeserializedParameterizedType(collectionClass, arrayOf(SerializerFactory.AnyType))

        private fun findMostSuitableCollectionType(actualClass: Class<*>): Class<out Collection<*>> =
                supportedTypes.keys.findLast { it.isAssignableFrom(actualClass) }!!
    }

    private val concreteBuilder: (List<*>) -> Collection<*> = findConcreteType(declaredType.rawType as Class<*>)

    private val typeNotation: TypeNotation = RestrictedType(SerializerFactory.nameForType(declaredType), null, emptyList(), "list", Descriptor(typeDescriptor), emptyList())

    override fun writeClassInfo(output: SerializationOutput) = ifThrowsAppend({ declaredType.typeName }) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(resolveTypeVariables(declaredType.actualTypeArguments[0], null))
        }
    }

    override fun writeObject(
            obj: Any,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int) = ifThrowsAppend({ declaredType.typeName }) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            withList {
                for (entry in obj as Collection<*>) {
                    output.writeObjectOrNull(entry, this, resolveTypeVariables(declaredType.actualTypeArguments[0], null), context, debugIndent)
                }
            }
        }
    }

    override fun readObject(
            obj: Any,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ declaredType.typeName }) {
        // TODO: Can we verify the entries in the list?
        concreteBuilder((obj as List<*>).map {
            input.readObjectOrNull(it, schemas, declaredType.actualTypeArguments[0], context)
        })
    }
}