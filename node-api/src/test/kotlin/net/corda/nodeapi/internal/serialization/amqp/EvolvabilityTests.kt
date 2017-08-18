package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import org.junit.Test
import java.io.File
import java.io.NotSerializableException
import kotlin.test.assertEquals

class EvolvabilityTests {

    @Test
    fun simpleOrderSwapSameType() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.simpleOrderSwapSameType")
        val f = File(path.toURI())

        val A = 1
        val B = 2

        // Original version of the class for the serialised version of this class
        //
        // data class C (val a: Int, val b: Int)
        // val sc = SerializationOutput(sf).serialize(C(A, B))
        // f.writeBytes(sc.bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        data class C (val b: Int, val a: Int)

        val sc2 = f.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun simpleOrderSwapDifferentType() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.simpleOrderSwapDifferentType")
        val f = File(path.toURI())
        val A = 1
        val B = "two"

        // Original version of the class as it was serialised
        //
        // data class C (val a: Int, val b: String)
        // val sc = SerializationOutput(sf).serialize(C(A, B))
        // f.writeBytes(sc.bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        data class C (val b: String, val a: Int)

        val sc2 = f.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun addAdditionalParamNotMandatory() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.addAdditionalParamNotMandatory")
        val f = File(path.toURI())
        val A = 1

        // Original version of the class as it was serialised
        //
        // data class C(val a: Int)
        // val sc = SerializationOutput(sf).serialize(C(A))
        // f.writeBytes(sc.bytes)
        // println ("Path = $path")

        data class C (val a: Int, val b: Int?)

        val sc2 = f.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals (A, deserializedC.a)
        assertEquals (null, deserializedC.b)
    }

    @Test(expected = NotSerializableException::class)
    fun addAdditionalParam() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.addAdditionalParam")
        val f = File(path.toURI())
        @Suppress("UNUSED_VARIABLE")
        val A = 1

        // Original version of the class as it was serialised
        //
        // data class C(val a: Int)
        // val sc = SerializationOutput(sf).serialize(C(A))
        // f.writeBytes(sc.bytes)
        // println ("Path = $path")

        // new version of the class, in this case a new parameter has been added (b)
        data class C (val a: Int, val b: Int)

        val sc2 = f.readBytes()

        // Expected to throw as we can't construct the new type as it contains a newly
        // added parameter that isn't optional, i.e. not nullable and there isn't
        // a compiler that takes the old parameters
        DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))
    }

    @Suppress("UNUSED_VARIABLE")
    @Test
    fun removeParameters() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.removeParameters")
        val f = File(path.toURI())
        val A = 1
        val B = "two"
        val C = "three"
        val D = 4

        // Original version of the class as it was serialised
        //
        // data class CC(val a: Int, val b: String, val c: String, val d: Int)
        // val scc = SerializationOutput(sf).serialize(CC(A, B, C, D))
        // f.writeBytes(scc.bytes)
        // println ("Path = $path")

        data class CC (val b: String, val d: Int)

        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals (B, deserializedCC.b)
        assertEquals (D, deserializedCC.d)
    }

    @Suppress("UNUSED_VARIABLE")
    @Test
    fun addAndRemoveParameters() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.addAndRemoveParameters")
        val f = File(path.toURI())
        val A = 1
        val B = "two"
        val C = "three"
        val D = 4
        val E = null

        // Original version of the class as it was serialised
        //
        // data class CC(val a: Int, val b: String, val c: String, val d: Int)
        // val scc = SerializationOutput(sf).serialize(CC(A, B, C, D))
        // f.writeBytes(scc.bytes)
        // println ("Path = $path")

        data class CC(val a: Int, val e: Boolean?, val d: Int)

        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(E, deserializedCC.e)
        assertEquals(D, deserializedCC.d)
    }

    @Test
    fun addMandatoryFieldWithAltConstructor() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.addMandatoryFieldWithAltConstructor")
        val f = File(path.toURI())
        val A = 1

        // Original version of the class as it was serialised
        //
        // data class CC(val a: Int)
        // val scc = SerializationOutput(sf).serialize(CC(A))
        // f.writeBytes(scc.bytes)
        // println ("Path = $path")

        @Suppress("UNUSED")
        data class CC (val a: Int, val b: String) {
            constructor (a: Int) : this (a, "hello")
        }

        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals (A, deserializedCC.a)
        assertEquals ("hello", deserializedCC.b)
    }

}