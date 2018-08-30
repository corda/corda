/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import net.corda.core.KeepForDJVM
import net.corda.core.internal.isConcreteClass
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
    override fun fingerprint(type: Type): String = FingerPrintingState(factory).fingerprint(type)
}

// Representation of the current state of fingerprinting
internal class FingerPrintingState(private val factory: SerializerFactory) {

    companion object {
        private const val ARRAY_HASH: String = "Array = true"
        private const val ENUM_HASH: String = "Enum = true"
        private const val ALREADY_SEEN_HASH: String = "Already seen = true"
        private const val NULLABLE_HASH: String = "Nullable = true"
        private const val NOT_NULLABLE_HASH: String = "Nullable = false"
        private const val ANY_TYPE_HASH: String = "Any type = true"
    }

    private val typesSeen: MutableSet<Type> = mutableSetOf()
    private var currentContext: Type? = null
    private var hasher: Hasher = newDefaultHasher()

    // Fingerprint the type recursively, and return the encoded fingerprint written into the hasher.
    fun fingerprint(type: Type) = fingerprintType(type).hasher.fingerprint

    // This method concatenates various elements of the types recursively as unencoded strings into the hasher,
    // effectively creating a unique string for a type which we then hash in the calling function above.
    private fun fingerprintType(type: Type): FingerPrintingState = apply {
        // Don't go round in circles.
        if (hasSeen(type)) append(ALREADY_SEEN_HASH)
        else ifThrowsAppend(
                { type.typeName },
                {
                    typesSeen.add(type)
                    currentContext = type
                    fingerprintNewType(type)
                })
    }

    // For a type we haven't seen before, determine the correct path depending on the type of type it is.
    private fun fingerprintNewType(type: Type) = when (type) {
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
        is TypeVariable<*> -> append("?$ANY_TYPE_HASH")
        is Class<*> -> fingerprintClass(type)
        is GenericArrayType -> fingerprintType(type.genericComponentType).append(ARRAY_HASH)
        else -> throw AMQPNotSerializableException(type, "Don't know how to hash")
    }

    private fun fingerprintClass(type: Class<*>) = when {
        type.isArray -> fingerprintType(type.componentType).append(ARRAY_HASH)
        type.isPrimitiveOrCollection -> append(type.name)
        type.isEnum -> fingerprintEnum(type)
        else -> fingerprintWithCustomSerializerOrElse(type, type) {
            if (type.kotlinObjectInstance != null) append(type.name)
            else fingerprintObject(type)
        }
    }

    private fun fingerprintParameterizedType(type: ParameterizedType) {
        // Hash the rawType + params
        type.asClass().let { clazz ->
            if (clazz.isCollectionOrMap) append(clazz.name)
            else fingerprintWithCustomSerializerOrElse(clazz, type) {
                fingerprintObject(type)
            }
        }

        // ...and concatenate the type data for each parameter type.
        type.actualTypeArguments.forEach { paramType ->
            fingerprintType(paramType)
        }
    }

    private fun fingerprintObject(type: Type) {
        // Hash the class + properties + interfaces
        append(type.asClass().name)

        orderedPropertiesForSerialization(type).forEach { prop ->
            fingerprintType(prop.serializer.resolvedType)
            fingerprintPropSerialiser(prop)
        }

        interfacesForSerialization(type, factory).forEach { iface ->
            fingerprintType(iface)
        }
    }

    // ensures any change to the enum (adding constants) will trigger the need for evolution
    private fun fingerprintEnum(type: Class<*>) {
        append(type.enumConstants.joinToString())
        append(type.name)
        append(ENUM_HASH)
    }

    private fun fingerprintPropSerialiser(prop: PropertyAccessor) {
        append(prop.serializer.name)
        append(if (prop.serializer.mandatory) NOT_NULLABLE_HASH
               else NULLABLE_HASH)
    }

    // Write the given character sequence into the hasher.
    private fun append(chars: CharSequence) {
        hasher = hasher.putUnencodedChars(chars)
    }

    // Give any custom serializers loaded into the factory the chance to supply their own type-descriptors
    private fun fingerprintWithCustomSerializerOrElse(
            clazz: Class<*>,
            declaredType: Type,
            defaultAction: () -> Unit)
            : Unit = factory.findCustomSerializer(clazz, declaredType)?.let {
        append(it.typeDescriptor)
    } ?: defaultAction()

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

    private fun orderedPropertiesForSerialization(type: Type): List<PropertyAccessor> {
        return propertiesForSerialization(
                if (type.asClass().isConcreteClass) constructorForDeserialization(type) else null,
                currentContext ?: type,
                factory).serializationOrder
    }

}

// region Utility functions

// Create a new instance of the [Hasher] used for fingerprinting by the default [SerializerFingerPrinter]
private fun newDefaultHasher() = Hashing.murmur3_128().newHasher()

// We obtain a fingerprint from a [Hasher] by taking the Base 64 encoding of its hash bytes
private val Hasher.fingerprint get() = hash().asBytes().toBase64()

internal fun fingerprintForDescriptors(vararg typeDescriptors: String): String =
        newDefaultHasher().putUnencodedChars(typeDescriptors.joinToString()).fingerprint

private val Class<*>.isCollectionOrMap get() =
    (Collection::class.java.isAssignableFrom(this) || Map::class.java.isAssignableFrom(this))
            && !EnumSet::class.java.isAssignableFrom(this)

private val Class<*>.isPrimitiveOrCollection get() =
    isPrimitive(this) || isCollectionOrMap
// endregion
