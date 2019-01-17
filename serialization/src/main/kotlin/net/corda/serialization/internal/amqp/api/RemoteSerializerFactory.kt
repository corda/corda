package net.corda.serialization.internal.amqp.api

import net.corda.serialization.internal.amqp.schema.SerializationSchemas
import net.corda.serialization.internal.model.TypeDescriptor
import java.io.NotSerializableException

/**
 * A factory that knows how to create serializers to deserialize values sent to us by remote parties.
 */
interface RemoteSerializerFactory {
    /**
     * Lookup and manufacture a serializer for the given AMQP type descriptor, assuming we also have the necessary types
     * contained in the provided [SerializationSchemas].
     *
     * @param typeDescriptor The type descriptor for the type to obtain a serializer for.
     * @param schema The schemas sent along with the serialized data.
     */
    @Throws(NotSerializableException::class)
    fun get(typeDescriptor: TypeDescriptor, schema: SerializationSchemas): AMQPSerializer<Any>
}