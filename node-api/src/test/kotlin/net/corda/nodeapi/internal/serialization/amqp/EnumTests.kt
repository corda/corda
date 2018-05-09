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

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.nodeapi.internal.serialization.amqp.testutils.testName
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.NotSerializableException
import java.time.DayOfWeek
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.corda.nodeapi.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.nodeapi.internal.serialization.amqp.testutils.serialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.nodeapi.internal.serialization.amqp.testutils.deserialize

class EnumTests {
    enum class Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    @CordaSerializable
    enum class AnnotatedBras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    // The state of the OldBras enum when the tests in changedEnum1 were serialised
    //      - use if the test file needs regenerating
    //enum class OldBras {
    //    TSHIRT, UNDERWIRE, PUSHUP, BRALETTE
    //}

    // the new state, SPACER has been added to change the ordinality
    enum class OldBras {
        SPACER, TSHIRT, UNDERWIRE, PUSHUP, BRALETTE
    }

    // The state of the OldBras2 enum when the tests in changedEnum2 were serialised
    //      - use if the test file needs regenerating
    //enum class OldBras2 {
    //    TSHIRT, UNDERWIRE, PUSHUP, BRALETTE
    //}

    // the new state, note in the test we serialised with value UNDERWIRE so the spacer
    // occurring after this won't have changed the ordinality of our serialised value
    // and thus should still be deserializable
    enum class OldBras2 {
        TSHIRT, UNDERWIRE, PUSHUP, SPACER, BRALETTE, SPACER2
    }


    enum class BrasWithInit(val someList: List<Int>) {
        TSHIRT(emptyList()),
        UNDERWIRE(listOf(1, 2, 3)),
        PUSHUP(listOf(100, 200)),
        BRALETTE(emptyList())
    }

    private val brasTestName = "${this.javaClass.name}\$Bras"

    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    @Suppress("NOTHING_TO_INLINE")
    inline private fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"

    private val sf1 = testDefaultFactoryNoEvolution()

    @Test
    fun serialiseSimpleTest() {
        data class C(val c: Bras)

        val schema = TestSerializationOutput(VERBOSE, sf1).serializeAndReturnSchema(C(Bras.UNDERWIRE)).schema

        assertEquals(2, schema.types.size)
        val schema_c = schema.types.find { it.name == classTestName("C") } as CompositeType
        val schema_bras = schema.types.find { it.name == brasTestName } as RestrictedType

        assertNotNull(schema_c)
        assertNotNull(schema_bras)

        assertEquals(1, schema_c.fields.size)
        assertEquals("c", schema_c.fields.first().name)
        assertEquals(brasTestName, schema_c.fields.first().type)

        assertEquals(8, schema_bras.choices.size)
        Bras.values().forEach {
            val bra = it
            assertNotNull(schema_bras.choices.find { it.name == bra.name })
        }
    }

    @Test
    fun deserialiseSimpleTest() {
        data class C(val c: Bras)

        val objAndEnvelope = DeserializationInput(sf1).deserializeAndReturnEnvelope(
                TestSerializationOutput(VERBOSE, sf1).serialize(C(Bras.UNDERWIRE)))

        val obj = objAndEnvelope.obj
        val schema = objAndEnvelope.envelope.schema

        assertEquals(2, schema.types.size)
        val schema_c = schema.types.find { it.name == classTestName("C") } as CompositeType
        val schema_bras = schema.types.find { it.name == brasTestName } as RestrictedType

        assertEquals(1, schema_c.fields.size)
        assertEquals("c", schema_c.fields.first().name)
        assertEquals(brasTestName, schema_c.fields.first().type)

        assertEquals(8, schema_bras.choices.size)
        Bras.values().forEach {
            val bra = it
            assertNotNull(schema_bras.choices.find { it.name == bra.name })
        }

        // Test the actual deserialised object
        assertEquals(obj.c, Bras.UNDERWIRE)
    }

    @Test
    fun multiEnum() {
        data class Support(val top: Bras, val day: DayOfWeek)
        data class WeeklySupport(val tops: List<Support>)

        val week = WeeklySupport(listOf(
                Support(Bras.PUSHUP, DayOfWeek.MONDAY),
                Support(Bras.UNDERWIRE, DayOfWeek.WEDNESDAY),
                Support(Bras.PADDED, DayOfWeek.SUNDAY)))

        val obj = DeserializationInput(sf1).deserialize(TestSerializationOutput(VERBOSE, sf1).serialize(week))

        assertEquals(week.tops[0].top, obj.tops[0].top)
        assertEquals(week.tops[0].day, obj.tops[0].day)
        assertEquals(week.tops[1].top, obj.tops[1].top)
        assertEquals(week.tops[1].day, obj.tops[1].day)
        assertEquals(week.tops[2].top, obj.tops[2].top)
        assertEquals(week.tops[2].day, obj.tops[2].day)
    }

