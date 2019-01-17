package net.corda.serialization.internal.amqp.api

import net.corda.core.internal.reflection.LocalTypeInformation
import net.corda.serialization.internal.model.RemoteTypeInformation

/**
 * A factory that knows how to create serialisers when there is a mismatch between the remote and local type schemas.
 */
interface EvolutionSerializerFactory {

    /**
     * Compare the given [RemoteTypeInformation] and [LocalTypeInformation], and construct (if needed) an evolution
     * serialiser that can take properties serialised in the remote schema and construct an object conformant to the local schema.
     *
     * Will return null if no evolution is necessary, because the schemas are compatible.
     */
    fun getEvolutionSerializer(
            remote: RemoteTypeInformation,
            local: LocalTypeInformation): AMQPSerializer<Any>?
}