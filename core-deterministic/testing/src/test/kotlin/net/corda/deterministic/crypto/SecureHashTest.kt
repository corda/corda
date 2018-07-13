package net.corda.deterministic.crypto

import net.corda.core.crypto.SecureHash
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SecureHashTest {
    @Test
    fun testSHA256() {
        val hash = SecureHash.sha256(byteArrayOf(0x64, -0x13, 0x42, 0x3a))
        assertEquals(SecureHash.parse("6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F"), hash)
        assertEquals("6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F", hash.toString())
    }

    @Test
    fun testPrefix() {
        val data = byteArrayOf(0x7d, 0x03, -0x21, 0x32, 0x56, 0x47)
        val digest = data.digestFor("SHA-256")
        val prefix = SecureHash.sha256(data).prefixChars(8)
        assertEquals(Hex.toHexString(digest).substring(0, 8).toUpperCase(), prefix)
    }

    @Test
    fun testConcat() {
        val hash1 = SecureHash.sha256(byteArrayOf(0x7d, 0x03, -0x21, 0x32, 0x56, 0x47))
        val hash2 = SecureHash.sha256(byteArrayOf(0x63, 0x01, 0x7f, -0x29, 0x1e, 0x3c))
        val combined = hash1.hashConcat(hash2)
        assertArrayEquals((hash1.bytes + hash2.bytes).digestFor("SHA-256"), combined.bytes)
    }

    @Test
    fun testConstants() {
        assertArrayEquals(SecureHash.zeroHash.bytes, ByteArray(32))
        assertArrayEquals(SecureHash.allOnesHash.bytes, ByteArray(32) { 0xFF.toByte() })
    }
}

private fun ByteArray.digestFor(algorithm: String): ByteArray {
    return MessageDigest.getInstance(algorithm).digest(this)
}