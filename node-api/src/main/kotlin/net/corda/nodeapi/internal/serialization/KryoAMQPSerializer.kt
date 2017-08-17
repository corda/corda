package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.utilities.sequence
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput

/**
 * This [Kryo] custom [Serializer] switches the object graph of anything annotated with `@CordaSerializable`
 * to using the AMQP serialization wire format, and simply writes that out as bytes to the wire.
 *
 * There is no need to write out the length, since this can be peeked out of the first few bytes of the stream.
 */
class KryoAMQPSerializer(val serializationFactory: SerializationFactory, val serializationContext: SerializationContext) : Serializer<Any>() {
    override fun write(kryo: Kryo, output: Output, obj: Any) {
        val bytes = serializationFactory.serialize(obj, serializationContext.withPreferredSerializationVersion(AmqpHeaderV1_0)).bytes
        // No need to write out the size since it's encoded within the AMQP.
        output.write(bytes)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Any>): Any {
        // Use our helper functions to peek the size of the serialized object out of the AMQP byte stream.
        val peekedBytes = input.readBytes(DeserializationInput.BYTES_NEEDED_TO_PEEK)
        val size = DeserializationInput.peekSize(peekedBytes)
        val allBytes = peekedBytes.copyOf(size)
        input.readBytes(allBytes, peekedBytes.size, size - peekedBytes.size)
        return serializationFactory.deserialize(allBytes.sequence(), type, serializationContext)
    }
}
