package net.corda.serialization.internal.amqp

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import net.corda.core.KeepForDJVM
import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.utilities.toBase64
import net.corda.serialization.internal.amqp.SerializerFactory.Companion.isPrimitive
import java.lang.reflect.*
import java.util.*

/**
 * Should be implemented by classes which wish to provide pluggable fingerprinting on types for a [SerializerFactory]
 */
@KeepForDJVM
interface FingerPrinter {
    /**
     * Return a unique identifier for a type, usually this will take into account the constituent elements
     * of said type such that any modification to any sub element wll generate a different fingerprint
     */
    fun fingerprint(type: Type): String
}

// Create a new instance of the [Hasher] used for fingerprinting by the default [SerializerFingerPrinter]
private fun newDefaultHasher() = Hashing.murmur3_128().newHasher()

// We obtain a fingerprint from a [Hasher] by taking the Base 64 encoding of its hash bytes
private val Hasher.fingerprint get() = hash().asBytes().toBase64()

internal fun fingerprintForDescriptors(vararg typeDescriptors: String): String =
        newDefaultHasher().putUnencodedChars(typeDescriptors.joinToString()).fingerprint

/**
 * Implementation of the finger printing mechanism used by default
 */
@KeepForDJVM
class SerializerFingerPrinter(val factory: SerializerFactory) : FingerPrinter {

    /**
     * The method generates a fingerprint for a given JVM [Type] that should be unique to the schema representation.
     * Thus it only takes into account properties and types and only supports the same object graph subset as the overall
     * serialization code.
     *
     * The idea being that even for two classes that share the same name but differ in a minor way, the fingerprint will be
     * different.
     */
    override fun fingerprint(type: Type): String =
            FingerPrintingState.initial(factory).fingerprint(type)
}

