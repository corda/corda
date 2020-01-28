package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.internal.MissingSerializerException
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.serialization.internal.model.*
import java.io.NotSerializableException
import java.util.Collections.singletonList

/**
 * A factory that knows how to create serializers to deserialize values sent to us by remote parties.
 */
interface RemoteSerializerFactory {
    /**
     * Lookup and manufacture a serializer for the given AMQP type descriptor, assuming we also have the necessary types
     * contained in the provided [Schema].
     *
     * @param typeDescriptor The type descriptor for the type to obtain a serializer for.
     * @param schema The schemas sent along with the serialized data.
     */
    @Throws(NotSerializableException::class, ClassNotFoundException::class)
    fun get(typeDescriptor: TypeDescriptor, schema: SerializationSchemas, context: SerializationContext): AMQPSerializer<Any>
}

/**
 * Represents the reflection of some [RemoteTypeInformation] by some [LocalTypeInformation], which we use to make
 * decisions about evolution.
 */
data class RemoteAndLocalTypeInformation(
        val remoteTypeInformation: RemoteTypeInformation,
        val localTypeInformation: LocalTypeInformation)

/**
 * A [RemoteSerializerFactory] which uses an [AMQPRemoteTypeModel] to interpret AMQP [Schema]s into [RemoteTypeInformation],
 * reflects this into [LocalTypeInformation] using a [LocalTypeModel] and a [TypeLoader], and compares the two in order to
 * decide whether to return the serializer provided by the [LocalSerializerFactory] or to construct a special evolution serializer
 * using the [EvolutionSerializerFactory].
 *
 * Its decisions are recorded by registering the chosen serialisers against their type descriptors
 * in the [DescriptorBasedSerializerRegistry].
 *
 * @param evolutionSerializerFactory The [EvolutionSerializerFactory] to use to create evolution serializers, when necessary.
 * @param descriptorBasedSerializerRegistry The registry to use to store serializers by [TypeDescriptor].
 * @param remoteTypeModel The [AMQPRemoteTypeModel] to use to interpret AMPQ [Schema] information into [RemoteTypeInformation].
 * @param localTypeModel The [LocalTypeModel] to use to obtain [LocalTypeInformation] for reflected [Type]s.
 * @param typeLoader The [TypeLoader] to use to load local [Type]s reflecting [RemoteTypeInformation].
 * @param localSerializerFactory The [LocalSerializerFactory] to use to obtain serializers for non-evolved types.
 */
