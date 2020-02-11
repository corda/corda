package net.corda.core.contracts

import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class PrivacySaltTest {
    @Test(timeout=300_000)
	fun `all-zero PrivacySalt not allowed`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            PrivacySalt(ByteArray(32))
        }.withMessage("Privacy salt should not be all zeros.")
    }
}