package net.corda.core.serialization.amqp

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.EmptyWhitelist
import net.corda.testing.MEGA_CORP_PUBKEY
import org.apache.qpid.proton.codec.DecoderImpl
import org.apache.qpid.proton.codec.EncoderImpl
import org.junit.Test
import java.io.IOException
import java.io.NotSerializableException
import java.nio.ByteBuffer
import java.util.*
import kotlin.test.assertNotNull
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

        override fun hashCode(): Int {
            return 1 // This isn't used, but without overriding we get a warning.
        }
    }

    data class Woo(val fred: Int) {
        @Suppress("unused")
        val bob = "Bob"
    }

    data class Woo2(val fred: Int, val bob: String = "Bob") {
        @ConstructorForDeserialization constructor(fred: Int) : this(fred, "Ginger")
    }

    @CordaSerializable
    data class AnnotatedWoo(val fred: Int) {
        @Suppress("unused")
        val bob = "Bob"
    }

    class FooList : ArrayList<Foo>()

    @Suppress("AddVarianceModifier")
    data class GenericFoo<T>(val bar: String, val pub: T)

    data class TreeMapWrapper(val tree: TreeMap<Int, Foo>)

    data class NavigableMapWrapper(val tree: NavigableMap<Int, Foo>)

    data class SortedSetWrapper(val set: SortedSet<Int>)

    class Mismatch(fred: Int) {
        val ginger: Int = fred

        override fun equals(other: Any?): Boolean = (other as? Mismatch)?.ginger == ginger
        override fun hashCode(): Int = ginger
    }

    class MismatchType(fred: Long) {
        val ginger: Int = fred.toInt()

        override fun equals(other: Any?): Boolean = (other as? MismatchType)?.ginger == ginger
        override fun hashCode(): Int = ginger
    }

    @CordaSerializable
    interface AnnotatedInterface

    data class InheritAnnotation(val foo: String) : AnnotatedInterface

    private fun serdes(obj: Any, factory: SerializerFactory = SerializerFactory(), freshDeserializationFactory: SerializerFactory = SerializerFactory(), expectedEqual: Boolean = true): Any {
        val ser = SerializationOutput(factory)
        val bytes = ser.serialize(obj)

        val decoder = DecoderImpl().apply {
            this.register(Envelope.DESCRIPTOR, Envelope.Companion)
            this.register(Schema.DESCRIPTOR, Schema.Companion)
            this.register(Descriptor.DESCRIPTOR, Descriptor.Companion)
            this.register(Field.DESCRIPTOR, Field.Companion)
            this.register(CompositeType.DESCRIPTOR, CompositeType.Companion)
            this.register(Choice.DESCRIPTOR, Choice.Companion)
            this.register(RestrictedType.DESCRIPTOR, RestrictedType.Companion)
        }
        EncoderImpl(decoder)
        decoder.setByteBuffer(ByteBuffer.wrap(bytes.bytes, 8, bytes.size - 8))
        // Check that a vanilla AMQP decoder can deserialize without schema.
        val result = decoder.readObject() as Envelope
        assertNotNull(result)

        val des = DeserializationInput(freshDeserializationFactory)
        val desObj = des.deserialize(bytes)
        assertTrue(Objects.deepEquals(obj, desObj) == expectedEqual)

        // Now repeat with a re-used factory
        val ser2 = SerializationOutput(factory)
        val des2 = DeserializationInput(factory)
        val desObj2 = des2.deserialize(ser2.serialize(obj))
        assertTrue(Objects.deepEquals(obj, desObj2) == expectedEqual)
        assertTrue(Objects.deepEquals(desObj, desObj2))

        // TODO: add some schema assertions to check correctly formed.
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

    @Test(expected = NotSerializableException::class)
    fun `test TreeMap`() {
        val obj = TreeMap<Int, Foo>()
        obj[456] = Foo("Fred", 123)
        serdes(obj)
    }

    @Test(expected = NotSerializableException::class)
    fun `test TreeMap property`() {
        val obj = TreeMapWrapper(TreeMap<Int, Foo>())
        obj.tree[456] = Foo("Fred", 123)
        serdes(obj)
    }

    @Test
    fun `test NavigableMap property`() {
        val obj = NavigableMapWrapper(TreeMap<Int, Foo>())
        obj.tree[456] = Foo("Fred", 123)
        serdes(obj)
    }

    @Test
    fun `test SortedSet property`() {
        val obj = SortedSetWrapper(TreeSet<Int>())
        obj.set += 456
        serdes(obj)
    }

    @Test(expected = NotSerializableException::class)
    fun `test mismatched property and constructor naming`() {
        val obj = Mismatch(456)
        serdes(obj)
    }

    @Test(expected = NotSerializableException::class)
    fun `test mismatched property and constructor type`() {
        val obj = MismatchType(456)
        serdes(obj)
    }

    @Test
    fun `test custom serializers on public key`() {
        val factory = SerializerFactory()
        factory.register(net.corda.core.serialization.amqp.custom.PublicKeySerializer())
        val factory2 = SerializerFactory()
        factory2.register(net.corda.core.serialization.amqp.custom.PublicKeySerializer())
        val obj = MEGA_CORP_PUBKEY
        serdes(obj, factory, factory2)
    }

    @Test
    fun `test annotation is inherited`() {
        val obj = InheritAnnotation("blah")
        serdes(obj, SerializerFactory(EmptyWhitelist))
    }

    @Test
    fun `test throwables serialize`() {
        val factory = SerializerFactory()
        factory.register(net.corda.core.serialization.amqp.custom.ThrowableSerializer(factory))

        val factory2 = SerializerFactory()
        factory2.register(net.corda.core.serialization.amqp.custom.ThrowableSerializer(factory2))

        val obj = IllegalAccessException("message").fillInStackTrace()
        serdes(obj, factory, factory2, false)
    }

    @Test
    fun `test complex throwables serialize`() {
        val factory = SerializerFactory()
        factory.register(net.corda.core.serialization.amqp.custom.ThrowableSerializer(factory))

        val factory2 = SerializerFactory()
        factory2.register(net.corda.core.serialization.amqp.custom.ThrowableSerializer(factory2))

        try {
            try {
                throw IOException("Layer 1")
            } catch(t: Throwable) {
                throw IllegalStateException("Layer 2", t)
            }
        } catch(t: Throwable) {
            serdes(t, factory, factory2, false)
        }
    }


    @Test
    fun `test suppressed throwables serialize`() {
        val factory = SerializerFactory()
        factory.register(net.corda.core.serialization.amqp.custom.ThrowableSerializer(factory))

        val factory2 = SerializerFactory()
        factory2.register(net.corda.core.serialization.amqp.custom.ThrowableSerializer(factory2))

        try {
            try {
                throw IOException("Layer 1")
            } catch(t: Throwable) {
                val e = IllegalStateException("Layer 2")
                e.addSuppressed(t)
                throw e
            }
        } catch(t: Throwable) {
            serdes(t, factory, factory2, false)
        }
    }
}