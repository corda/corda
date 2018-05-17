package net.corda.nodeapi.internal.serialization.amqp

import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.assertEquals
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.nodeapi.internal.serialization.amqp.testutils.deserialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.nodeapi.internal.serialization.amqp.testutils.serialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import org.junit.Test
import org.apache.qpid.proton.amqp.Symbol
import org.assertj.core.api.Assertions
import java.io.NotSerializableException
import java.util.concurrent.ConcurrentHashMap
import java.util.*

class PrivatePropertyTests {
    private val factory = testDefaultFactoryNoEvolution()

    companion object {
        val fields : Map<String, java.lang.reflect.Field> = mapOf (
                "serializersByDesc" to SerializerFactory::class.java.getDeclaredField("serializersByDescriptor")).apply {
            this.values.forEach {
                it.isAccessible = true
            }
        }
    }

    @Test
    fun testWithOnePrivateProperty() {
        data class C(private val b: String)

        val c1 = C("Pants are comfortable sometimes")
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertEquals(c1, c2)
    }

    @Test
    fun testWithOnePrivatePropertyBoolean() {
        data class C(private val b: Boolean)

        C(false).apply {
            assertEquals(this, DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(this)))
        }
    }

    @Test
    fun testWithOnePrivatePropertyNullableNotNull() {
        data class C(private val b: String?)

        val c1 = C("Pants are comfortable sometimes")
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertEquals(c1, c2)
    }

    @Test
    fun testWithOnePrivatePropertyNullableNull() {
        data class C(private val b: String?)

        val c1 = C(null)
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertEquals(c1, c2)
    }

    @Test
    fun testWithOnePublicOnePrivateProperty() {
        data class C(val a: Int, private val b: Int)

        val c1 = C(1, 2)
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertEquals(c1, c2)
    }

    @Test
    fun testWithInheritance() {
        open class B(val a: String, protected val b: String)
        class D (a: String, b: String) : B (a, b) {
            override fun equals(other: Any?): Boolean = when (other) {
                is D -> other.a == a && other.b == b
                else -> false
            }
            override fun hashCode(): Int = Objects.hash(a, b)
        }

        val d1 = D("clump", "lump")
        val d2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(d1))

        assertEquals(d1, d2)
    }

    @Test
    fun testMultiArgSetter() {
        @Suppress("UNUSED")
        data class C(private var a: Int, var b: Int) {
            // This will force the serialization engine to use getter / setter
            // instantiation for the object rather than construction
            @ConstructorForDeserialization
            constructor() : this(0, 0)

            fun setA(a: Int, b: Int) { this.a = a }
            fun getA() = a
        }

        val c1 = C(33, 44)
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertEquals(0, c2.getA())
        assertEquals(44, c2.b)
    }

    @Test
    fun testBadTypeArgSetter() {
        @Suppress("UNUSED")
        data class C(private var a: Int, val b: Int) {
            @ConstructorForDeserialization
            constructor() : this(0, 0)

            fun setA(a: String) { this.a = a.toInt() }
            fun getA() = a
        }

        val c1 = C(33, 44)
        Assertions.assertThatThrownBy {
            SerializationOutput(factory).serialize(c1)
        }.isInstanceOf(NotSerializableException::class.java).hasMessageContaining(
                "Defined setter for parameter a takes parameter of type class java.lang.String " +
                        "yet underlying type is int ")
    }

    @Test
    fun testWithOnePublicOnePrivateProperty2() {
        data class C(val a: Int, private val b: Int)

        val c1 = C(1, 2)
        val schemaAndBlob = SerializationOutput(factory).serializeAndReturnSchema(c1)
        assertEquals(1, schemaAndBlob.schema.types.size)

        @Suppress("UNCHECKED_CAST")
        val serializersByDescriptor = fields["serializersByDesc"]?.get(factory) as ConcurrentHashMap<Any, AMQPSerializer<Any>>

        val schemaDescriptor = schemaAndBlob.schema.types.first().descriptor.name
        serializersByDescriptor.filterKeys { (it as Symbol) == schemaDescriptor }.values.apply {
            assertEquals(1, this.size)
            assertTrue(this.first() is ObjectSerializer)
            val propertySerializers = (this.first() as ObjectSerializer).propertySerializers.serializationOrder.map { it.getter }
            assertEquals(2, propertySerializers.size)
            // a was public so should have a synthesised getter
            assertTrue(propertySerializers[0].propertyReader is PublicPropertyReader)

            // b is private and thus won't have teh getter so we'll have reverted
            // to using reflection to remove the inaccessible property
            assertTrue(propertySerializers[1].propertyReader is PrivatePropertyReader)
        }
    }

    @Test
    fun testGetterMakesAPublicReader() {
        data class C(val a: Int, private val b: Int) {
            @Suppress("UNUSED")
            fun getB() = b
        }

        val c1 = C(1, 2)
        val schemaAndBlob = SerializationOutput(factory).serializeAndReturnSchema(c1)
        assertEquals(1, schemaAndBlob.schema.types.size)

        @Suppress("UNCHECKED_CAST")
        val serializersByDescriptor = fields["serializersByDesc"]?.get(factory) as ConcurrentHashMap<Any, AMQPSerializer<Any>>

        val schemaDescriptor = schemaAndBlob.schema.types.first().descriptor.name
        serializersByDescriptor.filterKeys { (it as Symbol) == schemaDescriptor }.values.apply {
            assertEquals(1, this.size)
            assertTrue(this.first() is ObjectSerializer)
            val propertySerializers = (this.first() as ObjectSerializer).propertySerializers.serializationOrder.map { it.getter }
            assertEquals(2, propertySerializers.size)

            // as before, a is public so we'll use the getter method
            assertTrue(propertySerializers[0].propertyReader is PublicPropertyReader)

            // the getB() getter explicitly added means we should use the "normal" public
            // method reader rather than the private oen
            assertTrue(propertySerializers[1].propertyReader is PublicPropertyReader)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testNested() {
        data class Inner(private val a: Int)
        data class Outer(private val i: Inner)

        val c1 = Outer(Inner(1010101))
        val output = SerializationOutput(factory).serializeAndReturnSchema(c1)
        println (output.schema)

        val serializersByDescriptor = fields["serializersByDesc"]!!.get(factory) as ConcurrentHashMap<Any, AMQPSerializer<Any>>

        // Inner and Outer
        assertEquals(2, serializersByDescriptor.size)

        val c2 = DeserializationInput(factory).deserialize(output.obj)

        assertEquals(c1, c2)
    }

    //
    // Reproduces CORDA-1134
    //
    @Suppress("UNCHECKED_CAST")
    @Test
    fun allCapsProprtyNotPrivate() {
        data class C (val CCC: String)

        val output = SerializationOutput(factory).serializeAndReturnSchema(C("this is nice"))

        val serializersByDescriptor = fields["serializersByDesc"]!!.get(factory) as ConcurrentHashMap<Any, AMQPSerializer<Any>>

        val schemaDescriptor = output.schema.types.first().descriptor.name
        serializersByDescriptor.filterKeys { (it as Symbol) == schemaDescriptor }.values.apply {
            assertEquals(1, size)

            assertTrue(this.first() is ObjectSerializer)
            val propertySerializers = (this.first() as ObjectSerializer).propertySerializers.serializationOrder.map { it.getter }

            // CCC is the only property to be serialised
            assertEquals(1, propertySerializers.size)

            // and despite being all caps it should still be a public getter
            assertTrue(propertySerializers[0].propertyReader is PublicPropertyReader)
        }
    }

}