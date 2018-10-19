package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.RemotePropertyInformation
import net.corda.serialization.internal.model.RemoteTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier

class AMQPRemoteTypeModel {

    fun interpret(name: String, schema: Schema): RemoteTypeInformation {
        val notationLookup = schema.types.associateBy { it.name.typeIdentifier }
        return name.typeIdentifier.interpretIdentifier(notationLookup)
    }

    private fun TypeIdentifier.interpretIdentifier(notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation =
        notationLookup[this]?.interpretNotation(this, notationLookup) ?: interpretNoNotation(notationLookup)

    private fun TypeNotation.interpretNotation(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation =
        when(this) {
            is CompositeType -> interpretComposite(identifier, notationLookup)
            is RestrictedType -> interpretRestricted(identifier, notationLookup)
        }

    private fun CompositeType.interpretComposite(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation {
        val properties = fields.asSequence().map { it.interpret(notationLookup) }.toMap()
        val typeParameters = when(identifier) {
            is TypeIdentifier.Parameterised -> identifier.parameters.map { it.interpretIdentifier(notationLookup) }
            else -> emptyList()
        }
        val interfaceIdentifiers = provides.map { name -> name.typeIdentifier }
        val isInterface = identifier in interfaceIdentifiers
        val interfaces = interfaceIdentifiers.mapNotNull { interfaceIdentifier ->
            if (interfaceIdentifier == identifier) null
            else interfaceIdentifier.interpretIdentifier(notationLookup)
        }

        val typeDescriptor = descriptor.name?.toString() ?: throw IllegalStateException("Composite type $this has no type descriptor")

        return if (isInterface) RemoteTypeInformation.AnInterface(typeDescriptor, identifier, interfaces, typeParameters)
        else RemoteTypeInformation.APojo(typeDescriptor, identifier, properties, interfaces, typeParameters)
    }

    private fun RestrictedType.interpretRestricted(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation {
        throw TODO("Deal with enums")
    }

    private fun Field.interpret(notationLookup: Map<TypeIdentifier, TypeNotation>): Pair<String, RemotePropertyInformation> {
        return name to RemotePropertyInformation(
                type.typeIdentifier.interpretIdentifier(notationLookup),
                mandatory)
    }

    private fun TypeIdentifier.interpretNoNotation(notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation =
        when (this) {
            is TypeIdentifier.Top -> RemoteTypeInformation.Any
            is TypeIdentifier.Unknown -> RemoteTypeInformation.Unknown
            is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(this, componentType.interpretIdentifier(notationLookup))
            is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(this, parameters.map { it.interpretIdentifier(notationLookup) })
            else -> RemoteTypeInformation.Unparameterised(this)
        }

    private val String.typeIdentifier get(): TypeIdentifier = AMQPTypeIdentifierParser.parse(this)

}

object AMQPTypeIdentifierParser {
    fun parse(type: String, substituteAny: String? = null, forcePrimitive: Boolean = false): TypeIdentifier = when {
        type == "*" -> if (substituteAny == null) TypeIdentifier.Top else parse(substituteAny, forcePrimitive = forcePrimitive) ?: TypeIdentifier.Top
        type == "?" -> TypeIdentifier.Unknown
        type.endsWith("[]") -> TypeIdentifier.ArrayOf(parse(type.substring(0, type.lastIndex - 1)))
        type.endsWith("[p]") -> TypeIdentifier.ArrayOf(parse(type.substring(0, type.lastIndex - 2), forcePrimitive = true))
        type in simplified -> if (forcePrimitive) primitives[type]!! else wrapped[type]!!
        type.contains("<") -> TypeIdentifier.Parameterised(type.substringBefore("<"), parseParameters(type.substringAfter("<")))
        else -> TypeIdentifier.Unparameterised(type)
    }

    fun parseParameters(parameterList: String, depth: Int = 0): List<TypeIdentifier> = emptyList()

    private val simplified = mapOf(
            "boolean" to Boolean::class,
            "byte" to Byte::class,
            "char" to Char::class,
            "int" to Int::class,
            "short" to Short::class,
            "long" to Long::class,
            "double" to Double::class,
            "float" to Float::class)

    private val primitives = simplified.mapValues { (_, v) -> TypeIdentifier.forClass(v.javaPrimitiveType!!) }
    private val wrapped = simplified.mapValues { (_, v) -> TypeIdentifier.forClass(v.javaObjectType!!) }
}

/*
        name.endsWith("[]") -> {
            val elementType = typeForName(name.substring(0, name.lastIndex - 1), classloader)
            if (elementType is ParameterizedType || elementType is GenericArrayType) {
                DeserializedGenericArrayType(elementType)
            } else if (elementType is Class<*>) {
                java.lang.reflect.Array.newInstance(elementType, 0).javaClass
            } else {
                throw AMQPNoTypeNotSerializableException("Not able to deserialize array type: $name")
            }
        }
        name.endsWith("[p]") -> // There is no need to handle the ByteArray case as that type is coercible automatically
            // to the binary type and is thus handled by the main serializer and doesn't need a
            // special case for a primitive array of bytes
            when (name) {
                "int[p]" -> IntArray::class.java
                "char[p]" -> CharArray::class.java
                "boolean[p]" -> BooleanArray::class.java
                "float[p]" -> FloatArray::class.java
                "double[p]" -> DoubleArray::class.java
                "short[p]" -> ShortArray::class.java
                "long[p]" -> LongArray::class.java
                else -> throw AMQPNoTypeNotSerializableException("Not able to deserialize array type: $name")
            }
        else -> DeserializedParameterizedType.make(name, classloader)
 */