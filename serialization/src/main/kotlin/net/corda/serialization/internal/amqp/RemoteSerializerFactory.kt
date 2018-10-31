package net.corda.serialization.internal.amqp

import net.corda.core.StubOutForDJVM
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.serialization.internal.carpenter.CarpenterMetaSchema
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.MetaCarpenter
import net.corda.serialization.internal.carpenter.MetaCarpenterException
import java.io.NotSerializableException
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
        private val classCarpenter: ClassCarpenter,
        private val classloader: ClassLoader,
        private val evolutionSerializerProvider: EvolutionSerializerProvider,
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry<AMQPSerializer<Any>>,
        private val evolutionSerializerFactory: EvolutionSerializerFactory)
    : RemoteSerializerFactory {

    companion object {
        private val logger = contextLogger()
    }

    override fun get(typeDescriptor: Any, schema: SerializationSchemas): AMQPSerializer<Any> =
            descriptorBasedSerializerRegistry[typeDescriptor.toString()] ?: {
            logger.trace("get Serializer descriptor=${typeDescriptor}")
            processSchema(FactorySchemaAndDescriptor(schema, typeDescriptor))
            descriptorBasedSerializerRegistry[typeDescriptor.toString()] ?: throw NotSerializableException(
                    "Could not find type matching descriptor $typeDescriptor.")
        }()

    /**
     * Iterate over an AMQP schema, for each type ascertain whether it's on ClassPath of [classloader] and,
     * if not, use the [ClassCarpenter] to generate a class to use in its place.
     */
    private fun processSchema(schemaAndDescriptor: FactorySchemaAndDescriptor, sentinel: Boolean = false) {
        val requiringCarpentry = schemaAndDescriptor.schemas.schema.types.asSequence().mapNotNull { typeNotation ->
            try {
                getOrRegisterSerializer(schemaAndDescriptor, typeNotation)
                return@mapNotNull null
            } catch (e: ClassNotFoundException) {
                if (sentinel) {
                    logger.error("typeNotation=${typeNotation.name} error=\"after Carpentry attempt failed to load\"")
                    throw e
                }
                logger.trace { "typeNotation=\"${typeNotation.name}\" action=\"carpentry required\"" }
                return@mapNotNull typeNotation
            }
        }.toList()

        if (requiringCarpentry.isEmpty()) return

        runCarpentry(schemaAndDescriptor, CarpenterMetaSchema.buildWith(classloader, requiringCarpentry))
    }

    private fun getOrRegisterSerializer(schemaAndDescriptor: FactorySchemaAndDescriptor, typeNotation: TypeNotation) {
        logger.trace { "descriptor=${schemaAndDescriptor.typeDescriptor}, typeNotation=${typeNotation.name}" }
        val serialiser = processSchemaEntry(typeNotation)

        // if we just successfully built a serializer for the type but the type fingerprint
        // doesn't match that of the serialised object then we may be dealing with different
        // instance of the class, and such we need to build an EvolutionSerializer
        if (serialiser.typeDescriptor == typeNotation.descriptor.name) return

        logger.trace { "typeNotation=${typeNotation.name} action=\"requires Evolution\"" }
        evolutionSerializerProvider.getEvolutionSerializer(evolutionSerializerFactory, typeNotation, serialiser, schemaAndDescriptor.schemas)
    }

    private fun processSchemaEntry(typeNotation: TypeNotation) = when (typeNotation) {
        // java.lang.Class (whether a class or interface)
        is CompositeType -> {
            logger.trace("typeNotation=${typeNotation.name} amqpType=CompositeType")
            processCompositeType(typeNotation)
        }
        // Collection / Map, possibly with generics
        is RestrictedType -> {
            logger.trace("typeNotation=${typeNotation.name} amqpType=RestrictedType")
            processRestrictedType(typeNotation)
        }
    }

    // TODO: class loader logic, and compare the schema.
    private fun processRestrictedType(typeNotation: RestrictedType) =
            evolutionSerializerFactory.get(null, typeForName(typeNotation.name, classloader))

    private fun processCompositeType(typeNotation: CompositeType): AMQPSerializer<Any> {
        // TODO: class loader logic, and compare the schema.
        val type = typeForName(typeNotation.name, classloader)
        return evolutionSerializerFactory.get(type.asClass(), type)
    }

    private fun typeForName(name: String, classloader: ClassLoader): Type =
            AMQPTypeIdentifierParser.parse(name).getLocalType(classloader)

    @StubOutForDJVM
    private fun runCarpentry(schemaAndDescriptor: FactorySchemaAndDescriptor, metaSchema: CarpenterMetaSchema) {
        val mc = MetaCarpenter(metaSchema, classCarpenter)
        try {
            mc.build()
        } catch (e: MetaCarpenterException) {
            // preserve the actual message locally
            loggerFor<SerializerFactory>().apply {
                error("${e.message} [hint: enable trace debugging for the stack trace]")
                trace("", e)
            }

            // prevent carpenter exceptions escaping into the world, convert things into a nice
            // NotSerializableException for when this escapes over the wire
            NotSerializableException(e.name)
        }
        processSchema(schemaAndDescriptor, true)
    }
}