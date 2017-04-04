package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.DecoderImpl
import org.apache.qpid.proton.codec.EncoderImpl
import org.junit.Test
import java.io.NotSerializableException
import java.nio.ByteBuffer
import kotlin.test.assertEquals


class SerializationOutputTests {

    data class Foo(val bar: String, val pub: Int)

    interface FooInterface {
        val pub: Int
    }

    data class FooImplements(val bar: String, override val pub: Int) : FooInterface

    data class FooImplementsAndList(val bar: String, override val pub: Int, val names: List<String>) : FooInterface

    data class WrapHashMap(val map: Map<String, String>)

    private fun serdes(obj: Any): Any {
        val factory = SerializerFactory()
        val ser = SerializationOutput(factory)
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
        assertEquals(obj, desObj)

        // Now repeat with a re-used factory
        val ser2 = SerializationOutput(factory)
        val des2 = DeserializationInput(factory)
        val desObj2 = des2.deserialize(ser2.serialize(obj))
        assertEquals(obj, desObj2)
        return desObj2
    }

    @Test
    fun `test foo`() {
        val obj = Foo("Hello World!", 123)
        serdes(obj)
    }


    @Test
    fun `test foo implements`() {
        val obj = FooImplements("Hello World!", 123)
        serdes(obj)
    }

    @Test
    fun `test foo implements and list`() {
        val obj = FooImplementsAndList("Hello World!", 123, listOf("Fred", "Ginger"))
        serdes(obj)
    }


    @Test(expected = NotSerializableException::class)
    fun `test dislike of HashMap`() {
        val obj = WrapHashMap(HashMap<String, String>())
        serdes(obj)
    }
}