package net.corda.serialization.internal.amqp

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import net.corda.core.KeepForDJVM
import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.utilities.toBase64
import net.corda.serialization.internal.model.*
import java.lang.StringBuilder
import java.lang.reflect.*

fun getTypeModellingFingerPrinter(factory: SerializerFactory): FingerPrinter {
    return TypeModellingFingerPrinter(LocalTypeModel(DefaultOpacityRule), factory.whitelist, object : CustomTypeDescriptorLookup {
        override fun getCustomTypeDescriptor(type: Type): String? =
                factory.findCustomSerializer(type.asClass(), type)?.typeDescriptor?.toString()
    })
}

interface CustomTypeDescriptorLookup {
    fun getCustomTypeDescriptor(type: Type): String?
}
/**
 * Implementation of the finger printing mechanism used by default
 */
@KeepForDJVM
class TypeModellingFingerPrinter(val typeModel: LocalTypeModel, val whitelist: ClassWhitelist, val customTypeDescriptorLookup: CustomTypeDescriptorLookup) : FingerPrinter {

    /**
     * The method generates a fingerprint for a given JVM [Type] that should be unique to the schema representation.
     * Thus it only takes into account properties and types and only supports the same object graph subset as the overall
     * serialization code.
     *
     * The idea being that even for two classes that share the same name but differ in a minor way, the fingerprint will be
     * different.
     */
    override fun fingerprint(type: Type): String = TypeModellingFingerPrintingState(customTypeDescriptorLookup, whitelist).fingerprint(typeModel.interpret(type))
}

// Representation of the current state of fingerprinting
private class TypeModellingFingerPrintingState(private val customTypeDescriptorLookup: CustomTypeDescriptorLookup, private val whitelist: ClassWhitelist) {

    companion object {
        private const val ARRAY_HASH: String = "Array = true"
        private const val ENUM_HASH: String = "Enum = true"
        private const val ALREADY_SEEN_HASH: String = "Already seen = true"
        private const val NULLABLE_HASH: String = "Nullable = true"
        private const val NOT_NULLABLE_HASH: String = "Nullable = false"
        private const val ANY_TYPE_HASH: String = "Any type = true"
    }

    private val typesSeen: MutableSet<TypeIdentifier> = mutableSetOf()
    private val buffer = StringBuilder()
    private var hasher: Hasher = newDefaultHasher()

    // Fingerprint the type recursively, and return the encoded fingerprint written into the hasher.
    fun fingerprint(type: LocalTypeInformation): String {
        val title = type.typeIdentifier.prettyPrint()
        println("=".repeat(title.length))
        println(title)
        println("=".repeat(title.length))
        fingerprintType(type)
        println(buffer)
        return hasher.fingerprint
    }

    // This method concatenates various elements of the types recursively as unencoded strings into the hasher,
    // effectively creating a unique string for a type which we then hash in the calling function above.
    private fun fingerprintType(type: LocalTypeInformation): TypeModellingFingerPrintingState = apply {
        // Don't go round in circles.
        println(type.typeIdentifier.prettyPrint())
        if (hasSeen(type.typeIdentifier)) append(ALREADY_SEEN_HASH)
        else ifThrowsAppend(
                { type.observedType.typeName },
                {
                    typesSeen.add(type.typeIdentifier)
                    fingerprintNewType(type)
                })
    }

