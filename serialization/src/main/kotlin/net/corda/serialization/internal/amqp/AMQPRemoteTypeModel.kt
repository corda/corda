package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

/**
 * This class converts AMQP [Schema]s into [Map]s of [RemoteTypeInformation] by [TypeDescriptor].
 */
class AMQPRemoteTypeModel {
    
    fun interpret(schema: Schema): Map<TypeDescriptor, RemoteTypeInformation> {
        val notationLookup = schema.types.associateBy { it.name.typeIdentifier }
        val byTypeDescriptor = schema.types.associateBy { it.descriptor.name.toString() }
        return byTypeDescriptor.mapValues { (_, v) -> v.name.typeIdentifier.interpretIdentifier(notationLookup, emptySet()) }
    }

    private fun TypeIdentifier.interpretIdentifier(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
            if (this in seen) RemoteTypeInformation.Cycle(this) { interpretIdentifier(notationLookup, emptySet()) }
            else notationLookup[this]?.interpretNotation(this, notationLookup, seen + this) ?: interpretNoNotation(notationLookup, seen + this)

    private fun TypeNotation.interpretNotation(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
            when (this) {
                is CompositeType -> interpretComposite(identifier, notationLookup, seen)
                is RestrictedType -> interpretRestricted(identifier, notationLookup, seen)
            }

    private fun CompositeType.interpretComposite(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation {
        val properties = fields.asSequence().map { it.interpret(notationLookup, seen) }.toMap()
        val typeParameters = identifier.interpretTypeParameters(notationLookup, seen)
        val interfaceIdentifiers = provides.map { name -> name.typeIdentifier }
        val isInterface = identifier in interfaceIdentifiers
        val interfaces = interfaceIdentifiers.mapNotNull { interfaceIdentifier ->
            if (interfaceIdentifier == identifier) null
            else interfaceIdentifier.interpretIdentifier(notationLookup, seen)
        }

        val typeDescriptor = descriptor.name?.toString()
                ?: throw IllegalStateException("Composite type $this has no type descriptor")

        return if (isInterface) RemoteTypeInformation.AnInterface(typeDescriptor, identifier, properties, interfaces, typeParameters)
        else RemoteTypeInformation.Composable(typeDescriptor, identifier, properties, interfaces, typeParameters)
    }

    private fun TypeIdentifier.interpretTypeParameters(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): List<RemoteTypeInformation> = when (this) {
            is TypeIdentifier.Parameterised -> parameters.map { it.interpretIdentifier(notationLookup, seen) }
            else -> emptyList()
        }

    private fun RestrictedType.interpretRestricted(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
        when (identifier) {
            is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(typeDescriptor, identifier, identifier.interpretTypeParameters(notationLookup, seen))
            is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(typeDescriptor, identifier, identifier.componentType.interpretIdentifier(notationLookup, seen))
            else -> RemoteTypeInformation.AnEnum(typeDescriptor, identifier, choices.map { it.name })
        }

    private fun Field.interpret(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): Pair<String, RemotePropertyInformation> {
        val identifier = type.typeIdentifier
        val fieldTypeIdentifier = if (identifier == TypeIdentifier.TopType && !requires.isEmpty()) requires[0].typeIdentifier else identifier
        val fieldType = fieldTypeIdentifier.forcePrimitive(mandatory).interpretIdentifier(notationLookup, seen)
        return name to RemotePropertyInformation(
                fieldType,
                mandatory)
    }

    private val TypeNotation.typeDescriptor: String get() = descriptor.name?.toString() ?: ""

    private fun TypeIdentifier.interpretNoNotation(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
            when (this) {
                is TypeIdentifier.TopType -> RemoteTypeInformation.Top
                is TypeIdentifier.UnknownType -> RemoteTypeInformation.Unknown
                is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(name,this, componentType.interpretIdentifier(notationLookup, seen))
                is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(name, this, parameters.map { it.interpretIdentifier(notationLookup, seen) })
                else -> RemoteTypeInformation.Unparameterised(name, this)
            }

    private val String.typeIdentifier get(): TypeIdentifier = AMQPTypeIdentifierParser.parse(this)

    companion object {
        private fun TypeIdentifier.forcePrimitive(mandatory: Boolean) =
                if (mandatory) primitives[this] ?: this
                else this

        private val primitives = sequenceOf(
                Boolean::class,
                Byte::class,
                Char::class,
                Int::class,
                Short::class,
                Long::class,
                Float::class,
                Double::class).associate {
            TypeIdentifier.forClass(it.javaObjectType) to TypeIdentifier.forClass(it.javaPrimitiveType!!)
        }
    }
}