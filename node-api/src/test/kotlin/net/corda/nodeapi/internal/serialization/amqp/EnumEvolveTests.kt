/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.internal.toPath
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.nodeapi.internal.serialization.amqp.testutils.testName
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.NotSerializableException
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.corda.nodeapi.internal.serialization.amqp.testutils.serialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.deserialize

// NOTE: To recreate the test files used by these tests uncomment the original test classes and comment
//       the new ones out, then change each test to write out the serialized bytes rather than read
//       the file.
class EnumEvolveTests {
    @Suppress("UNUSED")
    var localPath: URI = projectRootDir.toUri().resolve(
            "node-api/src/test/resources/net/corda/nodeapi/internal/serialization/amqp")

    // Version of the class as it was serialised
    //
    // @CordaSerializationTransformEnumDefault("D", "C")
    // enum class DeserializeNewerSetToUnknown { A, B, C, D }
    //
    // Version of the class as it's used in the test
    enum class DeserializeNewerSetToUnknown { A, B, C }

    @Test
    fun deserialiseNewerSetToUnknown() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        data class C(val e: DeserializeNewerSetToUnknown)

        // Uncomment to re-generate test files
        // File(URI("$localPath/$resource")).writeBytes(
        //        SerializationOutput(sf).serialize(C(DeserializeNewerSetToUnknown.D)).bytes)

        val url = EvolvabilityTests::class.java.getResource(resource)

        val obj = DeserializationInput(sf).deserialize(SerializedBytes<C>(url.readBytes()))

