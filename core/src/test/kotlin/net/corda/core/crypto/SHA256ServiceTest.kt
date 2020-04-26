package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals

class SHA256ServiceTest {
    @Test(timeout = 300_000)
    fun testHashBytes() {
        val hash = SHA256DigestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a))
        Assert.assertEquals("6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F", hash.toString())
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = SHA256DigestService.hash("test")
        Assert.assertEquals("9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08", hash.toString())
    }

    @Test(timeout = 300_000)
    fun testGetAllOnesHash() {
        Assert.assertArrayEquals(SHA256DigestService.allOnesHash.bytes, ByteArray(32) { 0xFF.toByte() })
    }

    @Test(timeout = 300_000)
    fun testGetZeroHash() {
        Assert.assertArrayEquals(SHA256DigestService.zeroHash.bytes, ByteArray(32))
    }

    @Test(timeout = 300_000)
    fun `sha256 does not retain state between same-thread invocations`() {
        assertEquals(SHA256DigestService.hash("abc"), SHA256DigestService.hash("abc"))
    }
}