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
 * Should be implemented by classes which wish to provide pluggable fingerprinting og types for a [SerializerFactory]
 */
@KeepForDJVM
interface FingerPrinter {
    /**
     * Return a unique identifier for a type, usually this will take into account the constituent elements
     * of said type such that any modification to any sub element wll generate a different fingerprint
     */
    fun fingerprint(type: Type): String
}

// Representation of the current state of fingerprinting
internal data class FingerPrintingState(val hasher: Hasher, val typesSeen: Set<Type>, val contextType: Type?) {
    companion object {
        val initial get() = FingerPrintingState(
                Hashing.murmur3_128().newHasher(),
                emptySet(),
                null
        )
    }

    // Move to a state which has seen the given type, and has it as its context.
    fun observe(type: Type) = copy(typesSeen = typesSeen + type, contextType = type)

    // Test whether we are in a state in which we have already seen the given type.
    //
    // We don't include Example<?> and Example<T> where type is ? or T in this otherwise we
    // generate different fingerprints for class Outer<T>(val a: Inner<T>) when serialising
    // and deserializing (assuming deserialization is occurring in a factory that didn't
    // serialise the object in the  first place (and thus the cache lookup fails). This is also
    // true of Any, where we need  Example<A, B> and Example<?, ?> to have the same fingerprint
    fun hasSeen(type: Type) = (type in typesSeen)
            && (type !== SerializerFactory.AnyType)
            && (type !is TypeVariable<*>)
            && (type !is WildcardType)

    // Move to a state in which the given characters have been written into the hasher.
    fun write(chars: CharSequence) = copy(hasher = hasher.putUnencodedChars(chars))

    // Obtain the fingerprint from the hasher.
    val fingerprint: String get() = hasher.hash().asBytes().toBase64()
}

/**
 * Implementation of the finger printing mechanism used by default
 */
@KeepForDJVM
class SerializerFingerPrinter(val factory: SerializerFactory) : FingerPrinter {

    companion object {
        private const val ARRAY_HASH: String = "Array = true"
        private const val ENUM_HASH: String = "Enum = true"
        private const val ALREADY_SEEN_HASH: String = "Already seen = true"
        private const val NULLABLE_HASH: String = "Nullable = true"
        private const val NOT_NULLABLE_HASH: String = "Nullable = false"
        private const val ANY_TYPE_HASH: String = "Any type = true"

        internal fun fingerprintForDescriptors(vararg typeDescriptors: String): String =
            FingerPrintingState.initial.write(typeDescriptors.joinToString()).fingerprint
    }

    /**
     * The method generates a fingerprint for a given JVM [Type] that should be unique to the schema representation.
     * Thus it only takes into account properties and types and only supports the same object graph subset as the overall
     * serialization code.
     *
     * The idea being that even for two classes that share the same name but differ in a minor way, the fingerprint will be
     * different.
     */
    override fun fingerprint(type: Type): String =
        fingerprintForType(type, FingerPrintingState.initial).fingerprint

    // This method concatenates various elements of the types recursively as unencoded strings into the hasher,
    // effectively creating a unique string for a type which we then hash in the calling function above.
    private fun fingerprintForType(type: Type, state: FingerPrintingState): FingerPrintingState =
        if (state.hasSeen(type)) state.write(ALREADY_SEEN_HASH)
        else ifThrowsAppend(
            { type.typeName },
            { fingerprintForNewType(type, state.observe(type)) })

    private fun fingerprintForNewType(type: Type, state: FingerPrintingState): FingerPrintingState =
        when (type) {
            is ParameterizedType -> fingerprintForParameterizedType(type, state)
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
            is TypeVariable<*> -> state.write("?$ANY_TYPE_HASH")
            is Class<*> -> fingerprintForClass(type, state)
            // Hash the element type + some array hash
            is GenericArrayType -> fingerprintForType(type.genericComponentType, state).write(ARRAY_HASH)
            else -> throw AMQPNotSerializableException(type, "Don't know how to hash")
        }

    private fun fingerprintForClass(type: Class<*>, state: FingerPrintingState): FingerPrintingState = when {
        type.isArray -> fingerprintForType(type.componentType, state).write(ARRAY_HASH)
        type.isPrimitiveOrCollection -> state.write(type.name)
        type.isEnum ->
            // ensures any change to the enum (adding constants) will trigger the need for evolution
            state.write("${type.enumConstants.joinToString()}${type.name}$ENUM_HASH")
        else -> state.fingerprintWithCustomSerializerOrElse(type, type) {
            if (type.kotlinObjectInstance != null) {
                // TODO: name collision is too likely for kotlin objects, we need to introduce some
                // reference to the CorDapp but maybe reference to the JAR in the short term.
                write(type.name)
            } else {
                fingerprintForObject(type, this)
            }
        }
    }

    private fun fingerprintForParameterizedType(type: ParameterizedType, state: FingerPrintingState): FingerPrintingState {
        // Hash the rawType + params
        val clazz = type.asClass()

        val startingState = if (clazz.isCollectionOrMap) state.write(clazz.name)
        else state.fingerprintWithCustomSerializerOrElse(clazz, type) {
            fingerprintForObject(type, this)
        }

        // ... and concatenate the type data for each parameter type.
        return type.actualTypeArguments.fold(startingState) { orig, paramType ->
            fingerprintForType(paramType, orig)
        }
    }

    private fun fingerprintForObject(type: Type, state: FingerPrintingState): FingerPrintingState {
        // Hash the class + properties + interfaces
        val name = type.asClass().name

        val withProperties = propertiesForSerialization(
                constructorForDeserialization(type),
                state.contextType ?: type,
                factory)
                .serializationOrder
                .fold(state.write(name)) { orig, prop ->
                    fingerprintForType(prop.serializer.resolvedType, orig)
                        .write("${prop.serializerName}${prop.mandatoryNess}")
                }
        return interfacesForSerialization(type, factory).fold(withProperties) { orig, iface ->
            fingerprintForType(iface, orig)
        }
    }

    private val PropertyAccessor.serializerName get() = serializer.name
    private val PropertyAccessor.mandatoryNess get() = if (serializer.mandatory) NOT_NULLABLE_HASH else NULLABLE_HASH

    private val Class<*>.isCollectionOrMap get() =
            (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
                    && !EnumSet::class.java.isAssignableFrom(this)

    private val Class<*>.isPrimitiveOrCollection get() =
        isPrimitive(this) || isCollectionOrMap

    private fun FingerPrintingState.fingerprintWithCustomSerializerOrElse(
            clazz: Class<*>,
            declaredType: Type,
            block: FingerPrintingState.() -> FingerPrintingState): FingerPrintingState =
            // Need to check if a custom serializer is applicable
            factory.findCustomSerializer(clazz, declaredType)?.let { write(it.typeDescriptor) } ?: block()
}