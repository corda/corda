package net.corda.serialization.internal.amqp

import org.junit.Test
import kotlin.test.assertEquals
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.serializeAndReturnSchema
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.ConfigurableLocalTypeModel
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.FingerPrinter

class FingerPrinterTesting : FingerPrinter {
    private var index = 0
    private val cache = mutableMapOf<LocalTypeInformation, String>()

    override fun fingerprint(typeInformation: LocalTypeInformation): String {
        return cache.computeIfAbsent(typeInformation) { index++.toString() }
    }

    @Suppress("UNUSED")
    fun changeFingerprint(type: LocalTypeInformation) {
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
        val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
        val customSerializerRegistry: CustomSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)
        val typeModel = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist, customSerializerRegistry))

        assertEquals("0", fpt.fingerprint(typeModel.inspect(Integer::class.java)))
        assertEquals("1", fpt.fingerprint(typeModel.inspect(String::class.java)))
        assertEquals("0", fpt.fingerprint(typeModel.inspect(Integer::class.java)))
        assertEquals("1", fpt.fingerprint(typeModel.inspect(String::class.java)))
    }

    @Test
    fun worksAsReplacement() {
        data class C(val a: Int, val b: Long)

        val factory = SerializerFactoryBuilder.build(AllWhitelist,
                ClassCarpenterImpl(AllWhitelist, ClassLoader.getSystemClassLoader()),
                overrideFingerPrinter = FingerPrinterTesting())

        val blob = TestSerializationOutput(VERBOSE, factory).serializeAndReturnSchema(C(1, 2L))

        assertEquals(1, blob.schema.types.size)
        assertEquals("<descriptor name=\"net.corda:0\"/>", blob.schema.types[0].descriptor.toString())
    }
}