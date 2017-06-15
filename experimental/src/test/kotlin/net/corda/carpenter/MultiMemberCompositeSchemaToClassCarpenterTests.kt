package net.corda.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.amqp.*
import org.junit.Test
import kotlin.test.assertEquals

class MultiMemberCompositeSchemaToClassCarpenterTests {
    private var factory = SerializerFactory()

    fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)

    @Test
    fun twoInts() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(val a: Int, val b: Int)

        var a = A(testA, testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(a))

        assert(obj.first is A)
        val amqpObj = obj.first as A

        assertEquals(testA, amqpObj.a)
        assertEquals(testB, amqpObj.b)
        assertEquals(1, obj.second.schema.types.size)
        assert(obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals(2,     amqpSchema.fields.size)
        assertEquals("a",   amqpSchema.fields[0].name)
        assertEquals("int", amqpSchema.fields[0].type)
        assertEquals("b",   amqpSchema.fields[1].name)
        assertEquals("int", amqpSchema.fields[1].type)

        var pinochio = ClassCarpenter().build(ClassCarpenter.Schema(amqpSchema.name, amqpSchema.carpenterSchema()))

        val p = pinochio.constructors[0].newInstance(testA, testB)

        assertEquals(pinochio.getMethod("getA").invoke(p), amqpObj.a)
        assertEquals(pinochio.getMethod("getB").invoke(p), amqpObj.b)
    }

    @Test
    fun intAndStr() {
        val testA = 10
        val testB = "twenty"

        @CordaSerializable
        data class A(val a: Int, val b: String)

        var a = A(testA, testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(a))

        assert(obj.first is A)
        val amqpObj = obj.first as A

        assertEquals(testA, amqpObj.a)
        assertEquals(testB, amqpObj.b)
        assertEquals(1, obj.second.schema.types.size)
        assert(obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals(2,        amqpSchema.fields.size)
        assertEquals("a",      amqpSchema.fields[0].name)
        assertEquals("int",    amqpSchema.fields[0].type)
        assertEquals("b",      amqpSchema.fields[1].name)
        assertEquals("string", amqpSchema.fields[1].type)

        var pinochio = ClassCarpenter().build(ClassCarpenter.Schema(amqpSchema.name, amqpSchema.carpenterSchema()))

        val p = pinochio.constructors[0].newInstance(testA, testB)

        assertEquals(pinochio.getMethod("getA").invoke(p), amqpObj.a)
        assertEquals(pinochio.getMethod("getB").invoke(p), amqpObj.b)
    }

}

