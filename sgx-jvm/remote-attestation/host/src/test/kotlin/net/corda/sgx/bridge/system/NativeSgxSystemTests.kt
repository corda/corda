package net.corda.sgx.bridge.system

import net.corda.sgx.system.ExtendedGroupIdentifier
import net.corda.sgx.system.SgxDeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("KDocMissingDocumentation")
class NativeSgxSystemTests {

    @Test
    fun `can retrieve device status`() {
        val system = NativeSgxSystem()
        assertEquals(SgxDeviceStatus.ENABLED, system.getDeviceStatus())
    }

    @Test
    fun `can retrieve extended group identifier`() {
        val system = NativeSgxSystem()
        val identifier = system.getExtendedGroupIdentifier()
        assertEquals(ExtendedGroupIdentifier.INTEL, identifier)
    }

}