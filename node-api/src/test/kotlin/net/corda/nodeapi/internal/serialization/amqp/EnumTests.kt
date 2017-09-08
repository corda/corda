package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test
import java.time.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

import java.io.File
import java.io.NotSerializableException

import net.corda.core.serialization.SerializedBytes

class EnumTests {
    enum class Bras {
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
    // occuring after this won't have changed the ordinality of our serialised value
    // and thus should still be deserialisable
    enum class OldBras2 {
        TSHIRT, UNDERWIRE, PUSHUP, SPACER, BRALETTE, SPACER2
    }


    enum class BrasWithInit (val someList: List<Int>) {
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

    private val sf1 = testDefaultFactory()

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
            assertNotNull (schema_bras.choices.find { it.name == bra.name })
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
            assertNotNull (schema_bras.choices.find { it.name == bra.name })
        }

        // Test the actual deserialised object
        assertEquals(obj.c, Bras.UNDERWIRE)
    }

    @Test
    fun multiEnum() {
        data class Support (val top: Bras, val day : DayOfWeek)
        data class WeeklySupport (val tops: List<Support>)

        val week = WeeklySupport (listOf(
            Support (Bras.PUSHUP, DayOfWeek.MONDAY),
            Support (Bras.UNDERWIRE, DayOfWeek.WEDNESDAY),
            Support (Bras.PADDED, DayOfWeek.SUNDAY)))

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

        val c = C (BrasWithInit.PUSHUP)
        val obj = DeserializationInput(sf1).deserialize(TestSerializationOutput(VERBOSE, sf1).serialize(c))

        assertEquals(c.c, obj.c)
    }

    @Test(expected = NotSerializableException::class)
    fun changedEnum1() {
        val path = EnumTests::class.java.getResource("EnumTests.changedEnum1")
        val f = File(path.toURI())

        data class C (val a: OldBras)

        // Original version of the class for the serialised version of this class
        //
        // val a = OldBras.TSHIRT
        // val sc = SerializationOutput(sf1).serialize(C(a))
        // f.writeBytes(sc.bytes)
        // println(path)

        val sc2 = f.readBytes()

        // we expect this to throw
        DeserializationInput(sf1).deserialize(SerializedBytes<C>(sc2))
    }

    @Test(expected = NotSerializableException::class)
    fun changedEnum2() {
        val path = EnumTests::class.java.getResource("EnumTests.changedEnum2")
        val f = File(path.toURI())

        data class C (val a: OldBras2)

        // DO NOT CHANGE THIS, it's important we serialise with a value that doesn't
        // change position in the upated enum class

        // Original version of the class for the serialised version of this class
        //
        // val a = OldBras2.UNDERWIRE
        // val sc = SerializationOutput(sf1).serialize(C(a))
        // f.writeBytes(sc.bytes)
        // println(path)

        val sc2 = f.readBytes()

        // we expect this to throw
        DeserializationInput(sf1).deserialize(SerializedBytes<C>(sc2))
    }
}