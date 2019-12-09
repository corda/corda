package net.corda.core.crypto

import org.junit.Test
import kotlin.test.assertEquals

class SecureHashTest {
    @Test
    fun `sha256 does not retain state between same-thread invocations`() {
        assertEquals(SecureHash.sha256("abc"), SecureHash.sha256("abc"))
    }
}
