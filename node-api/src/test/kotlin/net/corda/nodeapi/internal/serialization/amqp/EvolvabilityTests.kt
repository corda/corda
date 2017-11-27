package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SerializedBytes
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import org.junit.Test
import java.io.File
import java.io.NotSerializableException
import java.net.URI
import kotlin.test.assertEquals

// To regenerate any of the binary test files do the following
//
//  0. set localPath accordingly
//  1. Uncomment the code where the original form of the class is defined in the test
//  2. Comment out the rest of the test
//  3. Run the test
//  4. Using the printed path copy that file to the resources directory
//  5. Comment back out the generation code and uncomment the actual test
class EvolvabilityTests {
    // When regenerating the test files this needs to be set to the file system location of the resource files
    var localPath = projectRootDir.toUri().resolve(
            "node-api/src/test/resources/net/corda/nodeapi/internal/serialization/amqp")

    @Test
    fun simpleOrderSwapSameType() {
        val sf = testDefaultFactory()
        val resource= "EvolvabilityTests.simpleOrderSwapSameType"

        val A = 1
        val B = 2

        // Original version of the class for the serialised version of this class
        // data class C (val a: Int, val b: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A, B)).bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        data class C(val b: Int, val a: Int)

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())

        val sc2 = f.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun simpleOrderSwapDifferentType() {
        val sf = testDefaultFactory()
        val A = 1
        val B = "two"
        val resource = "EvolvabilityTests.simpleOrderSwapDifferentType"

        // Original version of the class as it was serialised
        // data class C (val a: Int, val b: String)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A, B)).bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        data class C(val b: String, val a: Int)

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun addAdditionalParamNotMandatory() {
        val sf = testDefaultFactory()
        val A = 1
        val resource = "EvolvabilityTests.addAdditionalParamNotMandatory"

        // Original version of the class as it was serialised
        // data class C(val a: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A)).bytes)

        data class C(val a: Int, val b: Int?)

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(null, deserializedC.b)
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
        data class C(val a: Int, val b: Int)

        val sc2 = f.readBytes()

        // Expected to throw as we can't construct the new type as it contains a newly
        // added parameter that isn't optional, i.e. not nullable and there isn't
        // a constructor that takes the old parameters
        DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))
    }

    @Suppress("UNUSED_VARIABLE")
    @Test
    fun removeParameters() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.removeParameters"
        val A = 1
        val B = "two"
        val C = "three"
        val D = 4

        // Original version of the class as it was serialised
        // data class CC(val a: Int, val b: String, val c: String, val d: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C, D)).bytes)

        data class CC(val b: String, val d: Int)

        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.removeParameters")
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(B, deserializedCC.b)
        assertEquals(D, deserializedCC.d)
    }

    @Suppress("UNUSED_VARIABLE")
    @Test
    fun addAndRemoveParameters() {
        val sf = testDefaultFactory()
        val A = 1
        val B = "two"
        val C = "three"
        val D = 4
        val E = null

        val resource = "EvolvabilityTests.addAndRemoveParameters"

        // Original version of the class as it was serialised
        // data class CC(val a: Int, val b: String, val c: String, val d: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C, D)).bytes)

        data class CC(val a: Int, val e: Boolean?, val d: Int)

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(E, deserializedCC.e)
        assertEquals(D, deserializedCC.d)
    }

    @Test
    fun addMandatoryFieldWithAltConstructor() {
        val sf = testDefaultFactory()
        val A = 1
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructor"

        // Original version of the class as it was serialised
        // data class CC(val a: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A)).bytes)

        @Suppress("UNUSED")
        data class CC(val a: Int, val b: String) {
            @DeprecatedConstructorForDeserialization(1)
            constructor (a: Int) : this(a, "hello")
        }

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals("hello", deserializedCC.b)
    }

    @Test(expected = NotSerializableException::class)
    @Suppress("UNUSED")
    fun addMandatoryFieldWithAltConstructorUnAnnotated() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource(
                "EvolvabilityTests.addMandatoryFieldWithAltConstructorUnAnnotated")
        val f = File(path.toURI())
        @Suppress("UNUSED_VARIABLE")
        val A = 1

        // Original version of the class as it was serialised
        //
        // data class CC(val a: Int)
        // val scc = SerializationOutput(sf).serialize(CC(A))
        // f.writeBytes(scc.bytes)
        // println ("Path = $path")

        data class CC(val a: Int, val b: String) {
            // constructor annotation purposefully omitted
            constructor (a: Int) : this(a, "hello")
        }

        // we expect this to throw as we should not find any constructors
        // capable of dealing with this
        DeserializationInput(sf).deserialize(SerializedBytes<CC>(f.readBytes()))
    }

    @Test
    fun addMandatoryFieldWithAltReorderedConstructor() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltReorderedConstructor"
        val A = 1
        val B = 100
        val C = "This is not a banana"

        // Original version of the class as it was serialised
        // data class CC(val a: Int, val b: Int, val c: String)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C)).bytes)

        @Suppress("UNUSED")
        data class CC(val a: Int, val b: Int, val c: String, val d: String) {
            // ensure none of the original parameters align with the initial
            // construction order
            @DeprecatedConstructorForDeserialization(1)
            constructor (c: String, a: Int, b: Int) : this(a, b, c, "wibble")
        }

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(B, deserializedCC.b)
        assertEquals(C, deserializedCC.c)
        assertEquals("wibble", deserializedCC.d)
    }

    @Test
    fun addMandatoryFieldWithAltReorderedConstructorAndRemoval() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltReorderedConstructorAndRemoval"
        val A = 1
        @Suppress("UNUSED_VARIABLE")
        val B = 100
        val C = "This is not a banana"

        // Original version of the class as it was serialised
        // data class CC(val a: Int, val b: Int, val c: String)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C)).bytes)

        // b is removed, d is added
        data class CC(val a: Int, val c: String, val d: String) {
            // ensure none of the original parameters align with the initial
            // construction order
            @Suppress("UNUSED")
            @DeprecatedConstructorForDeserialization(1)
            constructor (c: String, a: Int) : this(a, c, "wibble")
        }

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(C, deserializedCC.c)
        assertEquals("wibble", deserializedCC.d)
    }

    @Test
    fun multiVersion() {
        val sf = testDefaultFactory()
        val resource1 = "EvolvabilityTests.multiVersion.1"
        val resource2 = "EvolvabilityTests.multiVersion.2"
        val resource3 = "EvolvabilityTests.multiVersion.3"

        val a = 100
        val b = 200
        val c = 300
        val d = 400

        // Original version of the class as it was serialised
        //
        // Version 1:
        // data class C (val a: Int, val b: Int)
        // File(URI("$localPath/$resource1")).writeBytes(SerializationOutput(sf).serialize(C(a, b)).bytes)
        //
        // Version 2 - add param c
        // data class C (val c: Int, val b: Int, val a: Int)
        // File(URI("$localPath/$resource2")).writeBytes(SerializationOutput(sf).serialize(C(c, b, a)).bytes)
        //
        // Version 3 - add param d
        // data class C (val b: Int, val c: Int, val d: Int, val a: Int)
        // File(URI("$localPath/$resource3")).writeBytes(SerializationOutput(sf).serialize(C(b, c, d, a)).bytes)

        @Suppress("UNUSED")
        data class C(val e: Int, val c: Int, val b: Int, val a: Int, val d: Int) {
            @DeprecatedConstructorForDeserialization(1)
            constructor (b: Int, a: Int) : this(-1, -1, b, a, -1)

            @DeprecatedConstructorForDeserialization(2)
            constructor (a: Int, c: Int, b: Int) : this(-1, c, b, a, -1)

            @DeprecatedConstructorForDeserialization(3)
            constructor (a: Int, b: Int, c: Int, d: Int) : this(-1, c, b, a, d)
        }

        val path1 = EvolvabilityTests::class.java.getResource(resource1)
        val path2 = EvolvabilityTests::class.java.getResource(resource2)
        val path3 = EvolvabilityTests::class.java.getResource(resource3)

        val sb1 = File(path1.toURI()).readBytes()
        val db1 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb1))

        assertEquals(a, db1.a)
        assertEquals(b, db1.b)
        assertEquals(-1, db1.c)
        assertEquals(-1, db1.d)
        assertEquals(-1, db1.e)

        val sb2 = File(path2.toURI()).readBytes()
        val db2 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb2))

        assertEquals(a, db2.a)
        assertEquals(b, db2.b)
        assertEquals(c, db2.c)
        assertEquals(-1, db2.d)
        assertEquals(-1, db2.e)

        val sb3 = File(path3.toURI()).readBytes()
        val db3 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb3))

        assertEquals(a, db3.a)
        assertEquals(b, db3.b)
        assertEquals(c, db3.c)
        assertEquals(d, db3.d)
        assertEquals(-1, db3.e)
    }

    @Test
    fun changeSubType() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.changeSubType"
        val oa = 100
        val ia = 200

        // Original version of the class as it was serialised
        // data class Inner (val a: Int)
        // data class Outer (val a: Int, val b: Inner)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(Outer(oa, Inner (ia))).bytes)

        // Add a parameter to inner but keep outer unchanged
        data class Inner(val a: Int, val b: String?)
        data class Outer(val a: Int, val b: Inner)

        val path = EvolvabilityTests::class.java.getResource(resource)
        val f = File(path.toURI())
        val sc2 = f.readBytes()
        val outer = DeserializationInput(sf).deserialize(SerializedBytes<Outer>(sc2))

        assertEquals(oa, outer.a)
        assertEquals(ia, outer.b.a)
        assertEquals(null, outer.b.b)
    }

    @Test
    fun multiVersionWithRemoval() {
        val sf = testDefaultFactory()

        val resource1 = "EvolvabilityTests.multiVersionWithRemoval.1"
        val resource2 = "EvolvabilityTests.multiVersionWithRemoval.2"
        val resource3 = "EvolvabilityTests.multiVersionWithRemoval.3"

        @Suppress("UNUSED_VARIABLE")
        val a = 100
        val b = 200
        val c = 300
        val d = 400
        val e = 500
        val f = 600

        // Original version of the class as it was serialised
        //
        // Version 1:
        // data class C (val a: Int, val b: Int, val c: Int)
        // File(URI("$localPath/$resource1")).writeBytes(SerializationOutput(sf).serialize(C(a, b, c)).bytes)
        //
        // Version 2 - remove property a, add property e
        // data class C (val b: Int, val c: Int, val d: Int, val e: Int)
        // File(URI("$localPath/$resource2")).writeBytes(SerializationOutput(sf).serialize(C(b, c, d, e)).bytes)
        //
        // Version 3 - add param d
        // data class C (val b: Int, val c: Int, val d: Int, val e: Int, val f: Int)
        // File(URI("$localPath/$resource3")).writeBytes(SerializationOutput(sf).serialize(C(b, c, d, e, f)).bytes)

        @Suppress("UNUSED")
        data class C(val b: Int, val c: Int, val d: Int, val e: Int, val f: Int, val g: Int) {
            @DeprecatedConstructorForDeserialization(1)
            constructor (b: Int, c: Int) : this(b, c, -1, -1, -1, -1)

            @DeprecatedConstructorForDeserialization(2)
            constructor (b: Int, c: Int, d: Int) : this(b, c, d, -1, -1, -1)

            @DeprecatedConstructorForDeserialization(3)
            constructor (b: Int, c: Int, d: Int, e: Int) : this(b, c, d, e, -1, -1)

            @DeprecatedConstructorForDeserialization(4)
            constructor (b: Int, c: Int, d: Int, e: Int, f: Int) : this(b, c, d, e, f, -1)
        }

        val path1 = EvolvabilityTests::class.java.getResource(resource1)
        val path2 = EvolvabilityTests::class.java.getResource(resource2)
        val path3 = EvolvabilityTests::class.java.getResource(resource3)

        val sb1 = File(path1.toURI()).readBytes()
        val db1 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb1))

        assertEquals(b, db1.b)
        assertEquals(c, db1.c)
        assertEquals(-1, db1.d) // must not be set by calling constructor 2 by mistake
        assertEquals(-1, db1.e)
        assertEquals(-1, db1.f)
        assertEquals(-1, db1.g)

        val sb2 = File(path2.toURI()).readBytes()
        val db2 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb2))

        assertEquals(b, db2.b)
        assertEquals(c, db2.c)
        assertEquals(d, db2.d)
        assertEquals(e, db2.e)
        assertEquals(-1, db2.f)
        assertEquals(-1, db1.g)

        val sb3 = File(path3.toURI()).readBytes()
        val db3 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb3))

        assertEquals(b, db3.b)
        assertEquals(c, db3.c)
        assertEquals(d, db3.d)
        assertEquals(e, db3.e)
        assertEquals(f, db3.f)
        assertEquals(-1, db3.g)
    }
}