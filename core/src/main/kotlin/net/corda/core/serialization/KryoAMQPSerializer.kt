package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.amqp.DeserializationInput
import net.corda.core.serialization.amqp.SerializationOutput
import net.corda.core.serialization.amqp.SerializerFactory

// TODO: Consider setting the immutable flag
object KryoAMQPSerializer : Serializer<Any>() {
    internal fun registerCustomSerializers(factory: SerializerFactory) {
        factory.apply {
            register(net.corda.core.serialization.amqp.custom.PublicKeySerializer)
            register(net.corda.core.serialization.amqp.custom.ThrowableSerializer(this))
            register(net.corda.core.serialization.amqp.custom.X500NameSerializer)
            register(net.corda.core.serialization.amqp.custom.BigDecimalSerializer)
            register(net.corda.core.serialization.amqp.custom.CurrencySerializer)
            register(net.corda.core.serialization.amqp.custom.InstantSerializer(this))
        }
    }

    // TODO: need to sort out the whitelist...
    private val serializerFactory = SerializerFactory().apply {
        registerCustomSerializers(this)
    }

    override fun write(kryo: Kryo, output: Output, `object`: Any) {
        val amqpOutput = SerializationOutput(serializerFactory)
        kryo.writeObject(output, amqpOutput.serialize(`object`))
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Any>): Any {
        val amqpInput = DeserializationInput(serializerFactory)
        @Suppress("UNCHECKED_CAST")
        return amqpInput.deserialize(kryo.readObject(input, SerializedBytes::class.java) as SerializedBytes<Any>, type)
    }
}