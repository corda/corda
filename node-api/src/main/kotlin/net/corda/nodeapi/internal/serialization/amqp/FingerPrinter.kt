/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.toBase64
import java.io.NotSerializableException
import java.lang.reflect.*
import java.util.*

/**
 * Should be implemented by classes which wish to provide plugable fingerprinting og types for a [SerializerFactory]
 */
interface FingerPrinter {
    /**
     * Return a unique identifier for a type, usually this will take into account the constituent elements
     * of said type such that any modification to any sub element wll generate a different fingerprint
     */
    fun fingerprint(type: Type): String

    /**
     * If required, associate an instance of the fingerprinter with a specific serializer factory
     */
    fun setOwner(factory: SerializerFactory)
}

/**
 * Implementation of the finger printing mechanism used by default
 */
class SerializerFingerPrinter : FingerPrinter {
    private var factory: SerializerFactory? = null

    private val ARRAY_HASH: String = "Array = true"
    private val ENUM_HASH: String = "Enum = true"
    private val ALREADY_SEEN_HASH: String = "Already seen = true"
    private val NULLABLE_HASH: String = "Nullable = true"
    private val NOT_NULLABLE_HASH: String = "Nullable = false"
    private val ANY_TYPE_HASH: String = "Any type = true"
    private val TYPE_VARIABLE_HASH: String = "Type variable = true"
    private val WILDCARD_TYPE_HASH: String = "Wild card = true"

    private val logger by lazy { loggerFor<Schema>() }

    override fun setOwner(factory: SerializerFactory) {
        this.factory = factory
    }

    /**
     * The method generates a fingerprint for a given JVM [Type] that should be unique to the schema representation.
     * Thus it only takes into account properties and types and only supports the same object graph subset as the overall
     * serialization code.
     *
     * The idea being that even for two classes that share the same name but differ in a minor way, the fingerprint will be
     * different.
     */
    override fun fingerprint(type: Type): String {
        return fingerprintForType(
                type, null, HashSet(), Hashing.murmur3_128().newHasher(), debugIndent = 1).hash().asBytes().toBase64()
    }

    private fun isCollectionOrMap(type: Class<*>) =
            (Collection::class.java.isAssignableFrom(type) || Map::class.java.isAssignableFrom(type))
                    && !EnumSet::class.java.isAssignableFrom(type)

    internal fun fingerprintForDescriptors(vararg typeDescriptors: String): String {
        val hasher = Hashing.murmur3_128().newHasher()
        for (typeDescriptor in typeDescriptors) {
            hasher.putUnencodedChars(typeDescriptor)
        }
        return hasher.hash().asBytes().toBase64()
    }

    private fun Hasher.fingerprintWithCustomSerializerOrElse(
            factory: SerializerFactory,
            clazz: Class<*>,
            declaredType: Type,
            block: () -> Hasher): Hasher {
        // Need to check if a custom serializer is applicable
        val customSerializer = factory.findCustomSerializer(clazz, declaredType)
        return if (customSerializer != null) {
            putUnencodedChars(customSerializer.typeDescriptor)
        } else {
            block()
        }
    }

