package net.corda.serialization.internal.carpenter

import net.corda.serialization.internal.AllWhitelist
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnumClassTests : AmqpCarpenterBase(AllWhitelist) {

    @Test(timeout=300_000)
	fun oneValue() {
        val enumConstants = mapOf("A" to EnumField())

        val schema = EnumSchema("gen.enum", enumConstants)

        assertTrue(cc.build(schema).isEnum)
    }

    @Test(timeout=300_000)
	fun oneValueInstantiate() {
        val enumConstants = mapOf("A" to EnumField())
        val schema = EnumSchema("gen.enum", enumConstants)
        val clazz = cc.build(schema)

        assertTrue(clazz.isEnum)
        assertEquals(enumConstants.size, clazz.enumConstants.size)
        assertEquals("A", clazz.enumConstants.first().toString())
        assertEquals(0, (clazz.enumConstants.first() as Enum<*>).ordinal)
        assertEquals("A", (clazz.enumConstants.first() as Enum<*>).name)
    }

    @Test(timeout=300_000)
	fun twoValuesInstantiate() {
        val enumConstants = mapOf("left" to EnumField(), "right" to EnumField())
        val schema = EnumSchema("gen.enum", enumConstants)
        val clazz = cc.build(schema)

        assertTrue(clazz.isEnum)
        assertEquals(enumConstants.size, clazz.enumConstants.size)

        val left = clazz.enumConstants[0] as Enum<*>
        val right = clazz.enumConstants[1] as Enum<*>

        assertEquals(0, left.ordinal)
        assertEquals("left", left.name)
        assertEquals(1, right.ordinal)
        assertEquals("right", right.name)
    }

    @Test(timeout=300_000)
	fun manyValues() {
        val enumConstants = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF",
                "GGG", "HHH", "III", "JJJ").associateWith { EnumField() }
        val schema = EnumSchema("gen.enum", enumConstants)
        val clazz = cc.build(schema)

        assertTrue(clazz.isEnum)
        assertEquals(enumConstants.size, clazz.enumConstants.size)

        var idx = 0
        enumConstants.forEach {
            val constant = clazz.enumConstants[idx] as Enum<*>
            assertEquals(idx++, constant.ordinal)
            assertEquals(it.key, constant.name)
        }
    }

    @Test(timeout=300_000)
	fun assignment() {
        val enumConstants = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF").associateWith { EnumField() }
        val schema = EnumSchema("gen.enum", enumConstants)
        val clazz = cc.build(schema)

        assertEquals("CCC", clazz.getMethod("valueOf", String::class.java).invoke(null, "CCC").toString())
        assertEquals("CCC", (clazz.getMethod("valueOf", String::class.java).invoke(null, "CCC") as Enum<*>).name)

        val ddd = clazz.getMethod("valueOf", String::class.java).invoke(null, "DDD") as Enum<*>

        assertTrue(ddd::class.java.isEnum)
        assertEquals("DDD", ddd.name)
        assertEquals(3, ddd.ordinal)
    }

    // if anything goes wrong with this test it's going to end up throwing *some*
    // exception, hence the lack of asserts
    @Test(timeout=300_000)
	fun assignAndTest() {
        val cc2 = ClassCarpenterImpl(whitelist = AllWhitelist)

        val schema1 = EnumSchema("gen.enum",
                listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF").associateWith { EnumField() })

        val enumClazz = cc2.build(schema1)

        val schema2 = ClassSchema("gen.class",
                mapOf(
                        "a" to NonNullableField(Int::class.java),
                        "b" to NonNullableField(enumClazz)))

        val classClazz = cc2.build(schema2)

        // make sure we can construct a class that has an enum we've constructed as a member
        classClazz.constructors[0].newInstance(1, enumClazz.getMethod(
                "valueOf", String::class.java).invoke(null, "BBB"))
    }
}