    // For a type we haven't seen before, determine the correct path depending on the type of type it is.
    private fun fingerprintNewType(type: LocalTypeInformation) = when (type) {
        is LocalTypeInformation.Cycle -> append(ALREADY_SEEN_HASH)
        is LocalTypeInformation.Unknown,
        is LocalTypeInformation.Any -> append("?$ANY_TYPE_HASH")
        is LocalTypeInformation.AnArray -> fingerprintType(type.componentType).append(ARRAY_HASH)
        is LocalTypeInformation.ACollection -> {
            append(type.typeIdentifier.name)
            type.typeParameters.forEach { fingerprintType(it) }
        }
        is LocalTypeInformation.APrimitive -> append(type.typeIdentifier.name)
        is LocalTypeInformation.Opaque -> {
            fingerprintWithCustomSerializerOrElse(type) {
                append(type.typeIdentifier.name)
            }
        }
        is LocalTypeInformation.AnInterface -> {
            fingerprintWithCustomSerializerOrElse(type) {
                append(type.typeIdentifier.name)
                fingerprintType(type) // replicate behaviour of old fingerprinter
                fingerprintInterfaces(type.interfaces)
                fingerprintTypeParameters(type)
            }
        }
        is LocalTypeInformation.AnEnum -> fingerprintEnum(type)
        is LocalTypeInformation.AnObject -> {
            fingerprintWithCustomSerializerOrElse(type) {
                if (type.observedType.asClass().kotlinObjectInstance != null) append(type.typeIdentifier.name)
                else fingerprintObject(type)
            }
            type.typeParameters.forEach { fingerprintType(it) }
        }
    }

    private fun fingerprintTypeParameters(type: LocalTypeInformation.AnInterface) {
        type.typeParameters.forEach { fingerprintType(it) }
    }

    private fun fingerprintObject(type: LocalTypeInformation.AnObject) {
        // Hash the class + properties + interfaces
        append(type.typeIdentifier.name)

        type.properties.entries.sortedBy { it.key }.forEach { (propertyName, propertyType) ->
            print(propertyName)
            print(" -> ")
            fingerprintType(propertyType.type)
            append(propertyName)
            append(if (propertyType.isMandatory) NOT_NULLABLE_HASH else NULLABLE_HASH)
        }

        fingerprintInterfaces(type.interfaces)
    }

    private fun fingerprintInterfaces(interfaces: List<LocalTypeInformation>) {
        interfaces.filter { whitelist.hasListed(it.observedType.asClass()) }.forEach {
            fingerprintType(it)
        }
    }

    // ensures any change to the enum (adding constants) will trigger the need for evolution
    private fun fingerprintEnum(type: LocalTypeInformation.AnEnum) {
        append(type.members.joinToString())
        append(type.typeIdentifier.name)
        append(ENUM_HASH)
    }

    // Write the given character sequence into the hasher.
    private fun append(chars: CharSequence) {
        buffer.append(chars)
        hasher = hasher.putUnencodedChars(chars)
    }

    // Give any custom serializers loaded into the factory the chance to supply their own type-descriptors
    private fun fingerprintWithCustomSerializerOrElse(
            type: LocalTypeInformation,
            defaultAction: () -> Unit)
            : Unit = customTypeDescriptorLookup.getCustomTypeDescriptor(type.observedType)?.let {
                append(it)
            } ?: defaultAction()

    // Test whether we are in a state in which we have already seen the given type.
    //
    // We don't include Example<?> and Example<T> where type is ? or T in this otherwise we
    // generate different fingerprints for class Outer<T>(val a: Inner<T>) when serialising
    // and deserializing (assuming deserialization is occurring in a factory that didn't
    // serialise the object in the  first place (and thus the cache lookup fails). This is also
    // true of Any, where we need  Example<A, B> and Example<?, ?> to have the same fingerprint
    private fun hasSeen(type: TypeIdentifier) = (type in typesSeen)
            && (type != TypeIdentifier.Unknown)

}

// region Utility functions

// Create a new instance of the [Hasher] used for fingerprinting by the default [SerializerFingerPrinter]
private fun newDefaultHasher() = Hashing.murmur3_128().newHasher()

// We obtain a fingerprint from a [Hasher] by taking the Base 64 encoding of its hash bytes
private val Hasher.fingerprint get() = hash().asBytes().toBase64()

internal fun fingerprintForDescriptors(vararg typeDescriptors: String): String =
        newDefaultHasher().putUnencodedChars(typeDescriptors.joinToString()).fingerprint
// endregion
