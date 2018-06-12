package net.corda.deterministic.contracts

import net.corda.core.contracts.PrivacySalt
import org.junit.Test
import kotlin.test.*

class PrivacySaltTest {
    private companion object {
        private const val SALT_SIZE = 32
    }

    @Test
    fun testValidSalt() {
        PrivacySalt(ByteArray(SALT_SIZE, { 0x14 }))
    }

    @Test
    fun testInvalidSaltWithAllZeros() {
        val ex = assertFailsWith<IllegalArgumentException> { PrivacySalt(ByteArray(SALT_SIZE)) }
        assertEquals("Privacy salt should not be all zeros.", ex.message)
    }

    @Test
    fun testTooShortPrivacySalt() {
        val ex = assertFailsWith<IllegalArgumentException> { PrivacySalt(ByteArray(SALT_SIZE - 1, { 0x7f })) }
        assertEquals("Privacy salt should be 32 bytes.", ex.message)
    }

    @Test
    fun testTooLongPrivacySalt() {
        val ex = assertFailsWith<IllegalArgumentException> { PrivacySalt(ByteArray(SALT_SIZE + 1, { 0x7f })) }
        assertEquals("Privacy salt should be 32 bytes.", ex.message)
    }
}