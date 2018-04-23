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

import net.corda.core.serialization.CordaSerializable
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.CompositeType
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MultiMemberCompositeSchemaToClassCarpenterTests : AmqpCarpenterBase(AllWhitelist) {

    @Test
    fun twoInts() {
        @CordaSerializable
        data class A(val a: Int, val b: Int)

        val testA = 10
        val testB = 20
        val a = A(testA, testB)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(a))

        assert(obj.obj is A)
        val amqpObj = obj.obj as A

        assertEquals(testA, amqpObj.a)
        assertEquals(testB, amqpObj.b)
        assertEquals(1, obj.envelope.schema.types.size)
        assert(obj.envelope.schema.types[0] is CompositeType)

        val amqpSchema = obj.envelope.schema.types[0] as CompositeType

        assertEquals(2, amqpSchema.fields.size)
        assertEquals("a", amqpSchema.fields[0].name)
        assertEquals("int", amqpSchema.fields[0].type)
        assertEquals("b", amqpSchema.fields[1].name)
        assertEquals("int", amqpSchema.fields[1].type)

        val carpenterSchema = CarpenterMetaSchema.newInstance()
        amqpSchema.carpenterSchema(
                classloader = ClassLoader.getSystemClassLoader(),
                carpenterSchemas = carpenterSchema,
                force = true)

        assertEquals(1, carpenterSchema.size)
        val aSchema = carpenterSchema.carpenterSchemas.find { it.name == classTestName("A") }

        assertNotEquals(null, aSchema)

        val pinochio = ClassCarpenter(whitelist = AllWhitelist).build(aSchema!!)
        val p = pinochio.constructors[0].newInstance(testA, testB)

        assertEquals(pinochio.getMethod("getA").invoke(p), amqpObj.a)
        assertEquals(pinochio.getMethod("getB").invoke(p), amqpObj.b)
    }

    @Test
    fun intAndStr() {
        @CordaSerializable
        data class A(val a: Int, val b: String)

        val testA = 10
        val testB = "twenty"
        val a = A(testA, testB)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(a))

        assert(obj.obj is A)
        val amqpObj = obj.obj as A

        assertEquals(testA, amqpObj.a)
        assertEquals(testB, amqpObj.b)
        assertEquals(1, obj.envelope.schema.types.size)
        assert(obj.envelope.schema.types[0] is CompositeType)

        val amqpSchema = obj.envelope.schema.types[0] as CompositeType

        assertEquals(2, amqpSchema.fields.size)
        assertEquals("a", amqpSchema.fields[0].name)
        assertEquals("int", amqpSchema.fields[0].type)
        assertEquals("b", amqpSchema.fields[1].name)
        assertEquals("string", amqpSchema.fields[1].type)

        val carpenterSchema = CarpenterMetaSchema.newInstance()
        amqpSchema.carpenterSchema(
                classloader = ClassLoader.getSystemClassLoader(),
                carpenterSchemas = carpenterSchema,
                force = true)

        assertEquals(1, carpenterSchema.size)
        val aSchema = carpenterSchema.carpenterSchemas.find { it.name == classTestName("A") }

        assertNotEquals(null, aSchema)

        val pinochio = ClassCarpenter(whitelist = AllWhitelist).build(aSchema!!)
        val p = pinochio.constructors[0].newInstance(testA, testB)

        assertEquals(pinochio.getMethod("getA").invoke(p), amqpObj.a)
        assertEquals(pinochio.getMethod("getB").invoke(p), amqpObj.b)
    }

}

