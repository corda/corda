package net.corda.serialization.internal.model

import com.google.common.hash.Hashing
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.toBase64
import net.corda.serialization.internal.amqp.*
import java.io.NotSerializableException

/**
 * A fingerprinter that fingerprints [LocalTypeInformation].
 */
interface FingerPrinter {
    /**
     * Traverse the provided [LocalTypeInformation] graph and emit a short fingerprint string uniquely representing
     * the shape of that graph.
     *
     * @param typeInformation The [LocalTypeInformation] to fingerprint.
     */
    fun fingerprint(typeInformation: LocalTypeInformation): String
}

/**
 * A [FingerPrinter] that consults a [CustomTypeDescriptorLookup] to obtain type descriptors for
 * types that do not need to be traversed to calculate their fingerprint information. (Usually these will be the type
 * descriptors supplied by custom serializers).
 *
 * @param customTypeDescriptorLookup The [CustomTypeDescriptorLookup] to use to obtain custom type descriptors for
 * selected types.
 */
class TypeModellingFingerPrinter(
        private val customTypeDescriptorLookup: CustomSerializerRegistry,
        private val debugEnabled: Boolean = false) : FingerPrinter {

    private val cache: MutableMap<TypeIdentifier, String> = DefaultCacheProvider.createCache()

    override fun fingerprint(typeInformation: LocalTypeInformation): String =
            cache.computeIfAbsent(typeInformation.typeIdentifier) {
                FingerPrintingState(
                        customTypeDescriptorLookup,
                        FingerprintWriter(debugEnabled)).fingerprint(typeInformation)
            }
}

/**
 * Wrapper for the [Hasher] we use to generate fingerprints, providing methods for writing various kinds of content
 * into the hash.
 */
internal class FingerprintWriter(debugEnabled: Boolean = false) {

    companion object {
        private const val ARRAY_HASH: String = "Array = true"
        private const val ENUM_HASH: String = "Enum = true"
        private const val ALREADY_SEEN_HASH: String = "Already seen = true"
        private const val NULLABLE_HASH: String = "Nullable = true"
        private const val NOT_NULLABLE_HASH: String = "Nullable = false"
        private const val ANY_TYPE_HASH: String = "Any type = true"

        private val logger = contextLogger()
    }

    private val debugBuffer: StringBuilder? = if (debugEnabled) StringBuilder() else null
    private var hasher = Hashing.murmur3_128().newHasher() // FIXUP: remove dependency on Guava Hasher

    fun write(chars: CharSequence) = append(chars)
    fun write(words: List<CharSequence>) = append(words.joinToString())
    fun writeAlreadySeen() = append(ALREADY_SEEN_HASH)
    fun writeEnum() = append(ENUM_HASH)
    fun writeArray() = append(ARRAY_HASH)
    fun writeNullable() = append(NULLABLE_HASH)
    fun writeNotNullable() = append(NOT_NULLABLE_HASH)
    fun writeUnknown() = append(ANY_TYPE_HASH)
    fun writeTop() = append(Any::class.java.name)

    private fun append(chars: CharSequence) = apply {
        debugBuffer?.append(chars)
        hasher = hasher.putUnencodedChars(chars)
    }

    val fingerprint: String by lazy {
        val fingerprint = hasher.hash().asBytes().toBase64()
        if (debugBuffer != null) logger.info("$fingerprint from $debugBuffer")
        fingerprint
    }
}

/**
 * Representation of the current state of fingerprinting, which keeps track of which types have already been visited
 * during fingerprinting.
 */
