package net.corda.sgx.system

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertNull

@Suppress("KDocMissingDocumentation")
class ExtendedGroupIdentifierTests {

    @Test
    fun `can retrieve extended group identifier for Intel`() {
        assertEquals(
                ExtendedGroupIdentifier.INTEL,
                SgxSystem.extendedGroupIdentifier(0)
        )
    }

    @Test
    fun `cannot retrieve other extended group identifiers`() {
        assertNull(SgxSystem.extendedGroupIdentifier(-1))
        assertNull(SgxSystem.extendedGroupIdentifier(1))
        assertNull(SgxSystem.extendedGroupIdentifier(2))
        assertNull(SgxSystem.extendedGroupIdentifier(10))
    }

}
