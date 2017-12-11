package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.AllWhitelist
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals

class GenericsTests {
    companion object {
        val VERBOSE = false
    }

    private fun printSeparator() = if(VERBOSE) println ("\n\n-------------------------------------------\n\n") else Unit

    private fun<T : Any> BytesAndSchemas<T>.printSchema() = if (VERBOSE) println ("${this.schema}\n") else Unit

    private fun ConcurrentHashMap<Any, AMQPSerializer<Any>>.printKeyToType() {
        if (!VERBOSE) return

        forEach {
            println ("Key = ${it.key} - ${it.value.type.typeName}")
        }
        println()
    }

    @Test
    fun twoDifferntTypesSameParameterizedOuter() {
        data class G<A>(val a: A)

        val factory = testDefaultFactoryNoEvolution()

        val bytes1 = SerializationOutput(factory).serializeAndReturnSchema(G("hi")).apply { printSchema() }

        factory.serializersByDescriptor.printKeyToType()

        val bytes2 = SerializationOutput(factory).serializeAndReturnSchema(G(121)).apply { printSchema() }

        factory.serializersByDescriptor.printKeyToType()

        listOf (factory, testDefaultFactory()).forEach { f ->
            DeserializationInput(f).deserialize(bytes1.obj).apply { assertEquals("hi", this.a) }
            DeserializationInput(f).deserialize(bytes2.obj).apply { assertEquals(121, this.a) }
        }
    }

    @Test
    fun doWeIgnoreMultipleParams() {
        data class G1<out T>(val a: T)
        data class G2<out T>(val a: T)
        data class Wrapper<out T>(val a: Int, val b: G1<T>, val c: G2<T>)

        val factory = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactoryNoEvolution()

        val bytes = SerializationOutput(factory).serializeAndReturnSchema(Wrapper(1, G1("hi"), G2("poop"))).apply { printSchema() }
        printSeparator()
        DeserializationInput(factory2).deserialize(bytes.obj)
    }

    @Test
    fun nestedSerializationOfGenerics() {
        data class G<out T>(val a: T)
        data class Wrapper<out T>(val a: Int, val b: G<T>)

        val factory = testDefaultFactoryNoEvolution()
        val altContextFactory = testDefaultFactoryNoEvolution()
        val ser = SerializationOutput(factory)

        val bytes = ser.serializeAndReturnSchema(G("hi")).apply { printSchema() }

        factory.serializersByDescriptor.printKeyToType()

        assertEquals("hi", DeserializationInput(factory).deserialize(bytes.obj).a)
        assertEquals("hi", DeserializationInput(altContextFactory).deserialize(bytes.obj).a)

        val bytes2 = ser.serializeAndReturnSchema(Wrapper(1, G("hi"))).apply { printSchema() }

        factory.serializersByDescriptor.printKeyToType()

        printSeparator()

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
