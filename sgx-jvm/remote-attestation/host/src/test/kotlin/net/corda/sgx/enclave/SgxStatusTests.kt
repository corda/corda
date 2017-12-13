package net.corda.sgx.enclave

import net.corda.sgx.system.SgxSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.test.assertFails

@Suppress("KDocMissingDocumentation")
class SgxStatusTests {

    @Test
    fun `can retrieve status from valid integer`() {
        assertEquals(SgxStatus.SUCCESS, SgxSystem.statusFromCode(0))
        assertEquals(SgxStatus.ERROR_BUSY, SgxSystem.statusFromCode(0x400a))
        assertEquals(SgxStatus.ERROR_FILE_CLOSE_FAILED, SgxSystem.statusFromCode(0x7009))
        assertNotEquals(SgxStatus.ERROR_AE_INVALID_EPIDBLOB, SgxSystem.statusFromCode(0x4003 - 1))
    }

    @Test
    fun `cannot retrieve status from invalid integer`() {
        assertFails { SgxSystem.statusFromCode(-1) }
        assertFails { SgxSystem.statusFromCode(0xfedc) }
    }

}
