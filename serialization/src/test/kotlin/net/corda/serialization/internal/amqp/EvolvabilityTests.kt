package net.corda.serialization.internal.amqp

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sign
import net.corda.core.flows.NotarisationRequest
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SerializableCalculatedProperty
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.amqp.custom.InstantSerializer
import net.corda.serialization.internal.amqp.testutils.ProjectStructure.projectRootDir
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.serialize
import net.corda.serialization.internal.amqp.testutils.serializeAndReturnSchema
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import net.corda.serialization.internal.amqp.testutils.testName
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertNotSame
import java.io.File
import java.io.NotSerializableException
import java.math.BigInteger
import java.net.URI
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.fail

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
    @Suppress("UNUSED")
    var localPath: URI = projectRootDir.toUri().resolve(
            "serialization/src/test/resources/net/corda/serialization/internal/amqp")

    companion object {
        private val DUMMY_NOTARY_NAME = CordaX500Name("Notary Service", "Zurich", "CH")
        private val DUMMY_NOTARY_PARTY = Party(DUMMY_NOTARY_NAME, Crypto.deriveKeyPairFromEntropy(Crypto.DEFAULT_SIGNATURE_SCHEME, BigInteger.valueOf(20)).public)
    }

    @Test(timeout=300_000)
	fun simpleOrderSwapSameType() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.simpleOrderSwapSameType"

        val A = 1
        val B = 2

        // Original version of the class for the serialised version of this class
        // data class C (val a: Int, val b: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A, B)).bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        data class C(val b: Int, val a: Int)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test(timeout=300_000)
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

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test(timeout=300_000)
	fun addAdditionalParamNotMandatory() {
        val sf = testDefaultFactory()
        val A = 1
        val resource = "EvolvabilityTests.addAdditionalParamNotMandatory"

        // Original version of the class as it was serialised
        // data class C(val a: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A)).bytes)

        data class C(val a: Int, val b: Int?)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(null, deserializedC.b)
    }

    @Test(expected = NotSerializableException::class, timeout=300_000)
    fun addAdditionalParam() {
        val sf = testDefaultFactory()
        val url = EvolvabilityTests::class.java.getResource("EvolvabilityTests.addAdditionalParam")
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

        val sc2 = url.readBytes()

        // Expected to throw as we can't construct the new type as it contains a newly
        // added parameter that isn't optional, i.e. not nullable and there isn't
        // a constructor that takes the old parameters
        DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))
    }

    @Suppress("UNUSED_VARIABLE")
    @Test(timeout=300_000)
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

        val url = EvolvabilityTests::class.java.getResource("EvolvabilityTests.removeParameters")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(B, deserializedCC.b)
        assertEquals(D, deserializedCC.d)
    }

    @Suppress("UNUSED_VARIABLE")
    @Test(timeout=300_000)
	fun removeParameterWithCalculatedParameter() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.removeParameterWithCalculatedParameter"

        // Original version of the class as it was serialised
        // data class CC(val a: Int, val b: String, val c: String, val d: Int) {
        //     @get:SerializableCalculatedProperty
        //     val e: String get() = "$b $c"
        // }
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(1, "hello", "world", 2)).bytes)


        data class CC(val b: String, val d: Int) {
            @get:SerializableCalculatedProperty
            val e: String get() = "$b sailor"
        }

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals("hello", deserializedCC.b)
        assertEquals(2, deserializedCC.d)
        assertEquals("hello sailor", deserializedCC.e)
    }

    @Suppress("UNUSED_VARIABLE")
    @Test(timeout=300_000)
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

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(E, deserializedCC.e)
        assertEquals(D, deserializedCC.d)
    }

    @Test(timeout=300_000)
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

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals("hello", deserializedCC.b)
    }

    @Test(timeout=300_000)
	fun addMandatoryFieldWithAltConstructorForceReorder() {
        val sf = testDefaultFactory()
        val z = 30
        val y = 20
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructorForceReorder"

        // Original version of the class as it was serialised
        // data class CC(val z: Int, val y: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(z, y)).bytes)

        @Suppress("UNUSED")
        data class CC(val z: Int, val y: Int, val a: String) {
            @DeprecatedConstructorForDeserialization(1)
            constructor (z: Int, y: Int) : this(z, y, "10")
        }

        val url = EvolvabilityTests::class.java.getResource(resource)
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(url.readBytes()))

        assertEquals("10", deserializedCC.a)
        assertEquals(y, deserializedCC.y)
        assertEquals(z, deserializedCC.z)
    }

    @Test(timeout=300_000)
	fun moreComplexNonNullWithReorder() {
        val resource = "${javaClass.simpleName}.${testName()}"

        data class NetworkParametersExample(
                val minimumPlatformVersion: Int,
                val notaries: List<String>,
                val maxMessageSize: Int,
                val maxTransactionSize: Int,
                val modifiedTime: Instant,
                val epoch: Int,
                val whitelistedContractImplementations: Map<String, List<Int>>,
                /* to regenerate test class, comment out this element */
                val eventHorizon: Int
        ) {
            // when regenerating test class this won't be required
            @DeprecatedConstructorForDeserialization(1)
            @Suppress("UNUSED")
            constructor (
                    minimumPlatformVersion: Int,
                    notaries: List<String>,
                    maxMessageSize: Int,
                    maxTransactionSize: Int,
                    modifiedTime: Instant,
                    epoch: Int,
                    whitelistedContractImplementations: Map<String, List<Int>>
            ) : this(minimumPlatformVersion,
                    notaries,
                    maxMessageSize,
                    maxTransactionSize,
                    modifiedTime,
                    epoch,
                    whitelistedContractImplementations,
                    Int.MAX_VALUE)
        }

        val factory = testDefaultFactory().apply {
            register(InstantSerializer(this))
        }

        // Uncomment to regenerate test case
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(factory).serialize(
        //         NetworkParametersExample(
        //                 10,
        //                 listOf("Notary1", "Notary2"),
        //                 100,
        //                 10,
        //                 Instant.now(),
        //                 9,
        //                 mapOf("A" to listOf(1, 2, 3), "B" to listOf (4, 5, 6)))).bytes)

        val url = EvolvabilityTests::class.java.getResource(resource)
        DeserializationInput(factory).deserialize(SerializedBytes<NetworkParametersExample>(url.readBytes()))
    }

    @Test(expected = NotSerializableException::class, timeout=300_000)
    @Suppress("UNUSED")
    fun addMandatoryFieldWithAltConstructorUnAnnotated() {
        val sf = testDefaultFactory()
        val url = EvolvabilityTests::class.java.getResource(
                "EvolvabilityTests.addMandatoryFieldWithAltConstructorUnAnnotated")
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
        DeserializationInput(sf).deserialize(SerializedBytes<CC>(url.readBytes()))
    }

    @Test(timeout=300_000)
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

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(B, deserializedCC.b)
        assertEquals(C, deserializedCC.c)
        assertEquals("wibble", deserializedCC.d)
    }

    @Test(timeout=300_000)
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

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(C, deserializedCC.c)
        assertEquals("wibble", deserializedCC.d)
    }

    @Test(timeout=300_000)
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

        val url1 = EvolvabilityTests::class.java.getResource(resource1)
        val url2 = EvolvabilityTests::class.java.getResource(resource2)
        val url3 = EvolvabilityTests::class.java.getResource(resource3)

        val sb1 = url1.readBytes()
        val db1 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb1))

        assertEquals(a, db1.a)
        assertEquals(b, db1.b)
        assertEquals(-1, db1.c)
        assertEquals(-1, db1.d)
        assertEquals(-1, db1.e)

        val sb2 = url2.readBytes()
        val db2 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb2))

        assertEquals(a, db2.a)
        assertEquals(b, db2.b)
        assertEquals(c, db2.c)
        assertEquals(-1, db2.d)
        assertEquals(-1, db2.e)

        val sb3 = url3.readBytes()
        val db3 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb3))

        assertEquals(a, db3.a)
        assertEquals(b, db3.b)
        assertEquals(c, db3.c)
        assertEquals(d, db3.d)
        assertEquals(-1, db3.e)
    }

    @Test(timeout=300_000)
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

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val outer = DeserializationInput(sf).deserialize(SerializedBytes<Outer>(sc2))

        assertEquals(oa, outer.a)
        assertEquals(ia, outer.b.a)
        assertEquals(null, outer.b.b)

        // Repeat, but receiving a message with the newer version of Inner
        val newVersion = SerializationOutput(sf).serializeAndReturnSchema(Outer(oa, Inner(ia, "new value")))
        val model = AMQPRemoteTypeModel()
        val remoteTypeInfo = model.interpret(SerializationSchemas(newVersion.schema, newVersion.transformsSchema))
        println(remoteTypeInfo)

        val newOuter = DeserializationInput(sf).deserialize(SerializedBytes<Outer>(newVersion.obj.bytes))
        assertEquals(oa, newOuter.a)
        assertEquals(ia, newOuter.b.a)
        assertEquals("new value", newOuter.b.b)
    }

    @Test(timeout=300_000)
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

        val url1 = EvolvabilityTests::class.java.getResource(resource1)
        val url2 = EvolvabilityTests::class.java.getResource(resource2)
        val url3 = EvolvabilityTests::class.java.getResource(resource3)

        val sb1 = url1.readBytes()
        val db1 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb1))

        assertEquals(b, db1.b)
        assertEquals(c, db1.c)
        assertEquals(-1, db1.d) // must not be set by calling constructor 2 by mistake
        assertEquals(-1, db1.e)
        assertEquals(-1, db1.f)
        assertEquals(-1, db1.g)

        val sb2 = url2.readBytes()
        val db2 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb2))

        assertEquals(b, db2.b)
        assertEquals(c, db2.c)
        assertEquals(d, db2.d)
        assertEquals(e, db2.e)
        assertEquals(-1, db2.f)
        assertEquals(-1, db1.g)

        val sb3 = url3.readBytes()
        val db3 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb3))

        assertEquals(b, db3.b)
        assertEquals(c, db3.c)
        assertEquals(d, db3.d)
        assertEquals(e, db3.e)
        assertEquals(f, db3.f)
        assertEquals(-1, db3.g)
    }

    //
    // This test uses a NetworkParameters signed set of bytes generated by Corda Enterprise and
    // is here to ensure we can still read them. This test exists because of the break in
    // being able to deserialize an object serialized prior to some fixes to the fingerprinter.
    //
    // The file itself was generated from Corda Enterprise at commit
    //      6a6b6f256 Skip cache invalidation during init() - caches are still null.
    //
    // To regenerate the file un-ignore the test below this one (regenerate broken network parameters),
    // to regenerate at a specific version add that test to a checkout at the desired sha then take
    // the resulting file and add to the repo, changing the filename as appropriate
    //
    @Test(timeout=300_000)
