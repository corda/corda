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

import net.corda.core.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.nodeapi.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.nodeapi.internal.serialization.amqp.testutils.serialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.NotSerializableException
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnumEvolvabilityTests {
    @Suppress("UNUSED")
    var localPath: URI = projectRootDir.toUri().resolve(
            "node-api/src/test/resources/net/corda/nodeapi/internal/serialization/amqp")

    companion object {
        const val VERBOSE = false
    }

    enum class NotAnnotated {
        A, B, C, D
    }

    @CordaSerializationTransformRenames()
    enum class MissingRenames {
        A, B, C, D
    }

    @CordaSerializationTransformEnumDefault("D", "A")
    enum class AnnotatedEnumOnce {
        A, B, C, D
    }

    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault("E", "D"),
            CordaSerializationTransformEnumDefault("D", "A"))
    enum class AnnotatedEnumTwice {
        A, B, C, D, E
    }

    @CordaSerializationTransformRename("E", "D")
    enum class RenameEnumOnce {
        A, B, C, E
    }

    @Test
    fun noAnnotation() {
        data class C(val n: NotAnnotated)

        val sf = testDefaultFactory()
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(NotAnnotated.A))

        assertEquals(2, bAndS.schema.types.size)
        assertEquals(0, bAndS.transformsSchema.types.size)
    }

    @CordaSerializationTransformEnumDefaults()
    enum class MissingDefaults {
        A, B, C, D
    }

    @Test
    fun missingDefaults() {
        data class C(val m: MissingDefaults)

        val sf = testDefaultFactory()
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(MissingDefaults.A))

        assertEquals(2, bAndS.schema.types.size)
        assertEquals(0, bAndS.transformsSchema.types.size)
    }

    @Test
    fun missingRenames() {
        data class C(val m: MissingRenames)

        val sf = testDefaultFactory()
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(MissingRenames.A))

        assertEquals(2, bAndS.schema.types.size)
        assertEquals(0, bAndS.transformsSchema.types.size)

    }

    @Test
    fun defaultAnnotationIsAddedToEnvelope() {
        data class C(val annotatedEnum: AnnotatedEnumOnce)

        val sf = testDefaultFactory()
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(AnnotatedEnumOnce.D))

        // only the enum is decorated so schema sizes should be different (2 objects, only one evolved)
        assertEquals(2, bAndS.schema.types.size)
        assertEquals(1, bAndS.transformsSchema.types.size)
        assertEquals(AnnotatedEnumOnce::class.java.name, bAndS.transformsSchema.types.keys.first())

        val schema = bAndS.transformsSchema.types.values.first()

        assertEquals(1, schema.size)
        assertTrue(schema.keys.contains(TransformTypes.EnumDefault))
        assertEquals(1, schema[TransformTypes.EnumDefault]!!.size)
        assertTrue(schema[TransformTypes.EnumDefault]!![0] is EnumDefaultSchemaTransform)
        assertEquals("D", (schema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).new)
        assertEquals("A", (schema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).old)
    }

    @Test
    fun doubleDefaultAnnotationIsAddedToEnvelope() {
        data class C(val annotatedEnum: AnnotatedEnumTwice)

        val sf = testDefaultFactory()
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(AnnotatedEnumTwice.E))

        assertEquals(2, bAndS.schema.types.size)
        assertEquals(1, bAndS.transformsSchema.types.size)
        assertEquals(AnnotatedEnumTwice::class.java.name, bAndS.transformsSchema.types.keys.first())

        val schema = bAndS.transformsSchema.types.values.first()

        assertEquals(1, schema.size)
        assertTrue(schema.keys.contains(TransformTypes.EnumDefault))
        assertEquals(2, schema[TransformTypes.EnumDefault]!!.size)
        assertTrue(schema[TransformTypes.EnumDefault]!![0] is EnumDefaultSchemaTransform)
        assertEquals("E", (schema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).new)
        assertEquals("D", (schema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).old)
        assertTrue(schema[TransformTypes.EnumDefault]!![1] is EnumDefaultSchemaTransform)
        assertEquals("D", (schema[TransformTypes.EnumDefault]!![1] as EnumDefaultSchemaTransform).new)
        assertEquals("A", (schema[TransformTypes.EnumDefault]!![1] as EnumDefaultSchemaTransform).old)
    }

    @Test
    fun defaultAnnotationIsAddedToEnvelopeAndDeserialised() {
        data class C(val annotatedEnum: AnnotatedEnumOnce)

        val sf = testDefaultFactory()
        val sb = TestSerializationOutput(VERBOSE, sf).serialize(C(AnnotatedEnumOnce.D))
        val db = DeserializationInput(sf).deserializeAndReturnEnvelope(sb)

        // as with the serialisation stage, de-serialising the object we should see two
        // types described in the header with one of those having transforms
        assertEquals(2, db.envelope.schema.types.size)
        assertEquals(1, db.envelope.transformsSchema.types.size)

        val eName = AnnotatedEnumOnce::class.java.name
        val types = db.envelope.schema.types
        val transforms = db.envelope.transformsSchema.types

        assertEquals(1, types.filter { it.name == eName }.size)
        assertTrue(eName in transforms)

        val schema = transforms[eName]

        assertTrue(schema!!.keys.contains(TransformTypes.EnumDefault))
        assertEquals(1, schema[TransformTypes.EnumDefault]!!.size)
        assertTrue(schema[TransformTypes.EnumDefault]!![0] is EnumDefaultSchemaTransform)
        assertEquals("D", (schema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).new)
        assertEquals("A", (schema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).old)
    }

    @Test
    fun doubleDefaultAnnotationIsAddedToEnvelopeAndDeserialised() {
        data class C(val annotatedEnum: AnnotatedEnumTwice)

        val sf = testDefaultFactory()
        val sb = TestSerializationOutput(VERBOSE, sf).serialize(C(AnnotatedEnumTwice.E))
        val db = DeserializationInput(sf).deserializeAndReturnEnvelope(sb)

        // as with the serialisation stage, de-serialising the object we should see two
        // types described in the header with one of those having transforms
        assertEquals(2, db.envelope.schema.types.size)
        assertEquals(1, db.envelope.transformsSchema.types.size)

        val transforms = db.envelope.transformsSchema.types

        assertTrue(transforms.contains(AnnotatedEnumTwice::class.java.name))
        assertTrue(transforms[AnnotatedEnumTwice::class.java.name]!!.contains(TransformTypes.EnumDefault))
        assertEquals(2, transforms[AnnotatedEnumTwice::class.java.name]!![TransformTypes.EnumDefault]!!.size)

        val enumDefaults = transforms[AnnotatedEnumTwice::class.java.name]!![TransformTypes.EnumDefault]!!

        assertEquals("E", (enumDefaults[0] as EnumDefaultSchemaTransform).new)
        assertEquals("D", (enumDefaults[0] as EnumDefaultSchemaTransform).old)
        assertEquals("D", (enumDefaults[1] as EnumDefaultSchemaTransform).new)
        assertEquals("A", (enumDefaults[1] as EnumDefaultSchemaTransform).old)
    }

    @Test
    fun renameAnnotationIsAdded() {
        data class C(val annotatedEnum: RenameEnumOnce)

        val sf = testDefaultFactory()

        // Serialise the object
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(RenameEnumOnce.E))

        assertEquals(2, bAndS.schema.types.size)
        assertEquals(1, bAndS.transformsSchema.types.size)
        assertEquals(RenameEnumOnce::class.java.name, bAndS.transformsSchema.types.keys.first())

        val serialisedSchema = bAndS.transformsSchema.types[RenameEnumOnce::class.java.name]!!

        assertEquals(1, serialisedSchema.size)
        assertTrue(serialisedSchema.containsKey(TransformTypes.Rename))
        assertEquals(1, serialisedSchema[TransformTypes.Rename]!!.size)
        assertEquals("D", (serialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).from)
        assertEquals("E", (serialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).to)

        // Now de-serialise the blob
        val cAndS = DeserializationInput(sf).deserializeAndReturnEnvelope(bAndS.obj)

        assertEquals(2, cAndS.envelope.schema.types.size)
        assertEquals(1, cAndS.envelope.transformsSchema.types.size)
        assertEquals(RenameEnumOnce::class.java.name, cAndS.envelope.transformsSchema.types.keys.first())

        val deserialisedSchema = cAndS.envelope.transformsSchema.types[RenameEnumOnce::class.java.name]!!

        assertEquals(1, deserialisedSchema.size)
        assertTrue(deserialisedSchema.containsKey(TransformTypes.Rename))
        assertEquals(1, deserialisedSchema[TransformTypes.Rename]!!.size)
        assertEquals("D", (deserialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).from)
        assertEquals("E", (deserialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).to)
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename("E", "C"),
            CordaSerializationTransformRename("F", "D"))
    enum class RenameEnumTwice {
        A, B, E, F
    }

    @Test
    fun doubleRenameAnnotationIsAdded() {
        data class C(val annotatedEnum: RenameEnumTwice)

        val sf = testDefaultFactory()

        // Serialise the object
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(RenameEnumTwice.F))

        assertEquals(2, bAndS.schema.types.size)
        assertEquals(1, bAndS.transformsSchema.types.size)
        assertEquals(RenameEnumTwice::class.java.name, bAndS.transformsSchema.types.keys.first())

        val serialisedSchema = bAndS.transformsSchema.types[RenameEnumTwice::class.java.name]!!

        assertEquals(1, serialisedSchema.size)
        assertTrue(serialisedSchema.containsKey(TransformTypes.Rename))
        assertEquals(2, serialisedSchema[TransformTypes.Rename]!!.size)
        assertEquals("C", (serialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).from)
        assertEquals("E", (serialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).to)
        assertEquals("D", (serialisedSchema[TransformTypes.Rename]!![1] as RenameSchemaTransform).from)
        assertEquals("F", (serialisedSchema[TransformTypes.Rename]!![1] as RenameSchemaTransform).to)

        // Now de-serialise the blob
        val cAndS = DeserializationInput(sf).deserializeAndReturnEnvelope(bAndS.obj)

        assertEquals(2, cAndS.envelope.schema.types.size)
        assertEquals(1, cAndS.envelope.transformsSchema.types.size)
        assertEquals(RenameEnumTwice::class.java.name, cAndS.envelope.transformsSchema.types.keys.first())

        val deserialisedSchema = cAndS.envelope.transformsSchema.types[RenameEnumTwice::class.java.name]!!

        assertEquals(1, deserialisedSchema.size)
        assertTrue(deserialisedSchema.containsKey(TransformTypes.Rename))
        assertEquals(2, deserialisedSchema[TransformTypes.Rename]!!.size)
        assertEquals("C", (deserialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).from)
        assertEquals("E", (deserialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).to)
        assertEquals("D", (deserialisedSchema[TransformTypes.Rename]!![1] as RenameSchemaTransform).from)
        assertEquals("F", (deserialisedSchema[TransformTypes.Rename]!![1] as RenameSchemaTransform).to)
    }

    @CordaSerializationTransformRename(from = "A", to = "X")
    @CordaSerializationTransformEnumDefault(old = "X", new = "E")
    enum class RenameAndExtendEnum {
        X, B, C, D, E
    }

    @Test
    fun bothAnnotationTypes() {
        data class C(val annotatedEnum: RenameAndExtendEnum)

        val sf = testDefaultFactory()

        // Serialise the object
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(RenameAndExtendEnum.X))

        assertEquals(2, bAndS.schema.types.size)
        assertEquals(1, bAndS.transformsSchema.types.size)
        assertEquals(RenameAndExtendEnum::class.java.name, bAndS.transformsSchema.types.keys.first())

        val serialisedSchema = bAndS.transformsSchema.types[RenameAndExtendEnum::class.java.name]!!

        // This time there should be two distinct transform types (all previous tests have had only
        // a single type
        assertEquals(2, serialisedSchema.size)
        assertTrue(serialisedSchema.containsKey(TransformTypes.Rename))
        assertTrue(serialisedSchema.containsKey(TransformTypes.EnumDefault))

        assertEquals(1, serialisedSchema[TransformTypes.Rename]!!.size)
        assertEquals("A", (serialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).from)
        assertEquals("X", (serialisedSchema[TransformTypes.Rename]!![0] as RenameSchemaTransform).to)

        assertEquals(1, serialisedSchema[TransformTypes.EnumDefault]!!.size)
        assertEquals("E", (serialisedSchema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).new)
        assertEquals("X", (serialisedSchema[TransformTypes.EnumDefault]!![0] as EnumDefaultSchemaTransform).old)
    }

    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault("D", "A"),
            CordaSerializationTransformEnumDefault("D", "A"))
    enum class RepeatedAnnotation {
        A, B, C, D, E
    }

    @Test
    fun repeatedAnnotation() {
        data class C(val a: RepeatedAnnotation)

        val sf = testDefaultFactory()

        Assertions.assertThatThrownBy {
            TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C(RepeatedAnnotation.A))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @CordaSerializationTransformEnumDefault("D", "A")
    enum class E1 {
        A, B, C, D
    }

    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault("D", "A"),
            CordaSerializationTransformEnumDefault("E", "A"))
    enum class E2 {
        A, B, C, D, E
    }

    @CordaSerializationTransformEnumDefaults(CordaSerializationTransformEnumDefault("D", "A"))
    enum class E3 {
        A, B, C, D
    }

    @Test
    fun multiEnums() {
        data class A(val a: E1, val b: E2)
        data class B(val a: E3, val b: A, val c: E1)
        data class C(val a: B, val b: E2, val c: E3)

        val c = C(B(E3.A, A(E1.A, E2.B), E1.C), E2.B, E3.A)

        val sf = testDefaultFactory()

        // Serialise the object
        val bAndS = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(c)

        println(bAndS.transformsSchema)

        // we have six types and three of those, the enums, should have transforms
        assertEquals(6, bAndS.schema.types.size)
        assertEquals(3, bAndS.transformsSchema.types.size)

        assertTrue(E1::class.java.name in bAndS.transformsSchema.types)
        assertTrue(E2::class.java.name in bAndS.transformsSchema.types)
        assertTrue(E3::class.java.name in bAndS.transformsSchema.types)

        val e1S = bAndS.transformsSchema.types[E1::class.java.name]!!
        val e2S = bAndS.transformsSchema.types[E2::class.java.name]!!
        val e3S = bAndS.transformsSchema.types[E3::class.java.name]!!

        assertEquals(1, e1S.size)
        assertEquals(1, e2S.size)
        assertEquals(1, e3S.size)

        assertTrue(TransformTypes.EnumDefault in e1S)
        assertTrue(TransformTypes.EnumDefault in e2S)
        assertTrue(TransformTypes.EnumDefault in e3S)

        assertEquals(1, e1S[TransformTypes.EnumDefault]!!.size)
        assertEquals(2, e2S[TransformTypes.EnumDefault]!!.size)
        assertEquals(1, e3S[TransformTypes.EnumDefault]!!.size)
    }

    @Test
    fun testCache() {
        data class C2(val annotatedEnum: AnnotatedEnumOnce)
        data class C1(val annotatedEnum: AnnotatedEnumOnce)

        val sf = testDefaultFactory()
        val f = sf.javaClass.getDeclaredField("transformsCache")
        f.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val transformsCache = f.get(sf) as ConcurrentHashMap<String, EnumMap<TransformTypes, MutableList<Transform>>>

        assertEquals(0, transformsCache.size)

        val sb1 = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C1(AnnotatedEnumOnce.D))

        assertEquals(2, transformsCache.size)
        assertTrue(transformsCache.containsKey(C1::class.java.name))
        assertTrue(transformsCache.containsKey(AnnotatedEnumOnce::class.java.name))

        val sb2 = TestSerializationOutput(VERBOSE, sf).serializeAndReturnSchema(C2(AnnotatedEnumOnce.D))

        assertEquals(3, transformsCache.size)
        assertTrue(transformsCache.containsKey(C1::class.java.name))
        assertTrue(transformsCache.containsKey(C2::class.java.name))
        assertTrue(transformsCache.containsKey(AnnotatedEnumOnce::class.java.name))

        assertEquals(sb1.transformsSchema.types[AnnotatedEnumOnce::class.java.name],
                sb2.transformsSchema.types[AnnotatedEnumOnce::class.java.name])
    }


    //@UnknownTransformAnnotation (10, 20, 30)
    enum class WithUnknownTest {
        A, B, C, D
    }

    data class WrapsUnknown(val unknown: WithUnknownTest)

    // To regenerate the types for this test uncomment the UnknownTransformAnnotation from
    // TransformTypes.kt and SupportedTransforms.kt
    // ALSO: remember to re-annotate the enum WithUnkownTest above
    @Test
    fun testUnknownTransform() {
        val resource = "EnumEvolvabilityTests.testUnknownTransform"
        val sf = testDefaultFactory()

        //File(URI("$localPath/$resource")).writeBytes(
        //        SerializationOutput(sf).serialize(WrapsUnknown(WithUnknownTest.D)).bytes)

        val sb1 = EvolvabilityTests::class.java.getResource(resource).readBytes()

        val envelope = DeserializationInput(sf).deserializeAndReturnEnvelope(SerializedBytes<WrapsUnknown>(sb1)).envelope

        assertTrue(envelope.transformsSchema.types.containsKey(WithUnknownTest::class.java.name))
        assertTrue(envelope.transformsSchema.types[WithUnknownTest::class.java.name]!!.containsKey(TransformTypes.Unknown))
    }

    //
    // In this example we will have attempted to rename D back to C
    //
    // The life cycle of the class would've looked like this
    //
    // 1. enum class RejectCyclicRename { A, B, C }
    // 2. enum class RejectCyclicRename { A, B, D }
    // 3. enum class RejectCyclicRename { A, B, C }
    //
    // And we're not at 3. However, we ban this rename
    //
    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename("D", "C"),
            CordaSerializationTransformRename("C", "D")
    )
    enum class RejectCyclicRename { A, B, C }

    @Test
    fun rejectCyclicRename() {
        data class C(val e: RejectCyclicRename)

        val sf = testDefaultFactory()
        Assertions.assertThatThrownBy {
            SerializationOutput(sf).serialize(C(RejectCyclicRename.A))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    //
    // In this test, like the above, we're looking to ensure repeated renames are rejected as
    // unserailzble. However, in this case, it isn't a struct cycle, rather one element
    // is renamed to match what a different element used to be called
    //
    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename(from = "B", to = "C"),
            CordaSerializationTransformRename(from = "C", to = "D")
    )
    enum class RejectCyclicRenameAlt { A, C, D }

    @Test
    fun rejectCyclicRenameAlt() {
        data class C(val e: RejectCyclicRenameAlt)

        val sf = testDefaultFactory()
        Assertions.assertThatThrownBy {
            SerializationOutput(sf).serialize(C(RejectCyclicRenameAlt.A))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename("G", "C"),
            CordaSerializationTransformRename("F", "G"),
            CordaSerializationTransformRename("E", "F"),
            CordaSerializationTransformRename("D", "E"),
            CordaSerializationTransformRename("C", "D")
    )
    enum class RejectCyclicRenameRedux { A, B, C }

    @Test
    fun rejectCyclicRenameRedux() {
        data class C(val e: RejectCyclicRenameRedux)

        val sf = testDefaultFactory()
        Assertions.assertThatThrownBy {
            SerializationOutput(sf).serialize(C(RejectCyclicRenameRedux.A))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @CordaSerializationTransformEnumDefault(new = "D", old = "X")
    enum class RejectBadDefault { A, B, C, D }

    @Test
    fun rejectBadDefault() {
        data class C(val e: RejectBadDefault)

        val sf = testDefaultFactory()
        Assertions.assertThatThrownBy {
            SerializationOutput(sf).serialize(C(RejectBadDefault.D))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @CordaSerializationTransformEnumDefault(new = "D", old = "D")
    enum class RejectBadDefaultToSelf { A, B, C, D }

    @Test
    fun rejectBadDefaultToSelf() {
        data class C(val e: RejectBadDefaultToSelf)

        val sf = testDefaultFactory()
        Assertions.assertThatThrownBy {
            SerializationOutput(sf).serialize(C(RejectBadDefaultToSelf.D))
        }.isInstanceOf(NotSerializableException::class.java)
    }

}
