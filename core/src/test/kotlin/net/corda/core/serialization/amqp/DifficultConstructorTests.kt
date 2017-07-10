package net.corda.core.serialization.amqp

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

interface I {
    val propA: Int
}

class DifficultConstructorTests {
    var factory = SerializerFactory()
    fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)

    @Test
    fun matchParamToVals(){
        class A (a: Int, b: Int, c: Int) {
            val a = a
            val b = b
            val c = c
        }

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
        class A (a: Int, b: Int, c: Int) {
            val a = a
            val b = b+c
        }

        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(2,3,4)))

        assertTrue(obj.obj is A)
    }

    @Test
    fun mistmatchedValsToParams(){
        class A (a: Int, b: Int) {
            val a = a
            val b = b
            val c = 10
        }

        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(2,3)))

        assertTrue(obj.obj is A)

        assertEquals ((obj.obj as A).a, 2)
        assertEquals ((obj.obj as A).b, 3)
        assertEquals ((obj.obj as A).c, 10)
    }

    @Test
    fun mistmatchedValsToParams2(){
        class A (a: Int) {
            val a = 10 * a
        }

        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(20)))

        assertTrue(obj.obj is A)
    }

    @Test
    fun interfaceAddsAProperty() {
        class A (val a: Int) : I {
            override val propA: Int = 5
        }

        DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(A(2)))
    }

    @Test
    fun inheritsAProperty() {
        open class A (a: Int) {
            val a = a
        }

        class B(a: Int, b: Int) : A (a) {
            val b = b
        }

        val b = B (2,3)

        assertEquals(2, b.a)
        assertEquals(3, b.b)

        DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))
    }

    @Test
    fun inheritsAPropertyAndConstant() {
        open class A (a: Int) {
            val a  = a
            val a2 = 10
        }

        class B(a: Int, b: Int) : A (a) {
            val b = b
        }

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
