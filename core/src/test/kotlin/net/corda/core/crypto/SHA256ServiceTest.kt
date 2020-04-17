package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals

class SHA256ServiceTest {
    private val service: DigestService = DefaultDigestServiceFactory.getService(Algorithm.SHA256())

    @Test(timeout = 300_000)
    fun testHashBytes() {
        val hash = service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a))
        Assert.assertEquals("6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F", hash.toString())
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08", hash.toString())
    }

    @Test(timeout = 300_000)
    fun testGetAllOnesHash() {
        Assert.assertArrayEquals(service.allOnesHash.bytes, ByteArray(32) { 0xFF.toByte() })
    }

    @Test(timeout = 300_000)
    fun testGetZeroHash() {
        Assert.assertArrayEquals(service.zeroHash.bytes, ByteArray(32))
    }

    @Test(timeout = 300_000)
    fun `sha256 does not retain state between same-thread invocations`() {
        assertEquals(service.hash("abc"), service.hash("abc"))
    }
}