private class FingerPrintingState(
        private val customSerializerRegistry: CustomSerializerRegistry,
        private val writer: FingerprintWriter) {

    companion object {
        private var CHARACTER_TYPE = LocalTypeInformation.Atomic(
                Character::class.java,
                TypeIdentifier.forClass(Character::class.java))
    }

    private val typesSeen: MutableSet<TypeIdentifier> = mutableSetOf()

    /**
     * Fingerprint the type recursively, and return the encoded fingerprint written into the hasher.
     */
    fun fingerprint(type: LocalTypeInformation): String =
            fingerprintType(type).writer.fingerprint

    // This method concatenates various elements of the types recursively as unencoded strings into the hasher,
    // effectively creating a unique string for a type which we then hash in the calling function above.
    private fun fingerprintType(type: LocalTypeInformation): FingerPrintingState = apply {
        // Don't go round in circles.
        when {
            hasSeen(type.typeIdentifier) -> writer.writeAlreadySeen()
            type is LocalTypeInformation.Cycle -> fingerprintType(type.follow)
            else -> ifThrowsAppend({ type.observedType.typeName }, {
                typesSeen.add(type.typeIdentifier)
                fingerprintNewType(type)
            })
        }
    }

    // For a type we haven't seen before, determine the correct path depending on the type of type it is.
    private fun fingerprintNewType(type: LocalTypeInformation) = apply {
        when (type) {
            is LocalTypeInformation.Cycle ->
                throw IllegalStateException("Cyclic references must be dereferenced before fingerprinting")
            is LocalTypeInformation.Unknown -> writer.writeUnknown()
            is LocalTypeInformation.Top -> writer.writeTop()
            is LocalTypeInformation.AnArray -> {
                fingerprintType(type.componentType)
                writer.writeArray()
            }
            is LocalTypeInformation.ACollection -> fingerprintCollection(type)
            is LocalTypeInformation.AMap -> fingerprintMap(type)
            is LocalTypeInformation.Atomic -> fingerprintName(type)
            is LocalTypeInformation.Opaque -> fingerprintOpaque(type)
            is LocalTypeInformation.AnEnum -> fingerprintEnum(type)
            is LocalTypeInformation.AnInterface -> fingerprintInterface(type)
            is LocalTypeInformation.Abstract -> fingerprintAbstract(type)
            is LocalTypeInformation.Singleton -> fingerprintName(type)
            is LocalTypeInformation.Composable -> fingerprintComposable(type)
            is LocalTypeInformation.NonComposable -> fingerprintNonComposable(type)
        }
    }

    private fun fingerprintCollection(type: LocalTypeInformation.ACollection) {
        fingerprintName(type)
        fingerprintType(type.elementType)
    }

    private fun fingerprintMap(type: LocalTypeInformation.AMap) {
        fingerprintName(type)
        fingerprintType(type.keyType)
        fingerprintType(type.valueType)
    }

    private fun fingerprintOpaque(type: LocalTypeInformation) =
            fingerprintWithCustomSerializerOrElse(type) {
                fingerprintName(type)
            }

    private fun fingerprintInterface(type: LocalTypeInformation.AnInterface) =
            fingerprintWithCustomSerializerOrElse(type) {
                fingerprintName(type)
                writer.writeAlreadySeen() // FIXUP: this replicates the behaviour of the old fingerprinter for compatibility reasons.
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

    private fun fingerprintNonComposable(type: LocalTypeInformation.NonComposable) =
            fingerprintWithCustomSerializerOrElse(type) {
                fingerprintName(type)
                fingerprintProperties(type.properties)
                fingerprintInterfaces(type.interfaces)
                fingerprintTypeParameters(type.typeParameters)
            }

    private fun fingerprintComposable(type: LocalTypeInformation.Composable) =
            fingerprintWithCustomSerializerOrElse(type) {
                fingerprintName(type)
                fingerprintProperties(type.properties)
                fingerprintInterfaces(type.interfaces)
                fingerprintTypeParameters(type.typeParameters)
            }

    private fun fingerprintName(type: LocalTypeInformation) {
        val identifier = type.typeIdentifier
        when (identifier) {
            is TypeIdentifier.ArrayOf -> writer.write(identifier.componentType.name).writeArray()
            else -> writer.write(identifier.name)
        }
    }

    private fun fingerprintTypeParameters(typeParameters: List<LocalTypeInformation>) =
            typeParameters.forEach { fingerprintType(it) }

    private fun fingerprintProperties(properties: Map<String, LocalPropertyInformation>) =
            properties.asSequence().sortedBy { it.key }.forEach { (propertyName, propertyType) ->
                val (neverMandatory, adjustedType) = adjustType(propertyType.type)
                fingerprintType(adjustedType)
                writer.write(propertyName)
                if (propertyType.isMandatory && !neverMandatory) writer.writeNotNullable() else writer.writeNullable()
            }

    // Compensate for the serialisation framework's forcing of char to Character
    private fun adjustType(propertyType: LocalTypeInformation): Pair<Boolean, LocalTypeInformation> =
            if (propertyType.typeIdentifier.name == "char") true to CHARACTER_TYPE else false to propertyType

    private fun fingerprintInterfaces(interfaces: List<LocalTypeInformation>) =
            interfaces.forEach { fingerprintType(it) }

    // ensures any change to the enum (adding constants) will trigger the need for evolution
    private fun fingerprintEnum(type: LocalTypeInformation.AnEnum) {
        writer.write(type.members).write(type.typeIdentifier.name).writeEnum()
    }

    // Give any custom serializers loaded into the factory the chance to supply their own type-descriptors
    private fun fingerprintWithCustomSerializerOrElse(type: LocalTypeInformation, defaultAction: () -> Unit) {
        val customTypeDescriptor = customSerializerRegistry.findCustomSerializer(type.observedType.asClass(), type.observedType)?.typeDescriptor?.toString()
        if (customTypeDescriptor != null) writer.write(customTypeDescriptor)
        else defaultAction()
    }

    // Test whether we are in a state in which we have already seen the given type.
    //
    // We don't include Example<?> and Example<T> where type is ? or T in this otherwise we
    // generate different fingerprints for class Outer<T>(val a: Inner<T>) when serialising
    // and deserializing (assuming deserialization is occurring in a factory that didn't
    // serialise the object in the  first place (and thus the cache lookup fails). This is also
    // true of Any, where we need  Example<A, B> and Example<?, ?> to have the same fingerprint
    private fun hasSeen(type: TypeIdentifier) = (type in typesSeen)
            && (type != TypeIdentifier.UnknownType)
}
