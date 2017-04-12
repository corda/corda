package net.corda.core.serialization.amqp

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.EmptyWhitelist
import org.apache.qpid.proton.codec.DecoderImpl
import org.apache.qpid.proton.codec.EncoderImpl
import org.junit.Test
import java.io.NotSerializableException
import java.nio.ByteBuffer
import java.util.*
import kotlin.test.assertTrue


class SerializationOutputTests {

    data class Foo(val bar: String, val pub: Int)

    interface FooInterface {
        val pub: Int
    }

    data class FooImplements(val bar: String, override val pub: Int) : FooInterface

    data class FooImplementsAndList(val bar: String, override val pub: Int, val names: List<String>) : FooInterface

    data class WrapHashMap(val map: Map<String, String>)

    data class WrapFooListArray(val listArray: Array<List<Foo>>) {
        override fun equals(other: Any?): Boolean {
            return other is WrapFooListArray && Objects.deepEquals(listArray, other.listArray)
        }
    }

    data class Woo(val fred: Int) {
        val bob = "Bob"
    }

    data class Woo2(val fred: Int, val bob: String = "Bob") {
        @CordaConstructor constructor(@CordaParam("fred") foo: Int) : this(foo, "Ginger")
    }

    @CordaSerializable
    data class AnnotatedWoo(val fred: Int) {
        val bob = "Bob"
    }

    class FooList : ArrayList<Foo>()

    data class GenericFoo<T>(val bar: String, val pub: T)

    private fun serdes(obj: Any, factory: SerializerFactory = SerializerFactory()): Any {
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
        assertTrue(Objects.deepEquals(obj, desObj))

        // Now repeat with a re-used factory
        val ser2 = SerializationOutput(factory)
        val des2 = DeserializationInput(factory)
        val desObj2 = des2.deserialize(ser2.serialize(obj))
        assertTrue(Objects.deepEquals(obj, desObj2))
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

    @Test
    fun `test string array`() {
        val obj = arrayOf("Fred", "Ginger")
        serdes(obj)
    }

    @Test
    fun `test foo array`() {
        val obj = arrayOf(Foo("Fred", 1), Foo("Ginger", 2))
        serdes(obj)
    }

    @Test
    fun `test top level list array`() {
        val obj = arrayOf(listOf("Fred", "Ginger"), listOf("Rogers", "Hammerstein"))
        serdes(obj)
    }

    @Test
    fun `test foo list array`() {
        val obj = WrapFooListArray(arrayOf(listOf(Foo("Fred", 1), Foo("Ginger", 2)), listOf(Foo("Rogers", 3), Foo("Hammerstein", 4))))
        serdes(obj)
    }

    @Test
    fun `test not all properties in constructor`() {
        val obj = Woo(2)
        serdes(obj)
    }

    @Test
    fun `test annotated constructor`() {
        val obj = Woo2(3)
        serdes(obj)
    }

    @Test(expected = NotSerializableException::class)
    fun `test whitelist`() {
        val obj = Woo2(4)
        serdes(obj, SerializerFactory(EmptyWhitelist))
    }

    @Test
    fun `test annotation whitelisting`() {
        val obj = AnnotatedWoo(5)
        serdes(obj, SerializerFactory(EmptyWhitelist))
    }

    @Test(expected = NotSerializableException::class)
    fun `test generic list subclass is not supported`() {
        val obj = FooList()
        serdes(obj)
    }

    @Test(expected = NotSerializableException::class)
    fun `test generic foo`() {
        val obj = GenericFoo("Fred", "Ginger")
        serdes(obj)
    }
}