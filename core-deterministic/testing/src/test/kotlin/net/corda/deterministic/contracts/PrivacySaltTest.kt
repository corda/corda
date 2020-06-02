package net.corda.deterministic.contracts

import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.SecureHash.Companion.SHA2_256
import org.junit.Test
import kotlin.test.*

class PrivacySaltTest {
    private companion object {
        private const val SALT_SIZE = 32
    }

    @Test(timeout=300_000)
	fun testValidSalt() {
        PrivacySalt(ByteArray(SALT_SIZE) { 0x14 })
    }

    @Test(timeout=300_000)
	fun testInvalidSaltWithAllZeros() {
        val ex = assertFailsWith<IllegalArgumentException> { PrivacySalt(ByteArray(SALT_SIZE)) }
        assertEquals("Privacy salt should not be all zeros.", ex.message)
    }

    @Test(timeout=300_000)
	fun testTooShortPrivacySaltForSHA256() {
        val ex = assertFailsWith<IllegalArgumentException> { PrivacySalt(ByteArray(SALT_SIZE - 1) { 0x7f }).apply { validateFor(SHA2_256) } }
        assertEquals("Privacy salt should be 32 bytes.", ex.message)
    }

    @Test(timeout=300_000)
	fun testTooLongPrivacySaltForSHA256() {
        val ex = assertFailsWith<IllegalArgumentException> { PrivacySalt(ByteArray(SALT_SIZE + 1) { 0x7f }).apply { validateFor(SHA2_256) } }
        assertEquals("Privacy salt should be 32 bytes.", ex.message)
    }
}