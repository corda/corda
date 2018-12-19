package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeModellingFingerPrinter
import org.junit.Test
import kotlin.test.assertNotEquals

class TypeModellingFingerPrinterTests {

    val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
    val customRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)
    val fingerprinter = TypeModellingFingerPrinter(customRegistry, true)

    // See https://r3-cev.atlassian.net/browse/CORDA-2266
    @Test
    fun `Object and wildcard are fingerprinted differently`() {
        val objectType = LocalTypeInformation.Top
        val anyType = LocalTypeInformation.Unknown

        assertNotEquals(fingerprinter.fingerprint(objectType), fingerprinter.fingerprint(anyType))
    }
}