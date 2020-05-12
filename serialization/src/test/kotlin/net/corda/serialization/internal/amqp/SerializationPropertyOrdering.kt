package net.corda.serialization.internal.amqp

import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.serialization.internal.amqp.testutils.*
import org.junit.Test
import kotlin.test.assertEquals

class SerializationPropertyOrdering {
    companion object {
        val VERBOSE get() = false

        val registry = TestDescriptorBasedSerializerRegistry()
        val sf = testDefaultFactoryNoEvolution(registry)
    }

    // Force object references to be ued to ensure we go through that code path
    // this test shows (not now it's fixed) a bug whereby deserializing objects
    // would break where refferenced objects were accessed before they'd been
    // processed thanks to the way the blob was deserialized
    @Test(timeout=300_000)
	fun refferenceOrdering() {
        data class Reffed(val c: String, val b: String, val a: String)
        data class User(val b: List<Reffed>, val a: List<Reffed>)

        val r1 = Reffed("do not", "or", "do")
        val r2 = Reffed("do not", "or", "do")
        val l = listOf(r1, r2, r1, r2, r1, r2)

        val u = User(l,l)
        val output = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(u)
        DeserializationInput(sf).deserialize(output.obj)
    }

    @Test(timeout=300_000)
	fun randomOrder() {
        data class C(val c: Int, val d: Int, val b: Int, val e: Int, val a: Int)

        val c = C(3,4,2,5,1)
        val output = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(c)

        // the schema should reflect the serialized order of properties, not the
        // construction order
        assertEquals(1, output.schema.types.size)
        output.schema.types.firstOrNull()?.apply {
            assertEquals(5, (this as CompositeType).fields.size)
            assertEquals("a", this.fields[0].name)
            assertEquals("b", this.fields[1].name)
            assertEquals("c", this.fields[2].name)
            assertEquals("d", this.fields[3].name)
            assertEquals("e", this.fields[4].name)
        }

        // and deserializing it should construct the object as it was  and not in the order prescribed
        // by the serialized form
        val input = DeserializationInput(sf).deserialize(output.obj)
        assertEquals(1, input.a)
        assertEquals(2, input.b)
        assertEquals(3, input.c)
        assertEquals(4, input.d)
        assertEquals(5, input.e)
    }

    @Suppress("UNCHECKED_CAST")
    @Test(timeout=300_000)
	fun randomOrderSetter() {
        data class C(var c: Int, var d: Int, var b: Int, var e: Int, var a: Int) {
            // This will force the serialization engine to use getter / setter
            // instantiation for the object rather than construction
            @ConstructorForDeserialization
            @Suppress("UNUSED")
            constructor() : this(0, 0, 0, 0, 0)
        }

        val c = C()

        c.a = 100
        c.b = 200
        c.c = 300
        c.d = 400
        c.e = 500

        val output = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(c)

        // the schema should reflect the serialized order of properties, not the
        // construction order
        assertEquals(1, output.schema.types.size)
        output.schema.types.firstOrNull()?.apply {
            assertEquals(5, (this as CompositeType).fields.size)
            assertEquals("a", this.fields[0].name)
            assertEquals("b", this.fields[1].name)
            assertEquals("c", this.fields[2].name)
            assertEquals("d", this.fields[3].name)
            assertEquals("e", this.fields[4].name)
        }

        val input = DeserializationInput(sf).deserialize(output.obj)
        assertEquals(100, input.a)
        assertEquals(200, input.b)
        assertEquals(300, input.c)
        assertEquals(400, input.d)
        assertEquals(500, input.e)
    }
}