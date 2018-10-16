package net.corda.serialization.internal.amqp

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import com.google.common.primitives.Primitives
import net.corda.core.KeepForDJVM
import net.corda.core.utilities.toBase64
import net.corda.serialization.internal.model.*
import org.apache.qpid.proton.amqp.*
import java.lang.reflect.*
import java.util.*

// Bridges between [SerializerFactory] and [TypeModellingFingerPrinter].
internal fun getTypeModellingFingerPrinter(factory: SerializerFactory): FingerPrinter {
    val customTypeDescriptorLookup = object : CustomTypeDescriptorLookup {
        override fun getCustomTypeDescriptor(type: Type): String? =
                factory.findCustomSerializer(type.asClass(), type)?.typeDescriptor?.toString()
    }
    return TypeModellingFingerPrinter(
            LocalTypeModel(
                    WhitelistBasedTypeModelConfiguration(
                            factory.whitelist
                    ) { Primitives.unwrap(it.asClass()) in opaqueTypes ||
                            customTypeDescriptorLookup.getCustomTypeDescriptor(it) != null }),
            customTypeDescriptorLookup)
}

/**
 * Implementation of the finger printing mechanism used by default
 */
@KeepForDJVM
class TypeModellingFingerPrinter(val typeModel: LocalTypeModel, val customTypeDescriptorLookup: CustomTypeDescriptorLookup) : FingerPrinter {

    /**
     * The method generates a fingerprint for a given JVM [Type] that should be unique to the schema representation.
     * Thus it only takes into account properties and types and only supports the same object graph subset as the overall
     * serialization code.
     *
     * The idea being that even for two classes that share the same name but differ in a minor way, the fingerprint will be
     * different.
     */
    override fun fingerprint(type: Type): String =
            TypeModellingFingerPrintingState(customTypeDescriptorLookup).fingerprint(typeModel.inspect(type))
}

// Representation of the current state of fingerprinting
private class TypeModellingFingerPrintingState(private val customTypeDescriptorLookup: CustomTypeDescriptorLookup) {

    companion object {
        private const val ARRAY_HASH: String = "Array = true"
        private const val ENUM_HASH: String = "Enum = true"
        private const val ALREADY_SEEN_HASH: String = "Already seen = true"
        private const val NULLABLE_HASH: String = "Nullable = true"
        private const val NOT_NULLABLE_HASH: String = "Nullable = false"
        private const val ANY_TYPE_HASH: String = "Any type = true"
    }

    private val typesSeen: MutableSet<TypeIdentifier> = mutableSetOf()
    private var hasher: Hasher = newDefaultHasher()

    // Fingerprint the type recursively, and return the encoded fingerprint written into the hasher.
    fun fingerprint(type: LocalTypeInformation): String =
        fingerprintType(type).hasher.fingerprint

    // This method concatenates various elements of the types recursively as unencoded strings into the hasher,
    // effectively creating a unique string for a type which we then hash in the calling function above.
    private fun fingerprintType(type: LocalTypeInformation): TypeModellingFingerPrintingState = apply {
        // Don't go round in circles.
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
        is LocalTypeInformation.ACollection -> fingerprintCollection(type)
        is LocalTypeInformation.APrimitive -> fingerprintName(type)
        is LocalTypeInformation.Opaque -> fingerprintOpaque(type)
        is LocalTypeInformation.AnEnum -> fingerprintEnum(type)
        is LocalTypeInformation.AnInterface -> fingerprintInterface(type)
        is LocalTypeInformation.Abstract -> fingerprintAbstract(type)
        is LocalTypeInformation.AnObject -> fingerprintName(type)
        is LocalTypeInformation.APojo -> fingerprintPojo(type)
    }

    private fun fingerprintCollection(type: LocalTypeInformation.ACollection) {
        fingerprintName(type)
        fingerprintTypeParameters(type.typeParameters)
    }

    private fun fingerprintOpaque(type: LocalTypeInformation) =
        fingerprintWithCustomSerializerOrElse(type) {
            fingerprintName(type)
        }

    private fun fingerprintInterface(type: LocalTypeInformation.AnInterface) =
        fingerprintWithCustomSerializerOrElse(type) {
            fingerprintName(type)
            fingerprintType(type) // replicate behaviour of old fingerprinter
            fingerprintInterfaces(type.interfaces)
            fingerprintTypeParameters(type.typeParameters)
        }

    private fun fingerprintAbstract(type: LocalTypeInformation.Abstract) =
        fingerprintWithCustomSerializerOrElse(type) {
            fingerprintName(type)
            fingerprintProperties(type.properties)
            fingerprintInterfaces(type.interfaces)
            fingerprintTypeParameters(type.typeParameters)
        }

    private fun fingerprintPojo(type: LocalTypeInformation.APojo) =
        fingerprintWithCustomSerializerOrElse(type) {
            fingerprintName(type)
            fingerprintProperties(type.properties)
            fingerprintInterfaces(type.interfaces)
            fingerprintTypeParameters(type.typeParameters)
        }

    private fun fingerprintName(type: LocalTypeInformation) = append(type.typeIdentifier.name)

    private fun fingerprintTypeParameters(typeParameters: List<LocalTypeInformation>) =
        typeParameters.forEach { fingerprintType(it) }

    private fun fingerprintProperties(properties: Map<String, LocalPropertyInformation>) =
        properties.asSequence().sortedBy { it.key }.forEach { (propertyName, propertyType) ->
            fingerprintType(propertyType.type)
            append(propertyName)
            append(if (propertyType.isMandatory) NOT_NULLABLE_HASH else NULLABLE_HASH)
        }

    private fun fingerprintInterfaces(interfaces: List<LocalTypeInformation>) =
        interfaces.forEach { fingerprintType(it) }

    // ensures any change to the enum (adding constants) will trigger the need for evolution
    private fun fingerprintEnum(type: LocalTypeInformation.AnEnum) {
        append(type.members.joinToString())
        append(type.typeIdentifier.name)
        append(ENUM_HASH)
    }

    // Write the given character sequence into the hasher.
    private fun append(chars: CharSequence) {
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

private val opaqueTypes = setOf(
        Character::class.java,
        Char::class.java,
        Boolean::class.java,
        Byte::class.java,
        UnsignedByte::class.java,
        Short::class.java,
        UnsignedShort::class.java,
        Int::class.java,
        UnsignedInteger::class.java,
        Long::class.java,
        UnsignedLong::class.java,
        Float::class.java,
        Double::class.java,
        Decimal32::class.java,
        Decimal64::class.java,
        Decimal128::class.java,
        Date::class.java,
        UUID::class.java,
        ByteArray::class.java,
        String::class.java,
        Symbol::class.java
)
