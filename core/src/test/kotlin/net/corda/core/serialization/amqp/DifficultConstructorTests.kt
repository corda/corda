package net.corda.core.serialization.amqp

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

interface I {
    val propA: Int
}

@Suppress("UNUSED")
interface II {
    val propA: Int
        get() = 50
}

@Suppress("UNUSED")
class DifficultConstructorTests {
    var factory = SerializerFactory()
    fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)
    private fun pname() = this.javaClass.`package`.name

    @Test
    fun matchParamToVals(){
        class A (val a: Int, val b: Int, val c: Int)

        val a = A (2, 3, 4)
        assertEquals (a.a, 2)
        assertEquals (a.b, 3)
        assertEquals (a.c, 4)

        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(a))

        assertTrue(obj.obj is A)

        assertEquals ((obj.obj as A).a, 2)
        assertEquals ((obj.obj as A).b, 3)
        assertEquals ((obj.obj as A).c, 4)
    }

    @Test (expected=java.io.NotSerializableException::class)
    fun mistmatchedParamsToVals(){
        class A (val a: Int, b: Int, c: Int) {
            val b = b+c
        }

        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(2,3,4)))

        assertTrue(obj.obj is A)
    }

    @Test
    fun mistmatchedValsToParams(){
        class A (val a: Int, val b: Int) {
            val c = 10
        }

        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(2,3)))

        assertTrue(obj.obj is A)

        assertEquals ((obj.obj as A).a, 2)
        assertEquals ((obj.obj as A).b, 3)
        assertEquals ((obj.obj as A).c, 10)

        /*
         * whilst the constructor only uses two params we should have serislaised the third as
         * it's possible the other end won't have the class on it's ClassPath anf will tus have
         * to carpent one up, in which case knowing additional parameters may be useful
         */
        assertEquals (1, obj.envelope.schema.types.size)
        assertTrue (obj.envelope.schema.types.first() is CompositeType)
        val fields = obj.envelope.schema.types.first() as CompositeType
        assertEquals(3, fields.fields.size)
        assertNotEquals(null, fields.fields.find {it.name == "a"})
        assertNotEquals(null, fields.fields.find {it.name == "b"})
        assertNotEquals(null, fields.fields.find {it.name == "c"})
    }


    @Test
    fun interfaceAddsAPropertyAndSets() {
        class A (val a: Int, override val propA: Int) : I

        DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(2, 10)))
    }

    @Test
    fun interfaceAddsAProperty() {
        class A (val a: Int) : I {
            override val propA: Int = 5
        }

        DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(2)))
    }

    @Test
    fun interfaceHasDefaultProperty() {
        class A (val a: Int): II

        val envelope = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(100))).envelope

        assertEquals (2, envelope.schema.types.size)

        val aSchema = envelope.schema.types.find {
                it.name == "${pname()}.DifficultConstructorTests\$interfaceHasDefaultProperty\$A" } as CompositeType

        val iSchema = envelope.schema.types.find {
                it.name == "${pname()}.II" } as CompositeType

        assertEquals(2, aSchema.fields.size)

        assertNotEquals(null, aSchema.fields.find { it.name == "a" })
        assertNotEquals(null, aSchema.fields.find { it.name == "propA" })

        assertEquals(1, iSchema.fields.size)
        assertNotEquals(null, iSchema.fields.find { it.name == "propA" })

    }

    @Test
    fun inheritsAProperty() {
        open class A (val a: Int)

        class B(a: Int, val b: Int) : A (a)

        val b = B (2,3)

        assertEquals(2, b.a)
        assertEquals(3, b.b)

        DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))
    }

    @Test
    fun inheritsAPropertyAndConstant() {
        open class A (val a: Int) {
            val a2 = 10
        }

        class B(a: Int, val b: Int) : A (a)

        val b = B (2,3)

        assertEquals(2, b.a)
        assertEquals(10, b.a2)
        assertEquals(3, b.b)

        val res = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))

        assertEquals(2, (res.obj as B).a)
        assertEquals(10, (res.obj as B).a2)
        assertEquals(3, (res.obj as B).b)
    }
}
