package net.corda.testing

import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestIdentityTests {
    @Test
    fun `entropy works`() {
        val a = TestIdentity(ALICE_NAME, 123)
        val b = TestIdentity(BOB_NAME, 123)
        assertEquals(a.publicKey, b.publicKey)
        assertEquals(a.keyPair.private, b.keyPair.private)
    }

    @Test
    fun `fresh works`() {
        val x = TestIdentity.fresh("xx")
        val y = TestIdentity.fresh("yy")
        // The param is called organisation so we'd better use it as such:
        assertEquals("xx", x.name.organisation)
        assertEquals("yy", y.name.organisation)
        // A fresh identity shouldn't be equal to anything by accident:
        assertNotEquals(x.name, y.name)
        assertNotEquals(x.publicKey, y.publicKey)
    }
}
