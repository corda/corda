/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.carpenter

import net.corda.nodeapi.internal.serialization.AllWhitelist
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnumClassTests : AmqpCarpenterBase(AllWhitelist) {

    @Test
    fun oneValue() {
        val enumConstants = mapOf("A" to EnumField())

        val schema = EnumSchema("gen.enum", enumConstants)

        assertTrue(cc.build(schema).isEnum)
    }

    @Test
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

    @Test
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

    @Test
    fun manyValues() {
        val enumConstants = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF",
                "GGG", "HHH", "III", "JJJ").associateBy({ it }, { EnumField() })
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

    @Test
    fun assignment() {
        val enumConstants = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF").associateBy({ it }, { EnumField() })
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
    @Test
    fun assignAndTest() {
        val cc2 = ClassCarpenterImpl(whitelist = AllWhitelist)

        val schema1 = EnumSchema("gen.enum",
                listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF").associateBy({ it }, { EnumField() }))

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