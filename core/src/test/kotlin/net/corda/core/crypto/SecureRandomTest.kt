package net.corda.core.crypto

import org.junit.Test
import java.security.SecureRandom

class SecureRandomTest {
    @Test(timeout = 1000)
    fun `regular SecureRandom does not spend a lot of time seeding itself`() {
        val bytes = ByteArray(1000)
        repeat(10) {
            val sr = SecureRandom()
            repeat(100) {
                sr.nextBytes(bytes)
            }
        }
    }
}
