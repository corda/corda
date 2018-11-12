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
        descriptorBasedSerializerRegistry[typeDescriptor.toString()] ?: {
            logger.trace("get Serializer descriptor=${typeDescriptor}")

            val remoteTypeInformationMap = remoteTypeModel.interpret(schema.schema)
            val reflected = typeReflector.reflect(remoteTypeInformationMap)

            val (remoteTypeInformation, localTypeInformation) = reflected[typeDescriptor.toString()] ?: throw NotSerializableException(
                    "Could not find type matching descriptor $typeDescriptor.")

            val localDescriptor = localSerializerFactory.createDescriptor(localTypeInformation.observedType)
            if (localDescriptor.toString() == typeDescriptor.toString()) {
                localSerializerFactory.get(localTypeInformation)
            } else {
                evolutionSerializerFactory.getEvolutionSerializer(remoteTypeInformation, localTypeInformation)
            }
        }()

}