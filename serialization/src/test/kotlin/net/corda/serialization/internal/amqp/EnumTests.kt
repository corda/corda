package net.corda.serialization.internal.amqp

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.EmptyWhitelist
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.serialization.internal.amqp.testutils.serializeAndReturnSchema
import net.corda.serialization.internal.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.serialization.internal.amqp.testutils.testName
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.io.NotSerializableException
import java.time.DayOfWeek
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    private inline fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"

    private val sf1 = testDefaultFactoryNoEvolution()

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun enumWithInit() {
        data class C(val c: BrasWithInit)

        val c = C(BrasWithInit.PUSHUP)
        val obj = DeserializationInput(sf1).deserialize(TestSerializationOutput(VERBOSE, sf1).serialize(c))

        assertEquals(c.c, obj.c)
    }

    @Test(timeout=300_000)
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
        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            DeserializationInput(sf1).deserialize(SerializedBytes<C>(sc2))
        }
    }

    @Test(timeout=300_000)
    fun changedEnum2() {
        val url = EnumTests::class.java.getResource("EnumTests.changedEnum2")

        data class C(val a: OldBras2)

        // DO NOT CHANGE THIS, it's important we serialise with a value that doesn't
        // change position in the updated enum class

        // Original version of the class for the serialised version of this class
        //
        // val a = OldBras2.UNDERWIRE
        // val sc = SerializationOutput(sf1).serialize(C(a))
        // f.writeBytes(sc.bytes)
        // println(path)

        val sc2 = url.readBytes()

        // we expect this to throw
        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            DeserializationInput(sf1).deserialize(SerializedBytes<C>(sc2))
        }
    }

    @Test(timeout=300_000)
	fun enumNotWhitelistedFails() {
        data class C(val c: Bras)

        class WL(val allowed: String) : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean {
                return type.name == allowed
            }
        }

        val whitelist = WL(classTestName("C"))
        val factory = SerializerFactoryBuilder.build(whitelist,
                ClassCarpenterImpl(whitelist, ClassLoader.getSystemClassLoader())
        )

        Assertions.assertThatThrownBy {
            TestSerializationOutput(VERBOSE, factory).serialize(C(Bras.UNDERWIRE))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @Test(timeout=300_000)
	fun enumWhitelisted() {
        data class C(val c: Bras)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean {
                return type.name == "net.corda.serialization.internal.amqp.EnumTests\$enumWhitelisted\$C" ||
                        type.name == "net.corda.serialization.internal.amqp.EnumTests\$Bras"
            }
        }

        val whitelist = WL()
        val factory = SerializerFactoryBuilder.build(whitelist,
                ClassCarpenterImpl(whitelist, ClassLoader.getSystemClassLoader())
        )

        // if it all works, this won't explode
        TestSerializationOutput(VERBOSE, factory).serialize(C(Bras.UNDERWIRE))
    }

    @Test(timeout=300_000)
	fun enumAnnotated() {
        @CordaSerializable data class C(val c: AnnotatedBras)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) = false
        }

        val whitelist = WL()
        val factory = SerializerFactoryBuilder.build(whitelist,
                ClassCarpenterImpl(whitelist, ClassLoader.getSystemClassLoader())
        )

        // if it all works, this won't explode
        TestSerializationOutput(VERBOSE, factory).serialize(C(AnnotatedBras.UNDERWIRE))
    }

    @Test(timeout=300_000)
	fun deserializeNonWhitlistedEnum() {
        data class C(val c: Bras)

        class WL(val allowed: List<String>) : ClassWhitelist {
            override fun hasListed(type: Class<*>) = type.name in allowed
        }

        // first serialise the class using a context in which Bras are whitelisted
        val whitelist = WL(listOf(classTestName("C"),
                "net.corda.serialization.internal.amqp.EnumTests\$Bras"))
        val factory = SerializerFactoryBuilder.build(whitelist,
                ClassCarpenterImpl(whitelist, ClassLoader.getSystemClassLoader())
        )
        val bytes = TestSerializationOutput(VERBOSE, factory).serialize(C(Bras.UNDERWIRE))

        // then take that serialised object and attempt to deserialize it in a context that
        // disallows the Bras enum
        val whitelist1 = WL(listOf(classTestName("C")))
        val factory2 = SerializerFactoryBuilder.build(whitelist1,
                ClassCarpenterImpl(whitelist1, ClassLoader.getSystemClassLoader())
        )
        Assertions.assertThatThrownBy {
            DeserializationInput(factory2).deserialize(bytes)
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @Test(timeout = 300_000)
    fun deserializeCustomisedEnum() {
        val input = CustomEnumWrapper(CustomEnum.ONE)
        val factory1 = SerializerFactoryBuilder.build(EmptyWhitelist, ClassLoader.getSystemClassLoader())
        val serialized = TestSerializationOutput(VERBOSE, factory1).serialize(input)

        val factory2 = SerializerFactoryBuilder.build(EmptyWhitelist, ClassLoader.getSystemClassLoader())
        val output = DeserializationInput(factory2).deserialize(serialized)

        assertEquals(input, output)
        assertNotSame("Deserialized object should be brand new.", input, output)
    }

    @Suppress("unused")
    @CordaSerializable
    enum class CustomEnum {
        ONE,
        TWO,
        THREE;

        override fun toString(): String {
            return "[${name.lowercase(Locale.getDefault())}]"
        }
    }

    @CordaSerializable
    data class CustomEnumWrapper(val data: CustomEnum)
}