@Ignore("Test fails after moving NetworkParameters and NotaryInfo into core from node-api")
    fun readBrokenNetworkParameters() {
        val sf = testDefaultFactory()
        sf.register(net.corda.serialization.internal.amqp.custom.InstantSerializer(sf))
        sf.register(net.corda.serialization.internal.amqp.custom.PublicKeySerializer)

        //
        // filename breakdown
        // networkParams - because this is a serialised set of network parameters
        // r3corda - generated by Corda Enterprise instead of Corda
        // 6a6b6f256 - Commit sha of the build that generated the file we're testing against
        //
        val resource = "networkParams.r3corda.6a6b6f256"

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<SignedData<NetworkParameters>>(sc2))
        val networkParams = DeserializationInput(sf).deserialize(deserializedC.raw)

        assertEquals(1000, networkParams.maxMessageSize)
        assertEquals(1000, networkParams.maxTransactionSize)
        assertEquals(3, networkParams.minimumPlatformVersion)
        assertEquals(1, networkParams.notaries.size)
        assertEquals(DUMMY_NOTARY_PARTY, networkParams.notaries.firstOrNull()?.identity)
    }

    //
    // This test uses a NetworkParameters signed set of bytes generated by a Corda 4.11 build and
    // is here to ensure we can still read them. This test exists because Corda 4.11 Adds in new
    // network parameters that should be backwards compatible with older corda versions
    //
    // The file itself was generated from a corda-os 4.11 build at commit
    //      58ecce1 Ent-9875: New Network Parameters
    //
    // To regenerate the file un-ignore the test below this one (regenerate broken network parameters),
    // to regenerate at a specific version add that test to a checkout at the desired sha then take
    // the resulting file and add to the repo, changing the filename as appropriate
    //
    @Test(timeout=300_000)
    fun `read corda 4-11 network parameters`() {
        val sf = testDefaultFactory()
        sf.register(net.corda.serialization.internal.amqp.custom.InstantSerializer(sf))
        sf.register(net.corda.serialization.internal.amqp.custom.PublicKeySerializer)
        sf.register(net.corda.serialization.internal.amqp.custom.DurationSerializer(sf))

        //
        // filename breakdown
        // networkParams - because this is a serialised set of network parameters
        // r3corda - generated by Corda Enterprise instead of Corda
        // 6a6b6f256 - Commit sha of the build that generated the file we're testing against
        //
        val resource = "networkParams.4.11.58ecce1"

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<SignedData<NetworkParameters>>(sc2))
        val networkParams = DeserializationInput(sf).deserialize(deserializedC.raw)

        assertEquals(1000, networkParams.maxMessageSize)
        assertEquals(1000, networkParams.maxTransactionSize)
        assertEquals(3, networkParams.minimumPlatformVersion)
        assertEquals(1, networkParams.notaries.size)
        assertEquals(DUMMY_NOTARY_PARTY, networkParams.notaries.firstOrNull()?.identity)
    }

    //
    // This test created a serialized and signed set of Network Parameters to test whether we
    // can still deserialize them
    //
    @Test(timeout=300_000)