        assertEquals(DeserializeNewerSetToUnknown.C, obj.e)
    }

    // Version of the class as it was serialised
    //
    // @CordaSerializationTransformEnumDefaults (
    //         CordaSerializationTransformEnumDefault("D", "C"),
    //         CordaSerializationTransformEnumDefault("E", "D"))
    // enum class DeserializeNewerSetToUnknown2 { A, B, C, D, E }
    //
    // Version of the class as it's used in the test
    enum class DeserializeNewerSetToUnknown2 { A, B, C }

    @Test
    fun deserialiseNewerSetToUnknown2() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        data class C(val e: DeserializeNewerSetToUnknown2)

        // Uncomment to re-generate test files
        // val so = SerializationOutput(sf)
        // File(URI("$localPath/$resource.C")).writeBytes(so.serialize(C(DeserializeNewerSetToUnknown2.C)).bytes)
        // File(URI("$localPath/$resource.D")).writeBytes(so.serialize(C(DeserializeNewerSetToUnknown2.D)).bytes)
        // File(URI("$localPath/$resource.E")).writeBytes(so.serialize(C(DeserializeNewerSetToUnknown2.E)).bytes)

        val url1 = EvolvabilityTests::class.java.getResource("$resource.C")
        val url2 = EvolvabilityTests::class.java.getResource("$resource.D")
        val url3 = EvolvabilityTests::class.java.getResource("$resource.E")

        // C will just work
        val obj1 = DeserializationInput(sf).deserialize(SerializedBytes<C>(url1.readBytes()))
        // D will transform directly to C
        val obj2 = DeserializationInput(sf).deserialize(SerializedBytes<C>(url2.readBytes()))
        // E will have to transform from E -> D -> C to work, so this should exercise that part
        // of the evolution code
        val obj3 = DeserializationInput(sf).deserialize(SerializedBytes<C>(url3.readBytes()))

        assertEquals(DeserializeNewerSetToUnknown2.C, obj1.e)
        assertEquals(DeserializeNewerSetToUnknown2.C, obj2.e)
        assertEquals(DeserializeNewerSetToUnknown2.C, obj3.e)
    }


    // Version of the class as it was serialised, evolve rule purposfuly not included to
    // test failure conditions
    //
    // enum class DeserializeNewerWithNoRule { A, B, C, D }
    //
    // Class as it exists for the test
    enum class DeserializeNewerWithNoRule { A, B, C }

    // Lets test to see if they forgot to provide an upgrade rule
    @Test
    fun deserialiseNewerWithNoRule() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        data class C(val e: DeserializeNewerWithNoRule)

        // Uncomment to re-generate test files
        // val so = SerializationOutput(sf)
        // File(URI("$localPath/$resource")).writeBytes(so.serialize(C(DeserializeNewerWithNoRule.D)).bytes)

        val url = EvolvabilityTests::class.java.getResource(resource)

        assertThatThrownBy {
            DeserializationInput(sf).deserialize(SerializedBytes<C>(url.readBytes()))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    // Version of class as it was serialized, at some point in the "future" several
    // values have been renamed
    //
    // First Change
    // A -> AA
    // @CordaSerializationTransformRenames (
    //         CordaSerializationTransformRename(from ="A", to = "AA")
    // )
    // enum class DeserializeWithRename { AA, B, C }
    //
    // Second Change
    // B -> BB
    // @CordaSerializationTransformRenames (
    //         CordaSerializationTransformRename(from = "B", to = "BB"),
    //         CordaSerializationTransformRename(from = "A", to = "AA")
    // )
    // enum class DeserializeWithRename { AA, BB, C }
    //
    // Third Change
    // BB -> XX
    // @CordaSerializationTransformRenames (
    //         CordaSerializationTransformRename(from = "B", to = "BB"),
    //         CordaSerializationTransformRename(from = "BB", to = "XX"),
    //         CordaSerializationTransformRename(from = "A", to = "AA")
    // )
    // enum class DeserializeWithRename { AA, XX, C }
    //
    // Finally, the version we're using to test with
    enum class DeserializeWithRename { A, B, C }

    @Test
    fun deserializeWithRename() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        data class C(val e: DeserializeWithRename)

        // Uncomment to re-generate test files, needs to be done in three stages
        // val so = SerializationOutput(sf)
        // First change
        // File(URI("$localPath/$resource.1.AA")).writeBytes(so.serialize(C(DeserializeWithRename.AA)).bytes)
        // File(URI("$localPath/$resource.1.B")).writeBytes(so.serialize(C(DeserializeWithRename.B)).bytes)
        // File(URI("$localPath/$resource.1.C")).writeBytes(so.serialize(C(DeserializeWithRename.C)).bytes)
        // Second change
        // File(URI("$localPath/$resource.2.AA")).writeBytes(so.serialize(C(DeserializeWithRename.AA)).bytes)
        // File(URI("$localPath/$resource.2.BB")).writeBytes(so.serialize(C(DeserializeWithRename.BB)).bytes)
        // File(URI("$localPath/$resource.2.C")).writeBytes(so.serialize(C(DeserializeWithRename.C)).bytes)
        // Third change
        // File(URI("$localPath/$resource.3.AA")).writeBytes(so.serialize(C(DeserializeWithRename.AA)).bytes)
        // File(URI("$localPath/$resource.3.XX")).writeBytes(so.serialize(C(DeserializeWithRename.XX)).bytes)
        // File(URI("$localPath/$resource.3.C")).writeBytes(so.serialize(C(DeserializeWithRename.C)).bytes)

        //
        // Test we can deserialize instances of the class after its first transformation
        //
        val path1_AA = EvolvabilityTests::class.java.getResource("$resource.1.AA")
        val path1_B = EvolvabilityTests::class.java.getResource("$resource.1.B")
        val path1_C = EvolvabilityTests::class.java.getResource("$resource.1.C")

        val obj1_AA = DeserializationInput(sf).deserialize(SerializedBytes<C>(path1_AA.readBytes()))
        val obj1_B = DeserializationInput(sf).deserialize(SerializedBytes<C>(path1_B.readBytes()))
        val obj1_C = DeserializationInput(sf).deserialize(SerializedBytes<C>(path1_C.readBytes()))

        assertEquals(DeserializeWithRename.A, obj1_AA.e)
        assertEquals(DeserializeWithRename.B, obj1_B.e)
        assertEquals(DeserializeWithRename.C, obj1_C.e)

        //
        // Test we can deserialize instances of the class after its second transformation
        //
        val path2_AA = EvolvabilityTests::class.java.getResource("$resource.2.AA")
        val path2_BB = EvolvabilityTests::class.java.getResource("$resource.2.BB")
        val path2_C = EvolvabilityTests::class.java.getResource("$resource.2.C")

        val obj2_AA = DeserializationInput(sf).deserialize(SerializedBytes<C>(path2_AA.readBytes()))
        val obj2_BB = DeserializationInput(sf).deserialize(SerializedBytes<C>(path2_BB.readBytes()))
        val obj2_C = DeserializationInput(sf).deserialize(SerializedBytes<C>(path2_C.readBytes()))

        assertEquals(DeserializeWithRename.A, obj2_AA.e)
        assertEquals(DeserializeWithRename.B, obj2_BB.e)
        assertEquals(DeserializeWithRename.C, obj2_C.e)

        //
        // Test we can deserialize instances of the class after its third transformation
        //
        val path3_AA = EvolvabilityTests::class.java.getResource("$resource.3.AA")
        val path3_XX = EvolvabilityTests::class.java.getResource("$resource.3.XX")
        val path3_C = EvolvabilityTests::class.java.getResource("$resource.3.C")

        val obj3_AA = DeserializationInput(sf).deserialize(SerializedBytes<C>(path3_AA.readBytes()))
        val obj3_XX = DeserializationInput(sf).deserialize(SerializedBytes<C>(path3_XX.readBytes()))
        val obj3_C = DeserializationInput(sf).deserialize(SerializedBytes<C>(path3_C.readBytes()))

        assertEquals(DeserializeWithRename.A, obj3_AA.e)
        assertEquals(DeserializeWithRename.B, obj3_XX.e)
        assertEquals(DeserializeWithRename.C, obj3_C.e)
    }

    // The origional version of the enum, what we'll be eventually deserialising into
    // enum class MultiOperations { A, B, C }
    //
    // First alteration, add D
    // @CordaSerializationTransformEnumDefault(old = "C", new = "D")
    // enum class MultiOperations { A, B, C, D }
    //
    // Second, add  E
    // @CordaSerializationTransformEnumDefaults(
    //         CordaSerializationTransformEnumDefault(old = "C", new = "D"),
    //         CordaSerializationTransformEnumDefault(old = "D", new = "E")
    // )
    // enum class MultiOperations { A, B, C, D, E }
    //
    // Third, Rename E to BOB
    // @CordaSerializationTransformEnumDefaults(
    //         CordaSerializationTransformEnumDefault(old = "C", new = "D"),
    //         CordaSerializationTransformEnumDefault(old = "D", new = "E")
    // )
    // @CordaSerializationTransformRename(to = "BOB", from = "E")
    // enum class MultiOperations { A, B, C, D, BOB }
    //
    // Fourth, Rename C to CAT, ADD F and G
    // @CordaSerializationTransformEnumDefaults(
    //         CordaSerializationTransformEnumDefault(old = "F", new = "G"),
    //         CordaSerializationTransformEnumDefault(old = "BOB", new = "F"),
    //         CordaSerializationTransformEnumDefault(old = "D", new = "E"),
    //         CordaSerializationTransformEnumDefault(old = "C", new = "D")
    // )
    // @CordaSerializationTransformRenames (
    //         CordaSerializationTransformRename(to = "CAT", from = "C"),
    //         CordaSerializationTransformRename(to = "BOB", from = "E")
    // )
    // enum class MultiOperations { A, B, CAT, D, BOB, F, G}
    //
    // Fifth, Rename F to FLUMP, Rename BOB to BBB, Rename A to APPLE
    // @CordaSerializationTransformEnumDefaults(
    //         CordaSerializationTransformEnumDefault(old = "F", new = "G"),
    //         CordaSerializationTransformEnumDefault(old = "BOB", new = "F"),
    //         CordaSerializationTransformEnumDefault(old = "D", new = "E"),
    //         CordaSerializationTransformEnumDefault(old = "C", new = "D")
    // )
    // @CordaSerializationTransformRenames (
    //         CordaSerializationTransformRename(to = "APPLE", from = "A"),
    //         CordaSerializationTransformRename(to = "BBB", from = "BOB"),
    //         CordaSerializationTransformRename(to = "FLUMP", from = "F"),
    //         CordaSerializationTransformRename(to = "CAT", from = "C"),
    //         CordaSerializationTransformRename(to = "BOB", from = "E")
    // )
    // enum class MultiOperations { APPLE, B, CAT, D, BBB, FLUMP, G}
    //
    // Finally, the original version of teh class that we're going to be testing with
    enum class MultiOperations { A, B, C }

    @Test
    fun multiOperations() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        data class C(val e: MultiOperations)

        // Uncomment to re-generate test files, needs to be done in three stages
        // val so = SerializationOutput(sf)
        // First change
        // File(URI("$localPath/$resource.1.A")).writeBytes(so.serialize(C(MultiOperations.A)).bytes)
        // File(URI("$localPath/$resource.1.B")).writeBytes(so.serialize(C(MultiOperations.B)).bytes)
        // File(URI("$localPath/$resource.1.C")).writeBytes(so.serialize(C(MultiOperations.C)).bytes)
        // File(URI("$localPath/$resource.1.D")).writeBytes(so.serialize(C(MultiOperations.D)).bytes)
        // Second change
        // File(URI("$localPath/$resource.2.A")).writeBytes(so.serialize(C(MultiOperations.A)).bytes)
        // File(URI("$localPath/$resource.2.B")).writeBytes(so.serialize(C(MultiOperations.B)).bytes)
        // File(URI("$localPath/$resource.2.C")).writeBytes(so.serialize(C(MultiOperations.C)).bytes)
        // File(URI("$localPath/$resource.2.D")).writeBytes(so.serialize(C(MultiOperations.D)).bytes)
        // File(URI("$localPath/$resource.2.E")).writeBytes(so.serialize(C(MultiOperations.E)).bytes)
        // Third change
        // File(URI("$localPath/$resource.3.A")).writeBytes(so.serialize(C(MultiOperations.A)).bytes)
        // File(URI("$localPath/$resource.3.B")).writeBytes(so.serialize(C(MultiOperations.B)).bytes)
        // File(URI("$localPath/$resource.3.C")).writeBytes(so.serialize(C(MultiOperations.C)).bytes)
        // File(URI("$localPath/$resource.3.D")).writeBytes(so.serialize(C(MultiOperations.D)).bytes)
        // File(URI("$localPath/$resource.3.BOB")).writeBytes(so.serialize(C(MultiOperations.BOB)).bytes)
        // Fourth change
        // File(URI("$localPath/$resource.4.A")).writeBytes(so.serialize(C(MultiOperations.A)).bytes)
        // File(URI("$localPath/$resource.4.B")).writeBytes(so.serialize(C(MultiOperations.B)).bytes)
        // File(URI("$localPath/$resource.4.CAT")).writeBytes(so.serialize(C(MultiOperations.CAT)).bytes)
        // File(URI("$localPath/$resource.4.D")).writeBytes(so.serialize(C(MultiOperations.D)).bytes)
        // File(URI("$localPath/$resource.4.BOB")).writeBytes(so.serialize(C(MultiOperations.BOB)).bytes)
        // File(URI("$localPath/$resource.4.F")).writeBytes(so.serialize(C(MultiOperations.F)).bytes)
        // File(URI("$localPath/$resource.4.G")).writeBytes(so.serialize(C(MultiOperations.G)).bytes)
        // Fifth change - { APPLE, B, CAT, D, BBB, FLUMP, G}
        // File(URI("$localPath/$resource.5.APPLE")).writeBytes(so.serialize(C(MultiOperations.APPLE)).bytes)
        // File(URI("$localPath/$resource.5.B")).writeBytes(so.serialize(C(MultiOperations.B)).bytes)
        // File(URI("$localPath/$resource.5.CAT")).writeBytes(so.serialize(C(MultiOperations.CAT)).bytes)
        // File(URI("$localPath/$resource.5.D")).writeBytes(so.serialize(C(MultiOperations.D)).bytes)
        // File(URI("$localPath/$resource.5.BBB")).writeBytes(so.serialize(C(MultiOperations.BBB)).bytes)
        // File(URI("$localPath/$resource.5.FLUMP")).writeBytes(so.serialize(C(MultiOperations.FLUMP)).bytes)
        // File(URI("$localPath/$resource.5.G")).writeBytes(so.serialize(C(MultiOperations.G)).bytes)

        val stage1Resources = listOf(
                Pair("$resource.1.A", MultiOperations.A),
                Pair("$resource.1.B", MultiOperations.B),
                Pair("$resource.1.C", MultiOperations.C),
                Pair("$resource.1.D", MultiOperations.C))

        val stage2Resources = listOf(
                Pair("$resource.2.A", MultiOperations.A),
                Pair("$resource.2.B", MultiOperations.B),
                Pair("$resource.2.C", MultiOperations.C),
                Pair("$resource.2.D", MultiOperations.C),
                Pair("$resource.2.E", MultiOperations.C))

        val stage3Resources = listOf(
                Pair("$resource.3.A", MultiOperations.A),
                Pair("$resource.3.B", MultiOperations.B),
                Pair("$resource.3.C", MultiOperations.C),
                Pair("$resource.3.D", MultiOperations.C),
                Pair("$resource.3.BOB", MultiOperations.C))

        val stage4Resources = listOf(
                Pair("$resource.4.A", MultiOperations.A),
                Pair("$resource.4.B", MultiOperations.B),
                Pair("$resource.4.CAT", MultiOperations.C),
                Pair("$resource.4.D", MultiOperations.C),
                Pair("$resource.4.BOB", MultiOperations.C),
                Pair("$resource.4.F", MultiOperations.C),
                Pair("$resource.4.G", MultiOperations.C))

        val stage5Resources = listOf(
                Pair("$resource.5.APPLE", MultiOperations.A),
                Pair("$resource.5.B", MultiOperations.B),
                Pair("$resource.5.CAT", MultiOperations.C),
                Pair("$resource.5.D", MultiOperations.C),
                Pair("$resource.5.BBB", MultiOperations.C),
                Pair("$resource.5.FLUMP", MultiOperations.C),
                Pair("$resource.5.G", MultiOperations.C))

        fun load(l: List<Pair<String, MultiOperations>>) = l.map {
            assertNotNull(EvolvabilityTests::class.java.getResource(it.first))
            assertThat(EvolvabilityTests::class.java.getResource(it.first).toPath()).exists()

            Pair(DeserializationInput(sf).deserialize(SerializedBytes<C>(
                    EvolvabilityTests::class.java.getResource(it.first).readBytes())), it.second)
        }

        load(stage1Resources).forEach { assertEquals(it.second, it.first.e) }
        load(stage2Resources).forEach { assertEquals(it.second, it.first.e) }
        load(stage3Resources).forEach { assertEquals(it.second, it.first.e) }
        load(stage4Resources).forEach { assertEquals(it.second, it.first.e) }
        load(stage5Resources).forEach { assertEquals(it.second, it.first.e) }
    }

    @CordaSerializationTransformEnumDefault(old = "A", new = "F")
    enum class BadNewValue { A, B, C, D }

    @Test
    fun badNewValue() {
        val sf = testDefaultFactory()

        data class C(val e: BadNewValue)

        assertThatThrownBy {
            SerializationOutput(sf).serialize(C(BadNewValue.A))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault(new = "D", old = "E"),
            CordaSerializationTransformEnumDefault(new = "E", old = "A")
    )
    enum class OutOfOrder { A, B, C, D, E }

    @Test
    fun outOfOrder() {
        val sf = testDefaultFactory()

        data class C(val e: OutOfOrder)

        assertThatThrownBy {
            SerializationOutput(sf).serialize(C(OutOfOrder.A))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    // class as it existed as it was serialized
    //
    // enum class ChangedOrdinality { A, B, C }
    //
    // class as it exists for the tests
    @CordaSerializationTransformEnumDefault("D", "A")
    enum class ChangedOrdinality { A, B, D, C }

    @Test
    fun changedOrdinality() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        data class C(val e: ChangedOrdinality)

        // Uncomment to re-generate test files, needs to be done in three stages
        // File(URI("$localPath/$resource")).writeBytes(
        //         SerializationOutput(sf).serialize(C(ChangedOrdinality.A)).bytes)

        assertThatThrownBy {
            DeserializationInput(sf).deserialize(SerializedBytes<C>(
                    EvolvabilityTests::class.java.getResource(resource).readBytes()))
        }.isInstanceOf(NotSerializableException::class.java)
    }
}
