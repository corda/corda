package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.RemotePropertyInformation
import net.corda.serialization.internal.model.RemoteTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import java.io.NotSerializableException

class AMQPRemoteTypeModel {

    fun interpret(typeDescriptor: String, schema: Schema): RemoteTypeInformation {
        val notationLookup = schema.types.associateBy { it.name.typeIdentifier }
        val target = schema.types.find { it.descriptor.name?.toString() == typeDescriptor } ?:
                throw NotSerializableException("Type descriptor '$typeDescriptor' not found in schema")
        return target.name.typeIdentifier.interpretIdentifier(notationLookup, emptySet())
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

        return if (isInterface) RemoteTypeInformation.AnInterface(typeDescriptor, identifier, interfaces, typeParameters)
        else RemoteTypeInformation.Composable(typeDescriptor, identifier, properties, interfaces, typeParameters)
    }

    private fun TypeIdentifier.interpretTypeParameters(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): List<RemoteTypeInformation> = when (this) {
            is TypeIdentifier.Parameterised -> parameters.map { it.interpretIdentifier(notationLookup, seen) }
            else -> emptyList()
        }

    private fun RestrictedType.interpretRestricted(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
        when (identifier) {
            is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(identifier, identifier.interpretTypeParameters(notationLookup, seen))
            is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(identifier, identifier.componentType.interpretIdentifier(notationLookup, seen))
            else -> RemoteTypeInformation.AnEnum(descriptor.name.toString(), identifier, choices.map { it.name })
        }

    private fun Field.interpret(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): Pair<String, RemotePropertyInformation> {
        val identifier = type.typeIdentifier
        val fieldTypeIdentifier = if (identifier == TypeIdentifier.TopType && !requires.isEmpty()) requires[0].typeIdentifier else identifier
        val fieldType = fieldTypeIdentifier.interpretIdentifier(notationLookup, seen)
        return name to RemotePropertyInformation(
                fieldType,
                mandatory)
    }

    private fun TypeIdentifier.interpretNoNotation(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
            when (this) {
                is TypeIdentifier.TopType -> RemoteTypeInformation.Any
                is TypeIdentifier.UnknownType -> RemoteTypeInformation.Unknown
                is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(this, componentType.interpretIdentifier(notationLookup, seen))
                is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(this, parameters.map { it.interpretIdentifier(notationLookup, seen) })
                else -> RemoteTypeInformation.Unparameterised(this)
            }

    private val String.typeIdentifier get(): TypeIdentifier = AMQPTypeIdentifierParser.parse(this)

}