@Ignore("This test simply regenerates the test file used for readBrokenNetworkParameters")
    fun `regenerate broken network parameters`() {
        // note: 6a6b6f256 is the sha that generates the file
        val resource = "networkParams.<corda version>.<commit sha>"
        val networkParameters = NetworkParameters(
                3, listOf(NotaryInfo(DUMMY_NOTARY_PARTY, false)), 1000, 1000, Instant.EPOCH, 1, emptyMap())

        val sf = testDefaultFactory()
        sf.register(net.corda.serialization.internal.amqp.custom.InstantSerializer(sf))
        sf.register(net.corda.serialization.internal.amqp.custom.PublicKeySerializer)

        val testOutput = TestSerializationOutput(true, sf)
        val serialized = testOutput.serialize(networkParameters)
        val keyPair = generateKeyPair()
        val sig = keyPair.private.sign(serialized.bytes, keyPair.public)
        val signed = SignedData(serialized, sig)
        val signedAndSerialized = testOutput.serialize(signed)

        File(URI("$localPath/$resource")).writeBytes(signedAndSerialized.bytes)
    }

    @Suppress("UNCHECKED_CAST")
    @Test(timeout=300_000)
	fun getterSetterEvolver1() {
        val resource = "EvolvabilityTests.getterSetterEvolver1"
        val sf = testDefaultFactory()

        //
        // Class as it was serialised
        //
        // data class C(var c: Int, var d: Int, var b: Int, var e: Int, var a: Int) {
        //     // This will force the serialization engine to use getter / setter
        //     // instantiation for the object rather than construction
        //     @ConstructorForDeserialization
        //     @Suppress("UNUSED")
        //     constructor() : this(0, 0, 0, 0, 0)
        // }
        //
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(3,4,2,5,1)).bytes)

        //
        // Class as it exists now, c has been removed
        //
        data class C(var d: Int, var b: Int, var e: Int, var a: Int) {
            // This will force the serialization engine to use getter / setter
            // instantiation for the object rather than construction
            @ConstructorForDeserialization
            @Suppress("UNUSED")
            constructor() : this(0, 0, 0, 0)
        }

        val url = EvolvabilityTests::class.java.getResource(resource)

        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(1, deserializedC.a)
        assertEquals(2, deserializedC.b)
        assertEquals(4, deserializedC.d)
        assertEquals(5, deserializedC.e)
    }

    // Class as it was serialized, with additional enum field.
    // enum class NewEnum { ONE, TWO, BUCKLE_MY_SHOE }
    // data class Evolved(val fnord: String, val newEnum: NewEnum)

    // Class before evolution
    data class Evolved(val fnord: String)

    @Test(timeout=300_000)
	fun evolutionWithCarpentry() {
        val resource = "EvolvabilityTests.evolutionWithCarpentry"
        val sf = testDefaultFactory()
        // Uncomment to recreate
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(Evolved("dronf", NewEnum.BUCKLE_MY_SHOE)).bytes)

        val url = EvolvabilityTests::class.java.getResource(resource)

        val sc2 = url.readBytes()
        val deserialized = DeserializationInput(sf).deserialize(SerializedBytes<Evolved>(sc2))

        assertEquals("dronf", deserialized.fnord)
    }

    // Container class
    data class ParameterizedContainer(val parameterized: Parameterized<Int, Int>?)
    // Class as it was serialized
    // data class Parameterized<A, B>(val a: A, val b: Set<B>)

    // Marker interface to force evolution
    interface ForceEvolution

    // Class after evolution
    data class Parameterized<A, B>(val a: A, val b: Set<B>) : ForceEvolution

    // See CORDA-2742
    @Test(timeout=300_000)
	fun evolutionWithPrimitives() {
        val resource = "EvolvabilityTests.evolutionWithPrimitives"
        val sf = testDefaultFactory()
        // Uncomment to recreate
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(ParameterizedContainer(Parameterized(10, setOf(20)))).bytes)

        val url = EvolvabilityTests::class.java.getResource(resource)

        val sc2 = url.readBytes()
        val deserialized = DeserializationInput(sf).deserialize(SerializedBytes<ParameterizedContainer>(sc2))

        assertEquals(10, deserialized.parameterized?.a)
    }

    @Test(timeout=300_000)
	fun addMandatoryFieldWithAltConstructorAndMakeExistingIntFieldNullable() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructorAndMakeExistingIntFieldNullable"

        // Original version of the class as it was serialised
        // data class CC(val a: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(1)).bytes)

        data class CC(val a: Int?, val b: Int) {
            @DeprecatedConstructorForDeserialization(1)
            @Suppress("unused")
            constructor(a: Int) : this(a,  42)
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(1, deserializedCC.a)
        assertEquals(42, deserializedCC.b)
    }

    @Test(timeout=300_000)
	fun addMandatoryFieldWithAltConstructorAndMakeExistingNullableIntFieldMandatory() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructorAndMakeExistingNullableIntFieldMandatory"

        // Original version of the class as it was serialised
        // data class CC(val a: Int?)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(null)).bytes)

        data class CC(val a: Int, val b: Int) {
            @DeprecatedConstructorForDeserialization(1)
            @Suppress("unused")
            constructor(a: Int?) : this(a ?: -1,42)
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(-1, deserializedCC.a)
        assertEquals(42, deserializedCC.b)
    }

    @Test(timeout=300_000)
	fun addMandatoryFieldAndRemoveExistingNullableIntField() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldAndRemoveExistingNullableIntField"

        // Original version of the class as it was serialised
        // data class CC(val data: String, val a: Int?)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC("written", null)).bytes)

        data class CC(val data: String, val b: String) {
            @DeprecatedConstructorForDeserialization(1)
            @Suppress("unused")
            constructor(data: String, a: Int?) : this(data, a?.toString() ?: "<not provided>")
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals("written", deserializedCC.data)
        assertEquals("<not provided>", deserializedCC.b)
    }

    @Test(timeout=300_000)
	fun removeExistingNullableIntFieldWithAltConstructor() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.removeExistingNullableIntFieldWithAltConstructor"

        // Original version of the class as it was serialised
