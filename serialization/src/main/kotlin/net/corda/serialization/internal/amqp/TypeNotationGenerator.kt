package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.LocalPropertyInformation
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import java.io.NotSerializableException

class TypeNotationGenerator(private val factory: LocalSerializerFactory) {

    fun getTypeNotation(typeInformation: LocalTypeInformation) = when(typeInformation) {
        is LocalTypeInformation.AnInterface -> typeInformation.getTypeNotation()
        is LocalTypeInformation.Composable -> typeInformation.getTypeNotation()
        is LocalTypeInformation.Abstract -> typeInformation.getTypeNotation()
        else -> throw NotSerializableException("Cannot generate type notation for $typeInformation")
    }

    private val LocalTypeInformation.amqpTypeName get() = SerializerFactory.nameForType(observedType)

    private fun LocalTypeInformation.AnInterface.getTypeNotation(): CompositeType =
            makeCompositeType(
                    (sequenceOf(this) + interfaces.asSequence()).toList(),
                    properties)

    private fun LocalTypeInformation.Composable.getTypeNotation(): CompositeType =
            makeCompositeType(interfaces, properties)

    private fun LocalTypeInformation.Abstract.getTypeNotation(): CompositeType =
            makeCompositeType(interfaces, properties)

    private fun LocalTypeInformation.makeCompositeType(
            interfaces: List<LocalTypeInformation>,
            properties: Map<String, LocalPropertyInformation>): CompositeType {
        val provides = interfaces.map { it.amqpTypeName }
        val fields = properties.map { (name, property) ->
            property.getField(name)
        }

        return CompositeType(
                amqpTypeName,
                null,
                provides,
                Descriptor(factory.createDescriptor(observedType)),
                fields)
    }

    private fun LocalPropertyInformation.getField(name: String): Field {
        val requires = when (type) {
            is LocalTypeInformation.AnInterface -> listOf(type.amqpTypeName)
            else -> emptyList()
        }

        val defaultValue: String? = defaultValues[type.typeIdentifier]

        return Field(name, type.amqpTypeName, requires, defaultValue, null, isMandatory, false)
    }

    companion object {
        private val defaultValues = sequenceOf(
                Boolean::class to "false",
                Byte::class to "0",
                Int::class to "0",
                Char::class to "&#0",
                Short::class to "0",
                Long::class to "0",
                Float::class to "0",
                Double::class to "0").associate { (type, value) ->
            TypeIdentifier.forClass(type.javaPrimitiveType!!) to value
        }
    }
}