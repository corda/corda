package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput
import net.corda.nodeapi.internal.serialization.amqp.SerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory

/**
 * This [Kryo] custom [Serializer] switches the object graph of anything annotated with `@CordaSerializable`
 * to using the AMQP serialization wire format, and simply writes that out as bytes to the wire.
 *
 * There is no need to write out the length, since this can be peeked out of the first few bytes of the stream.
 */
object KryoAMQPSerializer : Serializer<Any>() {
    internal fun registerCustomSerializers(factory: SerializerFactory) {
        factory.apply {
            register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)
            register(net.corda.nodeapi.internal.serialization.amqp.custom.ThrowableSerializer(this))
            register(net.corda.nodeapi.internal.serialization.amqp.custom.X500NameSerializer)
            register(net.corda.nodeapi.internal.serialization.amqp.custom.BigDecimalSerializer)
            register(net.corda.nodeapi.internal.serialization.amqp.custom.CurrencySerializer)
            register(net.corda.nodeapi.internal.serialization.amqp.custom.InstantSerializer(this))
        }
    }

    // TODO: need to sort out the whitelist... we currently do not apply the whitelist attached to the [Kryo]
    // instance to the factory.  We need to do this before turning on AMQP serialization.
    private val serializerFactory = SerializerFactory().apply {
        registerCustomSerializers(this)
    }

    override fun write(kryo: Kryo, output: Output, obj: Any) {
        val amqpOutput = SerializationOutput(serializerFactory)
        val bytes = amqpOutput.serialize(obj).bytes
        // No need to write out the size since it's encoded within the AMQP.
        output.write(bytes)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Any>): Any {
        val amqpInput = DeserializationInput(serializerFactory)
        // Use our helper functions to peek the size of the serialized object out of the AMQP byte stream.
        val peekedBytes = input.readBytes(DeserializationInput.BYTES_NEEDED_TO_PEEK)
        val size = DeserializationInput.peekSize(peekedBytes)
        val allBytes = peekedBytes.copyOf(size)
        input.readBytes(allBytes, peekedBytes.size, size - peekedBytes.size)
        return amqpInput.deserialize(SerializedBytes<Any>(allBytes), type)
    }
}