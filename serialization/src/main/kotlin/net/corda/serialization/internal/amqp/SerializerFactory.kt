package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import java.io.NotSerializableException
import javax.annotation.concurrent.ThreadSafe

@KeepForDJVM
class SerializationSchemas(resolveSchema: () -> Pair<Schema, TransformsSchema>) {
        constructor(schema: Schema, transforms: TransformsSchema) : this({ schema to transforms })

        private val resolvedSchema: Pair<Schema, TransformsSchema> by lazy(resolveSchema)

        val schema: Schema get() = resolvedSchema.first
        val transforms: TransformsSchema get() = resolvedSchema.second

        operator fun component1(): Schema = schema
        operator fun component2(): TransformsSchema = transforms
}

/**
 * Factory of serializers designed to be shared across threads and invocations.
 *
 * @property evolutionSerializerProvider controls how evolution serializers are generated by the factory. The normal
 * use case is an [EvolutionSerializer] type is returned. However, in some scenarios, primarily testing, this
 * can be altered to fit the requirements of the test.
 * @property onlyCustomSerializers used for testing, when set will cause the factory to throw a
 * [NotSerializableException] if it cannot find a registered custom serializer for a given type
 */
@KeepForDJVM
@ThreadSafe
interface SerializerFactory : LocalSerializerFactory, RemoteSerializerFactory, CustomSerializerRegistry

class ComposedSerializerFactory(
        private val localSerializerFactory: LocalSerializerFactory,
        private val remoteSerializerFactory: RemoteSerializerFactory,
        private val customSerializerRegistry: CachingCustomSerializerRegistry
) : SerializerFactory,
        LocalSerializerFactory by localSerializerFactory,
        RemoteSerializerFactory by remoteSerializerFactory,
        CustomSerializerRegistry by customSerializerRegistry {

        override val customSerializerNames: List<String>
                get() = customSerializerRegistry.customSerializerNames
}