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
        val typeParameters = when (identifier) {
            is TypeIdentifier.Parameterised -> identifier.parameters.map { it.interpretIdentifier(notationLookup) }
            else -> emptyList()
        }
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