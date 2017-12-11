package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.AllWhitelist
import org.junit.Test
import kotlin.test.assertEquals

class GenericsTests {

    @Test
    fun nestedSerializationOfGenerics() {
        data class G<T>(val a: T)
        data class Wrapper<T>(val a: Int, val b: G<T>)

        val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val altContextFactory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val ser = SerializationOutput(factory)

        val bytes = ser.serializeAndReturnSchema(G("hi"))

        assertEquals("hi", DeserializationInput(factory).deserialize(bytes.obj).a)
        assertEquals("hi", DeserializationInput(altContextFactory).deserialize(bytes.obj).a)

        val bytes2 = ser.serializeAndReturnSchema(Wrapper(1, G("hi")))

        DeserializationInput(factory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals("hi", b.a)
        }

        DeserializationInput(altContextFactory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals("hi", b.a)
        }
    }

    @Test
    fun nestedGenericsReferencesByteArrayViaSerializedBytes() {
        data class G(val a : Int)
        data class Wrapper<T : Any>(val a: Int, val b: SerializedBytes<T>)

        val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val factory2 = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val ser = SerializationOutput(factory)

        val gBytes = ser.serialize(G(1))
        val bytes2 = ser.serializeAndReturnSchema(Wrapper<G>(1, gBytes))

        DeserializationInput(factory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals(1, DeserializationInput(factory).deserialize(b).a)
        }
        DeserializationInput(factory2).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals(1, DeserializationInput(factory).deserialize(b).a)
        }
    }

    @Test
    fun nestedSerializationInMultipleContextsDoesntColideGenericTypes() {
        data class InnerA(val a_a: Int)
        data class InnerB(val a_b: Int)
        data class InnerC(val a_c: String)
        data class Container<T>(val b: T)
        data class Wrapper<T : Any>(val c: Container<T>)

        val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val factories = listOf(factory, SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader()))
        val ser = SerializationOutput(factory)

        ser.serialize(Wrapper(Container(InnerA(1)))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals(1, c.b.a_a) }
            }
        }

        ser.serialize(Wrapper(Container(InnerB(1)))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals(1, c.b.a_b) }
            }
        }

        ser.serialize(Wrapper(Container(InnerC("Ho ho ho")))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals("Ho ho ho", c.b.a_c) }
            }
        }
    }

    @Test
    fun nestedSerializationWhereGenericDoesntImpactFingerprint() {
        data class Inner(val a : Int)
        data class Container<T : Any>(val b: Inner)
        data class Wrapper<T: Any>(val c: Container<T>)

        val factorys = listOf(
                SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader()),
                SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader()))

        val ser = SerializationOutput(factorys[0])

        ser.serialize(Wrapper<Int>(Container(Inner(1)))).apply {
            factorys.forEach {
                assertEquals(1, DeserializationInput(it).deserialize(this).c.b.a)
            }
        }

        ser.serialize(Wrapper<String>(Container(Inner(1)))).apply {
            factorys.forEach {
                assertEquals(1, DeserializationInput(it).deserialize(this).c.b.a)
            }
        }
    }
}
