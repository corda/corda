package net.corda.serialization.internal.model

import net.corda.serialization.internal.amqp.asClass
import net.corda.serialization.internal.carpenter.*
import java.io.NotSerializableException
import java.lang.reflect.Type

/**
 * Constructs [Type]s using [RemoteTypeInformation].
 */
interface RemoteTypeCarpenter {
    fun carpent(typeInformation: RemoteTypeInformation): Type
}

/**
 * A [RemoteTypeCarpenter] that converts [RemoteTypeInformation] into [Schema] objects for the [ClassCarpenter] to use.
 */
class SchemaBuildingRemoteTypeCarpenter(private val carpenter: ClassCarpenter): RemoteTypeCarpenter {

    private val classLoader: ClassLoader get() = carpenter.classloader

    override fun carpent(typeInformation: RemoteTypeInformation): Type {
        try {
            when (typeInformation) {
                is RemoteTypeInformation.AnInterface -> typeInformation.carpentInterface()
                is RemoteTypeInformation.Composable -> typeInformation.carpentComposable()
                is RemoteTypeInformation.AnEnum -> typeInformation.carpentEnum()
                else -> {
                } // Anything else, such as arrays, will be taken care of by the above
            }
        } catch (e: ClassCarpenterException) {
            throw NotSerializableException("${typeInformation.typeIdentifier.name}: ${e.message}")
        }
        return typeInformation.typeIdentifier.getLocalType(classLoader)
    }

    private val RemoteTypeInformation.erasedLocalClass get() = typeIdentifier.getLocalType(classLoader).asClass()

    private fun RemoteTypeInformation.AnInterface.carpentInterface() {
        val fields = properties.mapValues { (name, property) ->
            FieldFactory.newInstance(property.isMandatory, name, property.type.erasedLocalClass)
        }
        val schema = CarpenterSchemaFactory.newInstance(
                name = typeIdentifier.name,
                fields = fields,
                interfaces = interfaces.map { it.erasedLocalClass },
                isInterface = true)
        carpenter.build(schema)
    }

    private fun RemoteTypeInformation.Composable.carpentComposable() {
        val fields = properties.mapValues { (name, property) ->
            FieldFactory.newInstance(property.isMandatory, name, property.type.erasedLocalClass)
        }
        val schema = CarpenterSchemaFactory.newInstance(
                name = typeIdentifier.name,
                fields = fields,
                interfaces = interfaces.map { it.erasedLocalClass },
                isInterface = false)
        carpenter.build(schema)
    }

    private fun RemoteTypeInformation.AnEnum.carpentEnum() {
        carpenter.build(EnumSchema(name = typeIdentifier.name, fields = members.associate { it to EnumField() }))
    }
}