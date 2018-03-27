package net.corda.nodeapi.internal.serialization.amqp

import net.corda.annotations.serialization.CordaSerializable
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializedBytes
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.File
import java.io.NotSerializableException
import java.time.DayOfWeek
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EnumTests {
    enum class UnderGarments {
        TSHIRT, UNDERWIRE, VEST, PANTS, STRAPLESS, BRIEFS, TEMPLE, HIPSTERS
    }

    @CordaSerializable
    enum class AnnotatedUnderGarments {
        TSHIRT, UNDERWIRE, VEST, PANTS, STRAPLESS, BRIEFS, TEMPLE, HIPSTERS
    }

    // The state of the OldUnderGarments enum when the tests in changedEnum1 were serialised
    //      - use if the test file needs regenerating
    //enum class OldUnderGarments {
    //    TSHIRT, UNDERWIRE, VEST, PANTS
    //}

    // the new state, SPACER has been added to change the ordinality
    enum class OldUnderGarments {
        SPACER, TSHIRT, UNDERWIRE, VEST, PANTS
    }

    // The state of the OldOldUnderGarments enum when the tests in changedEnum2 were serialised
    //      - use if the test file needs regenerating
    //enum class OldOldUnderGarments {
    //    TSHIRT, UNDERWIRE, VEST, PANTS
    //}

    // the new state, note in the test we serialised with value UNDERWIRE so the spacer
    // occurring after this won't have changed the ordinality of our serialised value
    // and thus should still be deserializable
    enum class OldOldUnderGarments {
        TSHIRT, UNDERWIRE, VEST, SPACER, PANTS, SPACER2
    }


    enum class BrasWithInit(val someList: List<Int>) {
        TSHIRT(emptyList()),
        UNDERWIRE(listOf(1, 2, 3)),
        VEST(listOf(100, 200)),
        PANTS(emptyList())
    }

    private val underGarmentsTestName = "${this.javaClass.name}\$UnderGarments"

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
        data class C(val c: UnderGarments)

        val schema = TestSerializationOutput(VERBOSE, sf1).serializeAndReturnSchema(C(UnderGarments.UNDERWIRE)).schema

        assertEquals(2, schema.types.size)
        val schema_c = schema.types.find { it.name == classTestName("C") } as CompositeType
        val schema_garments = schema.types.find { it.name == underGarmentsTestName } as RestrictedType

        assertNotNull(schema_c)
        assertNotNull(schema_garments)

        assertEquals(1, schema_c.fields.size)
        assertEquals("c", schema_c.fields.first().name)
        assertEquals(underGarmentsTestName, schema_c.fields.first().type)

        assertEquals(8, schema_garments.choices.size)
        UnderGarments.values().forEach {
            val garment = it
            assertNotNull(schema_garments.choices.find { it.name == garment.name })
        }
    }

    @Test
    fun deserialiseSimpleTest() {
        data class C(val c: UnderGarments)

        val objAndEnvelope = DeserializationInput(sf1).deserializeAndReturnEnvelope(
                TestSerializationOutput(VERBOSE, sf1).serialize(C(UnderGarments.UNDERWIRE)))

        val obj = objAndEnvelope.obj
        val schema = objAndEnvelope.envelope.schema

        assertEquals(2, schema.types.size)
        val schema_c = schema.types.find { it.name == classTestName("C") } as CompositeType
        val schema_garments = schema.types.find { it.name == underGarmentsTestName } as RestrictedType

        assertEquals(1, schema_c.fields.size)
        assertEquals("c", schema_c.fields.first().name)
        assertEquals(underGarmentsTestName, schema_c.fields.first().type)

        assertEquals(8, schema_garments.choices.size)
        UnderGarments.values().forEach {
            val garment = it
            assertNotNull(schema_garments.choices.find { it.name == garment.name })
        }

        // Test the actual deserialised object
        assertEquals(obj.c, UnderGarments.UNDERWIRE)
    }

    @Test
    fun multiEnum() {
        data class Support(val top: UnderGarments, val day: DayOfWeek)
        data class WeeklySupport(val tops: List<Support>)

        val week = WeeklySupport(listOf(
                Support(UnderGarments.VEST, DayOfWeek.MONDAY),
                Support(UnderGarments.UNDERWIRE, DayOfWeek.WEDNESDAY),
                Support(UnderGarments.HIPSTERS, DayOfWeek.SUNDAY)))

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

        val c = C(BrasWithInit.VEST)
        val obj = DeserializationInput(sf1).deserialize(TestSerializationOutput(VERBOSE, sf1).serialize(c))

        assertEquals(c.c, obj.c)
    }

    @Test(expected = NotSerializableException::class)
    fun changedEnum1() {
        val path = EnumTests::class.java.getResource("EnumTests.changedEnum1")
        val f = File(path.toURI())

        data class C(val a: OldUnderGarments)

        // Original version of the class for the serialised version of this class
        //
        // val a = OldUnderGarments.TSHIRT
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

        data class C(val a: OldOldUnderGarments)

        // DO NOT CHANGE THIS, it's important we serialise with a value that doesn't
        // change position in the upated enum class

        // Original version of the class for the serialised version of this class
        //
        // val a = OldOldUnderGarments.UNDERWIRE
        // val sc = SerializationOutput(sf1).serialize(C(a))
        // f.writeBytes(sc.bytes)
        // println(path)

        val sc2 = f.readBytes()

        // we expect this to throw
        DeserializationInput(sf1).deserialize(SerializedBytes<C>(sc2))
    }

    @Test
    fun enumNotWhitelistedFails() {
        data class C(val c: UnderGarments)

        class WL(val allowed: String) : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean {
                return type.name == allowed
            }
        }

        val factory = SerializerFactory(WL(classTestName("C")), ClassLoader.getSystemClassLoader())

        Assertions.assertThatThrownBy {
            TestSerializationOutput(VERBOSE, factory).serialize(C(UnderGarments.UNDERWIRE))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @Test
    fun enumWhitelisted() {
        data class C(val c: UnderGarments)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean {
                return type.name == "net.corda.nodeapi.internal.serialization.amqp.EnumTests\$enumWhitelisted\$C" ||
                        type.name == "net.corda.nodeapi.internal.serialization.amqp.EnumTests\$UnderGarments"
            }
        }

        val factory = SerializerFactory(WL(), ClassLoader.getSystemClassLoader())

        // if it all works, this won't explode
        TestSerializationOutput(VERBOSE, factory).serialize(C(UnderGarments.UNDERWIRE))
    }

    @Test
    fun enumAnnotated() {
        @CordaSerializable data class C(val c: AnnotatedUnderGarments)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) = false
        }

        val factory = SerializerFactory(WL(), ClassLoader.getSystemClassLoader())

        // if it all works, this won't explode
        TestSerializationOutput(VERBOSE, factory).serialize(C(AnnotatedUnderGarments.UNDERWIRE))
    }

    @Test
    fun deserializeNonWhitlistedEnum() {
        data class C(val c: UnderGarments)

        class WL(val allowed: List<String>) : ClassWhitelist {
            override fun hasListed(type: Class<*>) = type.name in allowed
        }

        // first serialise the class using a context in which UnderGarments are whitelisted
        val factory = SerializerFactory(WL(listOf(classTestName("C"),
                "net.corda.nodeapi.internal.serialization.amqp.EnumTests\$UnderGarments")),
                ClassLoader.getSystemClassLoader())
        val bytes = TestSerializationOutput(VERBOSE, factory).serialize(C(UnderGarments.UNDERWIRE))

        // then take that serialised object and attempt to deserialize it in a context that
        // disallows the UnderGarments enum
        val factory2 = SerializerFactory(WL(listOf(classTestName("C"))), ClassLoader.getSystemClassLoader())
        Assertions.assertThatThrownBy {
            DeserializationInput(factory2).deserialize(bytes)
        }.isInstanceOf(NotSerializableException::class.java)
    }
}