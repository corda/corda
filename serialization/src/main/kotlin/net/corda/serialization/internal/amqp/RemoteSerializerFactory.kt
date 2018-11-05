package net.corda.serialization.internal.amqp

import net.corda.core.StubOutForDJVM
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.serialization.internal.carpenter.CarpenterMetaSchema
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.MetaCarpenter
import net.corda.serialization.internal.carpenter.MetaCarpenterException
import net.corda.serialization.internal.model.*
import java.io.NotSerializableException
import java.lang.UnsupportedOperationException
import java.lang.reflect.Type

/**
 * A factory that knows how to create serializers to deserialize values sent to us by remote parties.
 */
interface RemoteSerializerFactory {
    /**
     * Lookup and manufacture a serializer for the given AMQP type descriptor, assuming we also have the necessary types
     * contained in the [Schema].
     */
    @Throws(NotSerializableException::class)
    fun get(typeDescriptor: Any, schema: SerializationSchemas): AMQPSerializer<Any>
}

class DefaultRemoteSerializerFactory(
        private val classloader: ClassLoader,
        private val evolutionSerializerProvider: EvolutionSerializerProvider,
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        private val remoteTypeModel: AMQPRemoteTypeModel,
        private val typeReflector: RemoteTypeReflector,
        private val localSerializerFactory: LocalSerializerFactory)
    : RemoteSerializerFactory {

    companion object {
        private val logger = contextLogger()
    }

    override fun get(typeDescriptor: Any, schema: SerializationSchemas): AMQPSerializer<Any> =
        descriptorBasedSerializerRegistry[typeDescriptor.toString()] ?: {
            logger.trace("get Serializer descriptor=${typeDescriptor}")

            val remoteTypeInformation = remoteTypeModel.interpret(schema.schema)
            val reflected = typeReflector.reflect(remoteTypeInformation)

            val requested = reflected[typeDescriptor.toString()] ?: throw NotSerializableException(
                    "Could not find type matching descriptor $typeDescriptor.")

            val localDescriptor = localSerializerFactory.createDescriptor(requested.localTypeInformation.observedType)
            if (localDescriptor.toString() == typeDescriptor.toString()) {
                localSerializerFactory.get(requested.localTypeInformation)
            } else {
                processSchema(FactorySchemaAndDescriptor(schema, typeDescriptor))
                descriptorBasedSerializerRegistry[typeDescriptor.toString()] ?: throw NotSerializableException(
                        "Could not find type matching descriptor $typeDescriptor.")
            }
        }()

    /**
     * Iterate over an AMQP schema, for each type ascertain whether it's on ClassPath of [classloader] and,
     * if not, use the [ClassCarpenter] to generate a class to use in its place.
     */
    private fun processSchema(schemaAndDescriptor: FactorySchemaAndDescriptor) {
        schemaAndDescriptor.schemas.schema.types.asSequence().forEach { typeNotation ->
            getOrRegisterSerializer(schemaAndDescriptor, typeNotation)
        }
    }

    private fun getOrRegisterSerializer(schemaAndDescriptor: FactorySchemaAndDescriptor, typeNotation: TypeNotation) {
        logger.trace { "descriptor=${schemaAndDescriptor.typeDescriptor}, typeNotation=${typeNotation.name}" }
        val serialiser = localSerializerFactory.get(typeForName(typeNotation.name, classloader))

        // if we just successfully built a serializer for the type but the type fingerprint
        // doesn't match that of the serialised object then we may be dealing with different
        // instance of the class, and such we need to build an EvolutionSerializer
        if (serialiser.typeDescriptor == typeNotation.descriptor.name) return

        logger.trace { "typeNotation=${typeNotation.name} action=\"requires Evolution\"" }
        evolutionSerializerProvider.getEvolutionSerializer(localSerializerFactory, descriptorBasedSerializerRegistry, typeNotation, serialiser, schemaAndDescriptor.schemas)
    }

    private fun typeForName(name: String, classloader: ClassLoader): Type =
            AMQPTypeIdentifierParser.parse(name).getLocalType(classloader)
}