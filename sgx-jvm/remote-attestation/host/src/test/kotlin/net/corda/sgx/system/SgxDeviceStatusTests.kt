package net.corda.sgx.system

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFails

@Suppress("KDocMissingDocumentation")
class SgxDeviceStatusTests {

    @Test
    fun `can retrieve status from valid integer`() {
        assertEquals(SgxDeviceStatus.ENABLED, SgxSystem.deviceStatusFromCode(0))
        assertEquals(SgxDeviceStatus.DISABLED, SgxSystem.deviceStatusFromCode(3))
    }

    @Test
    fun `cannot retrieve status from invalid integer`() {
        assertFails { SgxSystem.deviceStatusFromCode(-1) }
        assertFails { SgxSystem.deviceStatusFromCode(0xfedc) }
    }

}