//         data class CC(val data: String, val a: Int?)
//         File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC("written", null)).bytes)

        data class CC(val data: String) {
            @DeprecatedConstructorForDeserialization(1)
            @Suppress("unused")
            constructor(data: String, a: Int?) : this(data + (a?.toString() ?: "<not provided>"))
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals("written<not provided>", deserializedCC.data)
    }

    @Test(timeout = 300_000)
    fun notarisationRequestStabilityTest() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.notarisationRequest"

        // Stable form of NotarisationRequest from 4.9
        /*
        val txId = SecureHash.randomSHA256()
        val inputTxId1 = SecureHash.randomSHA256()
        val inputTxId2 = SecureHash.randomSHA256()
        val notarisationRequest = NotarisationRequest(listOf(StateRef(inputTxId1, 0), StateRef(inputTxId1, 1), StateRef(inputTxId2, 0), StateRef(inputTxId2, 1)).map { it.copy(txhash = SecureHash.create(it.txhash.toString())) }, txId)
        val currentForm = SerializationOutput(sf).serialize(notarisationRequest)
        File(URI("$localPath/$resource")).writeBytes(currentForm.bytes)
         */

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val previousForm = SerializedBytes<NotarisationRequest>(sc2)
        val deserialized = DeserializationInput(sf).deserialize(previousForm)

        assertEquals(deserialized.statesToConsume[0].txhash, deserialized.statesToConsume[1].txhash)
        assertEquals(deserialized.statesToConsume[2].txhash, deserialized.statesToConsume[3].txhash)
        assertNotSame(deserialized.statesToConsume[0].txhash, deserialized.statesToConsume[1].txhash)
        assertNotSame(deserialized.statesToConsume[2].txhash, deserialized.statesToConsume[3].txhash)

        val serialized = SerializationOutput(sf).serialize(deserialized)

        assertEquals(previousForm, serialized)

        val deserialized2 = DeserializationInput(sf).deserialize(serialized)
        assertEquals(deserialized.transactionId, deserialized2.transactionId)
        assertEquals(deserialized.statesToConsume, deserialized2.statesToConsume)

        assertEquals(deserialized2.statesToConsume[0].txhash, deserialized2.statesToConsume[1].txhash)
        assertEquals(deserialized2.statesToConsume[2].txhash, deserialized2.statesToConsume[3].txhash)
        assertNotSame(deserialized2.statesToConsume[0].txhash, deserialized2.statesToConsume[1].txhash)
        assertNotSame(deserialized2.statesToConsume[2].txhash, deserialized2.statesToConsume[3].txhash)
    }
}