    // This method concatenates various elements of the types recursively as unencoded strings into the hasher,
    // effectively creating a unique string for a type which we then hash in the calling function above.
    private fun fingerprintForType(type: Type, contextType: Type?, alreadySeen: MutableSet<Type>,
                                   hasher: Hasher, debugIndent: Int = 1): Hasher {
        // We don't include Example<?> and Example<T> where type is ? or T in this otherwise we
        // generate different fingerprints for class Outer<T>(val a: Inner<T>) when serialising
        // and deserializing (assuming deserialization is occurring in a factory that didn't
        // serialise the object in the  first place (and thus the cache lookup fails). This is also
        // true of Any, where we need  Example<A, B> and Example<?, ?> to have the same fingerprint
        return if ((type in alreadySeen)
                && (type !is SerializerFactory.AnyType)
                && (type !is TypeVariable<*>)
                && (type !is WildcardType)) {
            hasher.putUnencodedChars(ALREADY_SEEN_HASH)
        } else {
            alreadySeen += type
            try {
                when (type) {
                    is ParameterizedType -> {
                        // Hash the rawType + params
                        val clazz = type.rawType as Class<*>

                        val startingHash = if (isCollectionOrMap(clazz)) {
                            hasher.putUnencodedChars(clazz.name)
                        } else {
                            hasher.fingerprintWithCustomSerializerOrElse(factory!!, clazz, type) {
                                fingerprintForObject(type, type, alreadySeen, hasher, factory!!, debugIndent + 1)
                            }
                        }

                        // ... and concatenate the type data for each parameter type.
                        type.actualTypeArguments.fold(startingHash) { orig, paramType ->
                            fingerprintForType(paramType, type, alreadySeen, orig, debugIndent + 1)
                        }
                    }
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
                    is TypeVariable<*> -> {
                        hasher.putUnencodedChars("?").putUnencodedChars(ANY_TYPE_HASH)
                    }
                    is Class<*> -> {
                        if (type.isArray) {
                            fingerprintForType(type.componentType, contextType, alreadySeen, hasher, debugIndent + 1)
                                    .putUnencodedChars(ARRAY_HASH)
                        } else if (SerializerFactory.isPrimitive(type)) {
                            hasher.putUnencodedChars(type.name)
                        } else if (isCollectionOrMap(type)) {
                            hasher.putUnencodedChars(type.name)
                        } else if (type.isEnum) {
                            // ensures any change to the enum (adding constants) will trigger the need for evolution
                            hasher.apply {
                                type.enumConstants.forEach {
                                    putUnencodedChars(it.toString())
                                }
                            }.putUnencodedChars(type.name).putUnencodedChars(ENUM_HASH)
                        } else {
                            hasher.fingerprintWithCustomSerializerOrElse(factory!!, type, type) {
                                if (type.objectInstance() != null) {
                                    // TODO: name collision is too likely for kotlin objects, we need to introduce some reference
                                    // to the CorDapp but maybe reference to the JAR in the short term.
                                    hasher.putUnencodedChars(type.name)
                                } else {
                                    fingerprintForObject(type, type, alreadySeen, hasher, factory!!, debugIndent + 1)
                                }
                            }
                        }
                    }
                // Hash the element type + some array hash
                    is GenericArrayType -> {
                        fingerprintForType(type.genericComponentType, contextType, alreadySeen,
                                hasher, debugIndent + 1).putUnencodedChars(ARRAY_HASH)
                    }
                    else -> throw NotSerializableException("Don't know how to hash")
                }
            } catch (e: NotSerializableException) {
                val msg = "${e.message} -> $type"
                logger.error(msg, e)
                throw NotSerializableException(msg)
            }
        }
    }

    private fun fingerprintForObject(
            type: Type,
            contextType: Type?,
            alreadySeen: MutableSet<Type>,
            hasher: Hasher,
            factory: SerializerFactory,
            debugIndent: Int = 0): Hasher {
        // Hash the class + properties + interfaces
        val name = type.asClass()?.name
                ?: throw NotSerializableException("Expected only Class or ParameterizedType but found $type")

        propertiesForSerialization(constructorForDeserialization(type), contextType ?: type, factory)
                .serializationOrder
                .fold(hasher.putUnencodedChars(name)) { orig, prop ->
                    fingerprintForType(prop.getter.resolvedType, type, alreadySeen, orig, debugIndent + 1)
                            .putUnencodedChars(prop.getter.name)
                            .putUnencodedChars(if (prop.getter.mandatory) NOT_NULLABLE_HASH else NULLABLE_HASH)
                }
        interfacesForSerialization(type, factory).map { fingerprintForType(it, type, alreadySeen, hasher, debugIndent + 1) }
        return hasher
    }
}