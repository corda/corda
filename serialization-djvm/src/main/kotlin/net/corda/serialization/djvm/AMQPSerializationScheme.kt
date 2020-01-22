package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationScheme
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.amqpMagic

/**
 * This is an ephemeral [SerializationScheme] that will only ever
 * support a single [SerializerFactory]. The [ClassLoader] that
 * underpins everything this scheme is deserializing is not expected
 * to be long-lived either.
 */
class AMQPSerializationScheme(
    val serializerFactory: SerializerFactory
) : SerializationScheme {
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        return DeserializationInput(serializerFactory).deserialize(byteSequence, clazz, context)
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        return SerializationOutput(serializerFactory).serialize(obj, context)
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: UseCase): Boolean {
        return magic == amqpMagic && target == UseCase.P2P
    }
}
