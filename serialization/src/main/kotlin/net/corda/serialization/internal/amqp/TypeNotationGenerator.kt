package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.LocalPropertyInformation
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.Symbol
import java.io.NotSerializableException

object TypeNotationGenerator {

    fun getTypeNotation(typeInformation: LocalTypeInformation, typeDescriptor: Symbol) = when(typeInformation) {
        is LocalTypeInformation.AnInterface -> typeInformation.getTypeNotation(typeDescriptor)
        is LocalTypeInformation.Composable -> typeInformation.getTypeNotation(typeDescriptor)
        is LocalTypeInformation.Abstract -> typeInformation.getTypeNotation(typeDescriptor)
        else -> throw NotSerializableException("Cannot generate type notation for $typeInformation")
    }

    private val LocalTypeInformation.amqpTypeName get() = AMQPTypeIdentifiers.nameForType(typeIdentifier)

    private fun LocalTypeInformation.AnInterface.getTypeNotation(typeDescriptor: Symbol): CompositeType =
            makeCompositeType(
                    (sequenceOf(this) + interfaces.asSequence()).toList(),
                    properties,
                    typeDescriptor)

    private fun LocalTypeInformation.Composable.getTypeNotation(typeDescriptor: Symbol): CompositeType =
            makeCompositeType(interfaces, properties, typeDescriptor)

    private fun LocalTypeInformation.Abstract.getTypeNotation(typeDescriptor: Symbol): CompositeType =
            makeCompositeType(interfaces, properties, typeDescriptor)

    private fun LocalTypeInformation.makeCompositeType(
            interfaces: List<LocalTypeInformation>,
            properties: Map<String, LocalPropertyInformation>,
            typeDescriptor: Symbol): CompositeType {
        val provides = interfaces.map { it.amqpTypeName }
        val fields = properties.map { (name, property) ->
            property.getField(name)
        }

        return CompositeType(
                amqpTypeName,
                null,
                provides,
                Descriptor(typeDescriptor),
                fields)
    }

    private fun LocalPropertyInformation.getField(name: String): Field {
        val (typeName, requires) = when(type) {
            is LocalTypeInformation.AnInterface,
            is LocalTypeInformation.ACollection,
            is LocalTypeInformation.AMap -> "*" to listOf(type.amqpTypeName)
            else -> type.amqpTypeName to emptyList()
        }

        val defaultValue: String? = defaultValues[type.typeIdentifier]

        return Field(name, typeName, requires, defaultValue, null, isMandatory, false)
    }

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