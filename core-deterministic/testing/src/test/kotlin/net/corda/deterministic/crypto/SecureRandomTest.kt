package net.corda.deterministic.crypto

import net.corda.core.crypto.CordaSecurityProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import kotlin.test.assertFailsWith

class SecureRandomTest {
    private companion object {
        init {
            CordaSecurityProvider()
        }
    }

    @Test
    fun testNoCordaPRNG() {
        val error = assertFailsWith<NoSuchAlgorithmException> { SecureRandom.getInstance("CordaPRNG") }
        assertThat(error).hasMessage("CordaPRNG SecureRandom not available")
    }
}