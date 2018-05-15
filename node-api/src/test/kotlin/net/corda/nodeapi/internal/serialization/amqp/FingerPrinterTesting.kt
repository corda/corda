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

import org.junit.Test
import java.lang.reflect.Type
import kotlin.test.assertEquals
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.testutils.serializeAndReturnSchema

class FingerPrinterTesting : FingerPrinter {
    private var index = 0
    private val cache = mutableMapOf<Type, String>()

    override fun fingerprint(type: Type): String {
        return cache.computeIfAbsent(type) { index++.toString() }
    }

    override fun setOwner(factory: SerializerFactory) {
        return
    }

    @Suppress("UNUSED")
    fun changeFingerprint(type: Type) {
        cache.computeIfAbsent(type) { "" }.apply { index++.toString() }
    }
}

class FingerPrinterTestingTests {
    companion object {
        const val VERBOSE = true
    }

    @Test
    fun testingTest() {
        val fpt = FingerPrinterTesting()
        assertEquals("0", fpt.fingerprint(Integer::class.java))
        assertEquals("1", fpt.fingerprint(String::class.java))
        assertEquals("0", fpt.fingerprint(Integer::class.java))
        assertEquals("1", fpt.fingerprint(String::class.java))
    }

    @Test
    fun worksAsReplacement() {
        data class C(val a: Int, val b: Long)

        val factory = SerializerFactory(
                AllWhitelist,
                ClassLoader.getSystemClassLoader(),
                EvolutionSerializerGetterTesting(),
                FingerPrinterTesting())

        val blob = TestSerializationOutput(VERBOSE, factory).serializeAndReturnSchema(C(1, 2L))

        assertEquals(1, blob.schema.types.size)
        assertEquals("<descriptor name=\"net.corda:0\"/>", blob.schema.types[0].descriptor.toString())
    }
}