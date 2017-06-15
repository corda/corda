package net.corda.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.amqp.*
import org.junit.Test
import kotlin.test.assertEquals

class CompositeMemberCompositeSchemaToClassCarpenterTests {
    private var factory = SerializerFactory()

    fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)

    @Test
    fun nestedInts() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(val a: Int)

        @CordaSerializable
        class B (val a: A, var b: Int)

        var b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)

        val amqpObj = obj.first as B

        assertEquals(testB, amqpObj.b)
        assertEquals(testA, amqpObj.a.a)
        assertEquals(2, obj.second.schema.types.size)
        assert(obj.second.schema.types[0] is CompositeType)
        assert(obj.second.schema.types[1] is CompositeType)

        var amqpSchemaA : CompositeType? = null
        var amqpSchemaB : CompositeType? = null

        for (type in obj.second.schema.types) {
            when (type.name.split ("$").last()) {
                "A" -> amqpSchemaA = type as CompositeType
                "B" -> amqpSchemaB = type as CompositeType
            }
        }

        assert (amqpSchemaA != null)
        assert (amqpSchemaB != null)

        assertEquals(1,     amqpSchemaA?.fields?.size)
        assertEquals("a",   amqpSchemaA!!.fields[0].name)
        assertEquals("int", amqpSchemaA!!.fields[0].type)

        assertEquals(2,     amqpSchemaB?.fields?.size)
        assertEquals("a",   amqpSchemaB!!.fields[0].name)
        assertEquals("net.corda.carpenter.CompositeMemberCompositeSchemaToClassCarpenterTests\$nestedInts\$A",
                amqpSchemaB!!.fields[0].type)
        assertEquals("b",   amqpSchemaB!!.fields[1].name)
        assertEquals("int", amqpSchemaB!!.fields[1].type)

        var ccA = ClassCarpenter().build(ClassCarpenter.Schema(amqpSchemaA.name, amqpSchemaA.carpenterSchema()))
        var ccB = ClassCarpenter().build(ClassCarpenter.Schema(amqpSchemaB.name, amqpSchemaB.carpenterSchema()))

        /*
         * Since A is known to the JVM we can't constuct B with and instance of the carpented A but
         * need to use the defined one above
         */
        val instanceA = ccA.constructors[0].newInstance(testA)
        val instanceB = ccB.constructors[0].newInstance(A (testA), testB)

        assertEquals (ccA.getMethod("getA").invoke(instanceA), amqpObj.a.a)
        assertEquals ((ccB.getMethod("getA").invoke(instanceB) as A).a, amqpObj.a.a)
        assertEquals (ccB.getMethod("getB").invoke(instanceB), amqpObj.b)
    }

}

