package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.NonEmptySet
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.*
import kotlin.collections.LinkedHashSet

/**
 * Serialization / deserialization of predefined set of supported [Collection] types covering mostly [List]s and [Set]s.
 */
@KeepForDJVM
class CollectionSerializer(private val declaredType: ParameterizedType, factory: LocalSerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType

    override val typeDescriptor: Symbol by lazy {
        factory.createDescriptor(type)
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

        private val supportedTypeIdentifiers = supportedTypes.keys.asSequence().map { TypeIdentifier.forClass(it) }.toSet()

        fun resolveDeclared(declaredTypeInformation: LocalTypeInformation.ACollection): LocalTypeInformation.ACollection {
            if (declaredTypeInformation.typeIdentifier.erased in supportedTypeIdentifiers)
                return reparameterise(declaredTypeInformation)

            throw NotSerializableException(
                    "Cannot derive collection type for declared type: " +
                            declaredTypeInformation.prettyPrint(false))
        }

        fun resolveActual(actualClass: Class<*>, declaredTypeInformation: LocalTypeInformation.ACollection): LocalTypeInformation.ACollection {
            if (declaredTypeInformation.typeIdentifier.erased in supportedTypeIdentifiers)
                return reparameterise(declaredTypeInformation)

            val collectionClass = findMostSuitableCollectionType(actualClass)
            val erasedInformation = LocalTypeInformation.ACollection(
                    collectionClass,
                    TypeIdentifier.forClass(collectionClass),
                    LocalTypeInformation.Unknown)

            return when(declaredTypeInformation.typeIdentifier) {
                is TypeIdentifier.Parameterised -> erasedInformation.withElementType(declaredTypeInformation.elementType)
                else -> erasedInformation.withElementType(LocalTypeInformation.Unknown)
            }
        }

        private fun reparameterise(typeInformation: LocalTypeInformation.ACollection): LocalTypeInformation.ACollection =
                when(typeInformation.typeIdentifier) {
                    is TypeIdentifier.Parameterised -> typeInformation
                    is TypeIdentifier.Erased -> typeInformation.withElementType(LocalTypeInformation.Unknown)
                    else -> throw NotSerializableException(
                            "Unexpected type identifier ${typeInformation.typeIdentifier.prettyPrint(false)} " +
                                    "for collection type ${typeInformation.prettyPrint(false)}")
                }

        private fun findMostSuitableCollectionType(actualClass: Class<*>): Class<out Collection<*>> =
                supportedTypes.keys.findLast { it.isAssignableFrom(actualClass) }!!

        private fun findConcreteType(clazz: Class<*>): (List<*>) -> Collection<*> {
            return supportedTypes[clazz] ?: throw AMQPNotSerializableException(
                    clazz,
                    "Unsupported collection type $clazz.",
                    "Supported Collections are ${supportedTypes.keys.joinToString(",")}")
        }
    }

    private val concreteBuilder: (List<*>) -> Collection<*> = findConcreteType(declaredType.rawType as Class<*>)

    private val typeNotation: TypeNotation = RestrictedType(SerializerFactory.nameForType(declaredType), null, emptyList(), "list", Descriptor(typeDescriptor), emptyList())

    private val outboundType = resolveTypeVariables(declaredType.actualTypeArguments[0], null)
    private val inboundType = declaredType.actualTypeArguments[0]


    override fun writeClassInfo(output: SerializationOutput) = ifThrowsAppend({ declaredType.typeName }) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(outboundType)
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
                    output.writeObjectOrNull(entry, this, outboundType, context, debugIndent)
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
            input.readObjectOrNull(it, schemas, inboundType, context)
        })
    }
}