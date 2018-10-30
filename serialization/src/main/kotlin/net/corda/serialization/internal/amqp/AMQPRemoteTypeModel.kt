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
            when (this) {
                is CompositeType -> interpretComposite(identifier, notationLookup)
                is RestrictedType -> interpretRestricted(identifier, notationLookup)
            }

    private fun CompositeType.interpretComposite(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation {
        val properties = fields.asSequence().map { it.interpret(notationLookup) }.toMap()
        val typeParameters = identifier.interpretTypeParameters(notationLookup)
        val interfaceIdentifiers = provides.map { name -> name.typeIdentifier }
        val isInterface = identifier in interfaceIdentifiers
        val interfaces = interfaceIdentifiers.mapNotNull { interfaceIdentifier ->
            if (interfaceIdentifier == identifier) null
            else interfaceIdentifier.interpretIdentifier(notationLookup)
        }

        val typeDescriptor = descriptor.name?.toString()
                ?: throw IllegalStateException("Composite type $this has no type descriptor")

        return if (isInterface) RemoteTypeInformation.AnInterface(typeDescriptor, identifier, interfaces, typeParameters)
        else RemoteTypeInformation.APojo(typeDescriptor, identifier, properties, interfaces, typeParameters)
    }

    private fun TypeIdentifier.interpretTypeParameters(notationLookup: Map<TypeIdentifier, TypeNotation>): List<RemoteTypeInformation> = when (this) {
            is TypeIdentifier.Parameterised -> parameters.map { it.interpretIdentifier(notationLookup) }
            else -> emptyList()
        }

    private fun RestrictedType.interpretRestricted(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation =
        when (identifier) {
            is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(identifier, identifier.interpretTypeParameters(notationLookup))
            is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(identifier, identifier.componentType.interpretIdentifier(notationLookup))
            else -> RemoteTypeInformation.AnEnum(descriptor.name.toString(), identifier, choices.map { it.name })
        }

    private fun Field.interpret(notationLookup: Map<TypeIdentifier, TypeNotation>): Pair<String, RemotePropertyInformation> {
        val identifier = type.typeIdentifier
        val fieldTypeIdentifier = if (identifier == TypeIdentifier.TopType && !requires.isEmpty()) requires[0].typeIdentifier else identifier
        val fieldType = fieldTypeIdentifier.interpretIdentifier(notationLookup)
        return name to RemotePropertyInformation(
                fieldType,
                mandatory)
    }

    private fun TypeIdentifier.interpretNoNotation(notationLookup: Map<TypeIdentifier, TypeNotation>): RemoteTypeInformation =
            when (this) {
                is TypeIdentifier.TopType -> RemoteTypeInformation.Any
                is TypeIdentifier.UnknownType -> RemoteTypeInformation.Unknown
                is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(this, componentType.interpretIdentifier(notationLookup))
                is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(this, parameters.map { it.interpretIdentifier(notationLookup) })
                else -> RemoteTypeInformation.Unparameterised(this)
            }

    private val String.typeIdentifier get(): TypeIdentifier = AMQPTypeIdentifierParser.parse(this)

}