class DefaultRemoteSerializerFactory(
        private val evolutionSerializerFactory: EvolutionSerializerFactory,
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        private val remoteTypeModel: AMQPRemoteTypeModel,
        private val localTypeModel: LocalTypeModel,
        private val typeLoader: TypeLoader,
        private val localSerializerFactory: LocalSerializerFactory)
    : RemoteSerializerFactory {

    companion object {
        private val logger = contextLogger()
    }

    override fun get(
            typeDescriptor: TypeDescriptor,
            schema: SerializationSchemas,
            context: SerializationContext
    ): AMQPSerializer<Any> =
        // If we have seen this descriptor before, we assume we have seen everything in this schema before.
        descriptorBasedSerializerRegistry.getOrBuild(typeDescriptor) {
            logger.trace { "get Serializer descriptor=$typeDescriptor" }

            // Interpret all of the types in the schema into RemoteTypeInformation, and reflect that into LocalTypeInformation.
            val remoteTypeInformationMap = remoteTypeModel.interpret(schema)
            val reflected = reflect(remoteTypeInformationMap, context)

            // Get, and record in the registry, serializers for all of the types contained in the schema.
            // This will save us having to re-interpret the entire schema on re-entry when deserialising individual property values.
            val serializers = reflected.mapValues { (descriptor, remoteLocalPair) ->
                descriptorBasedSerializerRegistry.getOrBuild(descriptor) {
                    getUncached(remoteLocalPair.remoteTypeInformation, remoteLocalPair.localTypeInformation, context)
                }
            }

            // Return the specific serializer the caller asked for.
            serializers[typeDescriptor] ?: throw MissingSerializerException(
                message = "Could not find type matching descriptor $typeDescriptor.",
                typeDescriptor = typeDescriptor
            )
        }

    private fun getUncached(
            remoteTypeInformation: RemoteTypeInformation,
            localTypeInformation: LocalTypeInformation,
            context: SerializationContext
    ): AMQPSerializer<Any> {
        val remoteDescriptor = remoteTypeInformation.typeDescriptor

        // Obtain a serializer and descriptor for the local type.
        val localSerializer = localSerializerFactory.get(localTypeInformation)
        val localDescriptor = localSerializer.typeDescriptor.toString()

        return when {
            // If descriptors match, we can return the local serializer straight away.
            localDescriptor == remoteDescriptor -> localSerializer

            // Can we deserialise without evolution, e.g. going from List<Foo> to List<*>?
            remoteTypeInformation.isDeserialisableWithoutEvolutionTo(localTypeInformation) -> localSerializer

            // Are the remote/local types evolvable? If so, ask the evolution serializer factory for a serializer, returning
            // the local serializer if it returns null (i.e. no evolution required).
            remoteTypeInformation.isEvolvableTo(localTypeInformation) ->
                evolutionSerializerFactory.getEvolutionSerializer(remoteTypeInformation, localTypeInformation)
                        ?: localSerializer

            // The type descriptors are never going to match when we deserialise into
            // the DJVM's sandbox, but we don't want the node logs to fill up with
            // Big 'n Scary warnings either. Assume that the local serializer is fine
            // provided the local type is the same one we expect when loading the
            // remote class.
            remoteTypeInformation.isCompatibleWith(localTypeInformation, context) -> localSerializer

            // Descriptors don't match, and something is probably broken, but we let the framework do what it can with the local
            // serialiser (BlobInspectorTest uniquely breaks if we throw an exception here, and passes if we just warn and continue).
            else -> {
                logger.warn("""
Mismatch between type descriptors, but remote type is not evolvable to local type.

Remote type (descriptor: $remoteDescriptor)
${remoteTypeInformation.prettyPrint(false)}

Local type (descriptor $localDescriptor):
${localTypeInformation.prettyPrint(false)}
        """)

                localSerializer
            }
        }
    }

    private fun reflect(remoteInformation: Map<TypeDescriptor, RemoteTypeInformation>, context: SerializationContext):
            Map<TypeDescriptor, RemoteAndLocalTypeInformation> {
        val localInformationByIdentifier = typeLoader.load(remoteInformation.values, context).mapValues { (_, type) ->
            localTypeModel.inspect(type)
        }

        return remoteInformation.mapValues { (_, remoteInformation) ->
            RemoteAndLocalTypeInformation(remoteInformation, localInformationByIdentifier.getValue(remoteInformation.typeIdentifier))
        }
    }

    private fun RemoteTypeInformation.isEvolvableTo(localTypeInformation: LocalTypeInformation): Boolean = when(this) {
        is RemoteTypeInformation.Composable -> localTypeInformation is LocalTypeInformation.Composable
        is RemoteTypeInformation.AnEnum -> localTypeInformation is LocalTypeInformation.AnEnum
        else -> false
    }

    private fun RemoteTypeInformation.isDeserialisableWithoutEvolutionTo(localTypeInformation: LocalTypeInformation) =
            this is RemoteTypeInformation.Parameterised &&
                (localTypeInformation is LocalTypeInformation.ACollection ||
                localTypeInformation is LocalTypeInformation.AMap)

    private fun RemoteTypeInformation.isCompatibleWith(
        localTypeInformation: LocalTypeInformation,
        context: SerializationContext
    ): Boolean {
        val localTypes = typeLoader.load(singletonList(this), context)
        return localTypes.size == 1
            && localTypeInformation.observedType == localTypes.values.first()
    }
}