    @Test
    fun enumWithInit() {
        data class C(val c: BrasWithInit)

        val c = C(BrasWithInit.PUSHUP)
        val obj = DeserializationInput(sf1).deserialize(TestSerializationOutput(VERBOSE, sf1).serialize(c))

        assertEquals(c.c, obj.c)
    }

    @Test(expected = NotSerializableException::class)
    fun changedEnum1() {
        val url = EnumTests::class.java.getResource("EnumTests.changedEnum1")

        data class C(val a: OldBras)

        // Original version of the class for the serialised version of this class
        //
        // val a = OldBras.TSHIRT
        // val sc = SerializationOutput(sf1).serialize(C(a))
        // f.writeBytes(sc.bytes)
        // println(path)

        val sc2 = url.readBytes()

        // we expect this to throw
        DeserializationInput(sf1).deserialize(SerializedBytes<C>(sc2))
    }

    @Test(expected = NotSerializableException::class)
    fun changedEnum2() {
        val url = EnumTests::class.java.getResource("EnumTests.changedEnum2")

        data class C(val a: OldBras2)

        // DO NOT CHANGE THIS, it's important we serialise with a value that doesn't
        // change position in the upated enum class

        // Original version of the class for the serialised version of this class
        //
        // val a = OldBras2.UNDERWIRE
        // val sc = SerializationOutput(sf1).serialize(C(a))
        // f.writeBytes(sc.bytes)
        // println(path)

        val sc2 = url.readBytes()

        // we expect this to throw
        DeserializationInput(sf1).deserialize(SerializedBytes<C>(sc2))
    }

    @Test
    fun enumNotWhitelistedFails() {
        data class C(val c: Bras)

        class WL(val allowed: String) : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean {
                return type.name == allowed
            }
        }

        val factory = SerializerFactory(WL(classTestName("C")), ClassLoader.getSystemClassLoader())

        Assertions.assertThatThrownBy {
            TestSerializationOutput(VERBOSE, factory).serialize(C(Bras.UNDERWIRE))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @Test
    fun enumWhitelisted() {
        data class C(val c: Bras)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean {
                return type.name == "net.corda.nodeapi.internal.serialization.amqp.EnumTests\$enumWhitelisted\$C" ||
                        type.name == "net.corda.nodeapi.internal.serialization.amqp.EnumTests\$Bras"
            }
        }

        val factory = SerializerFactory(WL(), ClassLoader.getSystemClassLoader())

        // if it all works, this won't explode
        TestSerializationOutput(VERBOSE, factory).serialize(C(Bras.UNDERWIRE))
    }

    @Test
    fun enumAnnotated() {
        @CordaSerializable data class C(val c: AnnotatedBras)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) = false
        }

        val factory = SerializerFactory(WL(), ClassLoader.getSystemClassLoader())

        // if it all works, this won't explode
        TestSerializationOutput(VERBOSE, factory).serialize(C(AnnotatedBras.UNDERWIRE))
    }

    @Test
    fun deserializeNonWhitlistedEnum() {
        data class C(val c: Bras)

        class WL(val allowed: List<String>) : ClassWhitelist {
            override fun hasListed(type: Class<*>) = type.name in allowed
        }

        // first serialise the class using a context in which Bras are whitelisted
        val factory = SerializerFactory(WL(listOf(classTestName("C"),
                "net.corda.nodeapi.internal.serialization.amqp.EnumTests\$Bras")),
                ClassLoader.getSystemClassLoader())
        val bytes = TestSerializationOutput(VERBOSE, factory).serialize(C(Bras.UNDERWIRE))

        // then take that serialised object and attempt to deserialize it in a context that
        // disallows the Bras enum
        val factory2 = SerializerFactory(WL(listOf(classTestName("C"))), ClassLoader.getSystemClassLoader())
        Assertions.assertThatThrownBy {
            DeserializationInput(factory2).deserialize(bytes)
        }.isInstanceOf(NotSerializableException::class.java)
    }
}