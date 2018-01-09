package net.corda.nodeapi.internal.serialization.amqp

import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.apache.qpid.proton.amqp.Symbol
import java.util.concurrent.ConcurrentHashMap

class PrivatePropertyTests {
    private val factory = testDefaultFactory()

    @Test
    fun testWithOnePrivateProperty() {
        data class C(private val b: String)

        val c1 = C("Pants are comfortable sometimes")
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertEquals(c1, c2)
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
    fun testWithOnePublicOnePrivateProperty2() {
        data class C(val a: Int, private val b: Int)

        val c1 = C(1, 2)
        val schemaAndBlob = SerializationOutput(factory).serializeAndReturnSchema(c1)
        assertEquals(1, schemaAndBlob.schema.types.size)

        val field = SerializerFactory::class.java.getDeclaredField("serializersByDescriptor")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val serializersByDescriptor = field.get(factory) as ConcurrentHashMap<Any, AMQPSerializer<Any>>

        val schemaDescriptor = schemaAndBlob.schema.types.first().descriptor.name
        serializersByDescriptor.filterKeys { (it as Symbol) == schemaDescriptor }.values.apply {
            assertEquals(1, this.size)
            assertTrue(this.first() is ObjectSerializer)
            val propertySerializers = (this.first() as ObjectSerializer).propertySerializers.getters.toList()
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

        val field = SerializerFactory::class.java.getDeclaredField("serializersByDescriptor")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val serializersByDescriptor = field.get(factory) as ConcurrentHashMap<Any, AMQPSerializer<Any>>

        val schemaDescriptor = schemaAndBlob.schema.types.first().descriptor.name
        serializersByDescriptor.filterKeys { (it as Symbol) == schemaDescriptor }.values.apply {
            assertEquals(1, this.size)
            assertTrue(this.first() is ObjectSerializer)
            val propertySerializers = (this.first() as ObjectSerializer).propertySerializers.getters.toList()
            assertEquals(2, propertySerializers.size)

            // as before, a is public so we'll use the getter method
            assertTrue(propertySerializers[0].propertyReader is PublicPropertyReader)

            // the getB() getter explicitly added means we should use the "normal" public
            // method reader rather than the private oen
            assertTrue(propertySerializers[1].propertyReader is PublicPropertyReader)
        }
    }

    @Test
    fun testNested() {
        data class Inner(private val a: Int)
        data class Outer(private val i: Inner)

        val c1 = Outer(Inner(1010101))
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertEquals(c1, c2)
    }
}