// Representation of the current state of fingerprinting
internal data class FingerPrintingState(
        private val factory: SerializerFactory,
        private val hasher: Hasher,
        private val typesSeen: MutableSet<Type>,
        private val contextType: Type?) {

    companion object {
        fun initial(factory: SerializerFactory) = FingerPrintingState(
                factory,
                newDefaultHasher(),
                mutableSetOf(),
                null
        )

        private const val ARRAY_HASH: String = "Array = true"
        private const val ENUM_HASH: String = "Enum = true"
        private const val ALREADY_SEEN_HASH: String = "Already seen = true"
        private const val NULLABLE_HASH: String = "Nullable = true"
        private const val NOT_NULLABLE_HASH: String = "Nullable = false"
        private const val ANY_TYPE_HASH: String = "Any type = true"
    }

    fun fingerprint(type: Type) = fingerprintType(type).hasher.fingerprint

    // This method concatenates various elements of the types recursively as unencoded strings into the hasher,
    // effectively creating a unique string for a type which we then hash in the calling function above.
    private fun fingerprintType(type: Type): FingerPrintingState =
        ifNewType(type) {
            when (type) {
                is ParameterizedType -> fingerprintParameterizedType(type)
                // Previously, we drew a distinction between TypeVariable, WildcardType, and AnyType, changing
                // the signature of the fingerprinted object. This, however, doesn't work as it breaks bi-
                // directional fingerprints. That is, fingerprinting a concrete instance of a generic
                // type (Example<Int>), creates a different fingerprint from the generic type itself (Example<T>)
                //
                // On serialization Example<Int> is treated as Example<T>, a TypeVariable
                // On deserialisation it is seen as Example<?>, A WildcardType *and* a TypeVariable
                //      Note: AnyType is a special case of WildcardType used in other parts of the
                //            serializer so both cases need to be dealt with here
                //
                // If we treat these types as fundamentally different and alter the fingerprint we will
                // end up breaking into the evolver when we shouldn't or, worse, evoking the carpenter.
                is SerializerFactory.AnyType,
                is WildcardType,
                is TypeVariable<*> -> writeAnyType()
                is Class<*> -> fingerprintClass(type)
                is GenericArrayType -> fingerprintType(type.genericComponentType).writeArray()
                else -> throw AMQPNotSerializableException(type, "Don't know how to hash")
            }
        }


    private fun fingerprintClass(type: Class<*>): FingerPrintingState = when {
        type.isArray -> fingerprintType(type.componentType).writeArray()
        type.isPrimitiveOrCollection -> writePrimitiveOrCollection(type)
        type.isEnum -> writeEnum(type)
        else -> fingerprintWithCustomSerializerOrElse(type, type) {
            if (type.kotlinObjectInstance != null)writeKotlinObject(type)
            else fingerprintObject(type)
        }
    }

    private fun fingerprintParameterizedType(type: ParameterizedType): FingerPrintingState {
        // Hash the rawType + params
        val clazz = type.asClass()

        val startingState = if (clazz.isCollectionOrMap) writePrimitiveOrCollection(clazz)
        else fingerprintWithCustomSerializerOrElse(clazz, type) {
            fingerprintObject(type)
        }

        // ... and concatenate the type data for each parameter type.
        return type.actualTypeArguments.fold(startingState) { orig, paramType ->
            orig.fingerprintType(paramType)
        }
    }

    private fun fingerprintObject(type: Type): FingerPrintingState {
        // Hash the class + properties + interfaces
        val name = type.asClass().name

        val withProperties = propertiesForSerialization(type).fold(write(name)) { orig, prop ->
            orig.fingerprintType(prop.serializer.resolvedType).writePropSerializer(prop)
        }

        return interfacesForSerialization(type, factory).fold(withProperties) { orig, iface ->
            orig.fingerprintType(iface)
        }
    }

    fun propertiesForSerialization(type: Type): List<PropertyAccessor> {
        return propertiesForSerialization(
                constructorForDeserialization(type),
                contextType ?: type,
                factory)
                .serializationOrder
    }

    private val Class<*>.isCollectionOrMap get() =
        (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
                && !EnumSet::class.java.isAssignableFrom(this)

    private val Class<*>.isPrimitiveOrCollection get() =
        isPrimitive(this) || isCollectionOrMap

    private fun fingerprintWithCustomSerializerOrElse(
            clazz: Class<*>,
            declaredType: Type,
            block: () -> FingerPrintingState): FingerPrintingState =
            // Need to check if a custom serializer is applicable
            factory.findCustomSerializer(clazz, declaredType)?.let { write(it.typeDescriptor) } ?: block()

    private fun ifNewType(type: Type, block: FingerPrintingState.() -> FingerPrintingState) =
            if (hasSeen(type)) write(ALREADY_SEEN_HASH)
            else ifThrowsAppend(
                    { type.typeName },
                    { copy(typesSeen = typesSeen.apply { add(type) }, contextType = type).block() })

    // Test whether we are in a state in which we have already seen the given type.
    //
    // We don't include Example<?> and Example<T> where type is ? or T in this otherwise we
    // generate different fingerprints for class Outer<T>(val a: Inner<T>) when serialising
    // and deserializing (assuming deserialization is occurring in a factory that didn't
    // serialise the object in the  first place (and thus the cache lookup fails). This is also
    // true of Any, where we need  Example<A, B> and Example<?, ?> to have the same fingerprint
    private fun hasSeen(type: Type) = (type in typesSeen)
            && (type !== SerializerFactory.AnyType)
            && (type !is TypeVariable<*>)
            && (type !is WildcardType)

    // Move to a state in which the given character sequences have been written into the hasher.
    private fun write(vararg charSequences: CharSequence) = copy(hasher = charSequences.fold(hasher, Hasher::putUnencodedChars))

    private fun writeArray() = write(ARRAY_HASH)
    private fun writeAnyType() = write("?$ANY_TYPE_HASH")

    // ensures any change to the enum (adding constants) will trigger the need for evolution
    private fun writeEnum(type: Class<*>) = write(type.enumConstants.joinToString(), type.name, ENUM_HASH)
    private fun writePrimitiveOrCollection(type: Class<*>) = write(type.name)

    // TODO: name collision is too likely for kotlin objects, we need to introduce some
    // reference to the CorDapp but maybe reference to the JAR in the short term.
    private fun writeKotlinObject(type: Class<*>) = write(type.name)

    private fun writePropSerializer(prop: PropertyAccessor) = write(
            prop.serializer.name,
            if (prop.serializer.mandatory) NOT_NULLABLE_HASH else NULLABLE_HASH)

}