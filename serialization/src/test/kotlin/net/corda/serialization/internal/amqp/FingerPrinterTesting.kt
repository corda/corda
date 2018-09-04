package net.corda.serialization.internal.amqp

import org.junit.Test
import java.lang.reflect.Type
import kotlin.test.assertEquals
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.serializeAndReturnSchema

class FingerPrinterTesting : FingerPrinter {
    private var index = 0
    private val cache = mutableMapOf<Type, String>()

    override fun fingerprint(type: Type): String {
        return cache.computeIfAbsent(type) { index++.toString() }
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
                evolutionSerializerGetter = EvolutionSerializerGetterTesting(),
                fingerPrinterConstructor = { _ -> FingerPrinterTesting() })

        val blob = TestSerializationOutput(VERBOSE, factory).serializeAndReturnSchema(C(1, 2L))

        assertEquals(1, blob.schema.types.size)
        assertEquals("<descriptor name=\"net.corda:0\"/>", blob.schema.types[0].descriptor.toString())
    }
}