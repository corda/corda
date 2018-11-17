package net.corda.serialization.internal.amqp

import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

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
        private val evolutionSerializerFactory: EvolutionSerializerFactory,
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        private val remoteTypeModel: AMQPRemoteTypeModel,
        private val typeReflector: RemoteTypeReflector,
        private val localSerializerFactory: LocalSerializerFactory)
    : RemoteSerializerFactory {

    companion object {
        private val logger = contextLogger()
    }

    override fun get(typeDescriptor: Any, schema: SerializationSchemas): AMQPSerializer<Any> =
        // If we have seen this descriptor before, we assume we have seen everything in this schema before.
        descriptorBasedSerializerRegistry[typeDescriptor.toString()] ?: {
            logger.trace("get Serializer descriptor=$typeDescriptor")

            // Interpret all of the types in the schema into RemoteTypeInformation, and reflect that into LocalTypeInformation.
            val remoteTypeInformationMap = remoteTypeModel.interpret(schema)
            val reflected = typeReflector.reflect(remoteTypeInformationMap)

           // Make sure the registry is populated with a serializer for every type we have interpreted from the schema.
           val serializers = reflected.mapValues { (descriptor, remoteLocalPair) ->
               descriptorBasedSerializerRegistry[descriptor] ?:
                 getUncached(remoteLocalPair, descriptor)

           }

            serializers[typeDescriptor.toString()] ?: throw NotSerializableException(
                    "Could not find type matching descriptor $typeDescriptor.")
        }()

    private fun getUncached(remoteLocalPair: ReflectedTypeInformation, descriptor: TypeDescriptor): AMQPSerializer<Any> {
        val (remoteTypeInformation, localTypeInformation) = remoteLocalPair

        if (remoteTypeInformation !is RemoteTypeInformation.Composable
            && remoteTypeInformation !is RemoteTypeInformation.AnEnum) return localSerializerFactory.get(localTypeInformation)

        val localDescriptor = localSerializerFactory.createDescriptor(localTypeInformation)

        return if (localDescriptor.toString() == descriptor) {
            localSerializerFactory.get(localTypeInformation)
        } else {
            evolutionSerializerFactory.getEvolutionSerializer(remoteTypeInformation, localTypeInformation).also {
                descriptorBasedSerializerRegistry[descriptor] = it
            }
        }
    }
}