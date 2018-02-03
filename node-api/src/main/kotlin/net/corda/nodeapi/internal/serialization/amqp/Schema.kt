package net.corda.nodeapi.internal.serialization.amqp

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.toBase64
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.codec.DescribedTypeConstructor
import java.io.NotSerializableException
import java.lang.reflect.*
import java.util.*
import net.corda.nodeapi.internal.serialization.carpenter.Field as CarpenterField
import net.corda.nodeapi.internal.serialization.carpenter.Schema as CarpenterSchema

const val DESCRIPTOR_DOMAIN: String = "net.corda"

// "corda" + majorVersionByte + minorVersionMSB + minorVersionLSB
val AmqpHeaderV1_0: OpaqueBytes = OpaqueBytes("corda\u0001\u0000\u0000".toByteArray())

/**
 * This and the classes below are OO representations of the AMQP XML schema described in the specification. Their
 * [toString] representations generate the associated XML form.
 */
data class Schema(val types: List<TypeNotation>) : DescribedType {
    companion object : DescribedTypeConstructor<Schema> {
        val DESCRIPTOR = AMQPDescriptorRegistry.SCHEMA.amqpDescriptor

        fun get(obj: Any): Schema {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            return newInstance(listOf((list[0] as List<*>).map { TypeNotation.get(it!!) }))
        }

        override fun getTypeClass(): Class<*> = Schema::class.java

        override fun newInstance(described: Any?): Schema {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")
            return Schema(uncheckedCast(list[0]))
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(types)

    override fun toString(): String = types.joinToString("\n")
}

data class Descriptor(val name: Symbol?, val code: UnsignedLong? = null) : DescribedType {
    constructor(name: String?) : this(Symbol.valueOf(name))

    companion object : DescribedTypeConstructor<Descriptor> {
        val DESCRIPTOR = AMQPDescriptorRegistry.OBJECT_DESCRIPTOR.amqpDescriptor

        fun get(obj: Any): Descriptor {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            return newInstance(describedType.described)
        }

        override fun getTypeClass(): Class<*> = Descriptor::class.java

        override fun newInstance(described: Any?): Descriptor {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")
            return Descriptor(list[0] as? Symbol, list[1] as? UnsignedLong)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, code)

    override fun toString(): String {
        val sb = StringBuilder("<descriptor")
        if (name != null) {
            sb.append(" name=\"$name\"")
        }
        if (code != null) {
            val code = String.format("0x%08x:0x%08x", code.toLong().shr(32), code.toLong().and(0xffff))
            sb.append(" code=\"$code\"")
        }
        sb.append("/>")
        return sb.toString()
    }
}

data class Field(val name: String, val type: String, val requires: List<String>, val default: String?, val label: String?, val mandatory: Boolean, val multiple: Boolean) : DescribedType {
    companion object : DescribedTypeConstructor<Field> {
        val DESCRIPTOR = AMQPDescriptorRegistry.FIELD.amqpDescriptor

        fun get(obj: Any): Field {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            return newInstance(describedType.described)
        }

        override fun getTypeClass(): Class<*> = Field::class.java

        override fun newInstance(described: Any?): Field {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")
            return Field(list[0] as String, list[1] as String, uncheckedCast(list[2]), list[3] as? String, list[4] as? String, list[5] as Boolean, list[6] as Boolean)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, type, requires, default, label, mandatory, multiple)

    override fun toString(): String {
        val sb = StringBuilder("<field name=\"$name\" type=\"$type\" mandatory=\"$mandatory\" multiple=\"$multiple\"")
        if (requires.isNotEmpty()) {
            sb.append(" requires=\"")
            sb.append(requires.joinToString(","))
            sb.append("\"")
        }
        if (default != null) {
            sb.append(" default=\"$default\"")
        }
        if (!label.isNullOrBlank()) {
            sb.append(" label=\"$label\"")
        }
        sb.append("/>")
        return sb.toString()
    }
}

sealed class TypeNotation : DescribedType {
    companion object {
        fun get(obj: Any): TypeNotation {
            val describedType = obj as DescribedType
            return when (describedType.descriptor) {
                CompositeType.DESCRIPTOR -> CompositeType.get(describedType)
                RestrictedType.DESCRIPTOR -> RestrictedType.get(describedType)
                else -> throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
        }
    }

    abstract val name: String
    abstract val label: String?
    abstract val provides: List<String>
    abstract val descriptor: Descriptor
}

data class CompositeType(override val name: String, override val label: String?, override val provides: List<String>, override val descriptor: Descriptor, val fields: List<Field>) : TypeNotation() {
    companion object : DescribedTypeConstructor<CompositeType> {
        val DESCRIPTOR = AMQPDescriptorRegistry.COMPOSITE_TYPE.amqpDescriptor

        fun get(describedType: DescribedType): CompositeType {
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            return newInstance(listOf(list[0], list[1], list[2], Descriptor.get(list[3]!!), (list[4] as List<*>).map { Field.get(it!!) }))
        }

        override fun getTypeClass(): Class<*> = CompositeType::class.java

        override fun newInstance(described: Any?): CompositeType {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")
            return CompositeType(list[0] as String, list[1] as? String, uncheckedCast(list[2]), list[3] as Descriptor, uncheckedCast(list[4]))
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, label, provides, descriptor, fields)

    override fun toString(): String {
        val sb = StringBuilder("<type class=\"composite\" name=\"$name\"")
        if (!label.isNullOrBlank()) {
            sb.append(" label=\"$label\"")
        }
        if (provides.isNotEmpty()) {
            sb.append(" provides=\"")
            sb.append(provides.joinToString(","))
            sb.append("\"")
        }
        sb.append(">\n")
        sb.append("  $descriptor\n")
        for (field in fields) {
            sb.append("  $field\n")
        }
        sb.append("</type>")
        return sb.toString()
    }
}

data class RestrictedType(override val name: String,
                          override val label: String?,
                          override val provides: List<String>,
                          val source: String,
                          override val descriptor: Descriptor,
                          val choices: List<Choice>) : TypeNotation() {
    companion object : DescribedTypeConstructor<RestrictedType> {
        val DESCRIPTOR = AMQPDescriptorRegistry.RESTRICTED_TYPE.amqpDescriptor

        fun get(describedType: DescribedType): RestrictedType {
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            return newInstance(listOf(list[0], list[1], list[2], list[3], Descriptor.get(list[4]!!), (list[5] as List<*>).map { Choice.get(it!!) }))
        }

        override fun getTypeClass(): Class<*> = RestrictedType::class.java

        override fun newInstance(described: Any?): RestrictedType {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")
            return RestrictedType(list[0] as String, list[1] as? String, uncheckedCast(list[2]), list[3] as String, list[4] as Descriptor, uncheckedCast(list[5]))
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, label, provides, source, descriptor, choices)

    override fun toString(): String {
        val sb = StringBuilder("<type class=\"restricted\" name=\"$name\"")
        if (!label.isNullOrBlank()) {
            sb.append(" label=\"$label\"")
        }
        sb.append(" source=\"$source\"")
        if (provides.isNotEmpty()) {
            sb.append(" provides=\"")
            sb.append(provides.joinToString(","))
            sb.append("\"")
        }
        sb.append(">\n")
        sb.append("  $descriptor\n")
        choices.forEach {
            sb.append("  $it\n")
        }
        sb.append("</type>")
        return sb.toString()
    }
}

data class Choice(val name: String, val value: String) : DescribedType {
    companion object : DescribedTypeConstructor<Choice> {
        val DESCRIPTOR = AMQPDescriptorRegistry.CHOICE.amqpDescriptor

        fun get(obj: Any): Choice {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            return newInstance(describedType.described)
        }

        override fun getTypeClass(): Class<*> = Choice::class.java

        override fun newInstance(described: Any?): Choice {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")
            return Choice(list[0] as String, list[1] as String)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, value)

    override fun toString(): String {
        return "<choice name=\"$name\" value=\"$value\"/>"
    }
}

data class ReferencedObject(private val refCounter: Int) : DescribedType {
    companion object : DescribedTypeConstructor<ReferencedObject> {
        val DESCRIPTOR = AMQPDescriptorRegistry.REFERENCED_OBJECT.amqpDescriptor

        fun get(obj: Any): ReferencedObject {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            return newInstance(describedType.described)
        }

        override fun getTypeClass(): Class<*> = ReferencedObject::class.java

        override fun newInstance(described: Any?): ReferencedObject {
            val unInt = described as? UnsignedInteger ?: throw IllegalStateException("Was expecting an UnsignedInteger")
            return ReferencedObject(unInt.toInt())
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): UnsignedInteger = UnsignedInteger(refCounter)

    override fun toString(): String = "<refObject refCounter=$refCounter/>"
}

private val ARRAY_HASH: String = "Array = true"
private val ENUM_HASH: String = "Enum = true"
private val ALREADY_SEEN_HASH: String = "Already seen = true"
private val NULLABLE_HASH: String = "Nullable = true"
private val NOT_NULLABLE_HASH: String = "Nullable = false"
private val ANY_TYPE_HASH: String = "Any type = true"
private val TYPE_VARIABLE_HASH: String = "Type variable = true"
private val WILDCARD_TYPE_HASH: String = "Wild card = true"

private val logger by lazy { loggerFor<Schema>() }

/**
 * The method generates a fingerprint for a given JVM [Type] that should be unique to the schema representation.
 * Thus it only takes into account properties and types and only supports the same object graph subset as the overall
 * serialization code.
 *
 * The idea being that even for two classes that share the same name but differ in a minor way, the fingerprint will be
 * different.
 */
// TODO: write tests
internal fun fingerprintForType(type: Type, factory: SerializerFactory): String {
    return fingerprintForType(type, null, HashSet(), Hashing.murmur3_128().newHasher(), factory).hash().asBytes().toBase64()
}

internal fun fingerprintForDescriptors(vararg typeDescriptors: String): String {
    val hasher = Hashing.murmur3_128().newHasher()
    for (typeDescriptor in typeDescriptors) {
        hasher.putUnencodedChars(typeDescriptor)
    }
    return hasher.hash().asBytes().toBase64()
}

private fun Hasher.fingerprintWithCustomSerializerOrElse(factory: SerializerFactory, clazz: Class<*>, declaredType: Type, block: () -> Hasher): Hasher {
    // Need to check if a custom serializer is applicable
    val customSerializer = factory.findCustomSerializer(clazz, declaredType)
    return if (customSerializer != null) {
        putUnencodedChars(customSerializer.typeDescriptor)
    } else {
        block()
    }
}

// This method concatenates various elements of the types recursively as unencoded strings into the hasher, effectively
// creating a unique string for a type which we then hash in the calling function above.
private fun fingerprintForType(type: Type, contextType: Type?, alreadySeen: MutableSet<Type>,
                               hasher: Hasher, factory: SerializerFactory, debugIndent: Int = 1): Hasher {
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
                        hasher.fingerprintWithCustomSerializerOrElse(factory, clazz, type) {
                            fingerprintForObject(type, type, alreadySeen, hasher, factory, debugIndent+1)
                        }
                    }

                    // ... and concatenate the type data for each parameter type.
                    type.actualTypeArguments.fold(startingHash) { orig, paramType ->
                        fingerprintForType(paramType, type, alreadySeen, orig, factory, debugIndent+1)
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
                        fingerprintForType(type.componentType, contextType, alreadySeen, hasher, factory, debugIndent+1)
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
                        hasher.fingerprintWithCustomSerializerOrElse(factory, type, type) {
                            if (type.kotlin.objectInstance != null) {
                                // TODO: name collision is too likely for kotlin objects, we need to introduce some reference
                                // to the CorDapp but maybe reference to the JAR in the short term.
                                hasher.putUnencodedChars(type.name)
                            } else {
                                fingerprintForObject(type, type, alreadySeen, hasher, factory, debugIndent+1)
                            }
                        }
                    }
                }
            // Hash the element type + some array hash
                is GenericArrayType -> {
                    fingerprintForType(type.genericComponentType, contextType, alreadySeen,
                            hasher, factory, debugIndent+1).putUnencodedChars(ARRAY_HASH)
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

private fun isCollectionOrMap(type: Class<*>) = (Collection::class.java.isAssignableFrom(type) || Map::class.java.isAssignableFrom(type)) &&
        !EnumSet::class.java.isAssignableFrom(type)

private fun fingerprintForObject(
        type: Type,
        contextType: Type?,
        alreadySeen: MutableSet<Type>,
        hasher: Hasher,
        factory: SerializerFactory,
        debugIndent: Int = 0): Hasher {
    // Hash the class + properties + interfaces
    val name = type.asClass()?.name ?: throw NotSerializableException("Expected only Class or ParameterizedType but found $type")

    propertiesForSerialization(constructorForDeserialization(type), contextType ?: type, factory)
            .serializationOrder
            .fold(hasher.putUnencodedChars(name)) { orig, prop ->
                fingerprintForType(prop.getter.resolvedType, type, alreadySeen, orig, factory, debugIndent+1)
                        .putUnencodedChars(prop.getter.name)
                        .putUnencodedChars(if (prop.getter.mandatory) NOT_NULLABLE_HASH else NULLABLE_HASH)
            }
    interfacesForSerialization(type, factory).map { fingerprintForType(it, type, alreadySeen, hasher, factory, debugIndent+1) }
    return hasher
}
