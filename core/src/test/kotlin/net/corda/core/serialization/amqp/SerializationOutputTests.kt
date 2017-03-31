package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.DecoderImpl
import org.apache.qpid.proton.codec.EncoderImpl
import org.junit.Test
import java.nio.ByteBuffer


class SerializationOutputTests {

    data class Foo(val bar: String, val pub: Int)

    @Test
    fun `test`() {
        val obj = Foo("Hello World!", 123)
        val ser = SerializationOutput()
        val bytes = ser.serialize(obj)

        val decoder = DecoderImpl().apply {
            this.register(Envelope.DESCRIPTOR, Envelope.Constructor)
            this.register(Schema.DESCRIPTOR, Schema.Constructor)
            this.register(Descriptor.DESCRIPTOR, Descriptor.Constructor)
            this.register(Field.DESCRIPTOR, Field.Constructor)
            this.register(CompositeType.DESCRIPTOR, CompositeType.Constructor)
            this.register(Choice.DESCRIPTOR, Choice.Constructor)
            this.register(RestrictedType.DESCRIPTOR, RestrictedType.Constructor)
        }
        EncoderImpl(decoder)
        decoder.setByteBuffer(ByteBuffer.wrap(bytes.bytes, 8, bytes.size - 8))
        val result = decoder.readObject()
        println(result)

        val des = DeserializationInput()
        val desObj = des.deserialize(bytes)
        println(desObj)
    }
}