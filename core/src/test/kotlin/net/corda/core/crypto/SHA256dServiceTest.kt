package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals

class SHA256dServiceTest {
    private val service: DigestService = DefaultDigestServiceFactory.getService(Algorithm.SHA256d())

    @Test(timeout = 300_000)
    fun testHashBytes() {
        val hash = service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a))
        Assert.assertEquals("CB2A6BC131E59DC17DF10769ACBDFEC06965F0AFEAF1C3359E69CB915873E051", hash.toString())
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("954D5A49FD70D9B8BCDB35D252267829957F7EF7FA6C74F88419BDC5E82209F4", hash.toString())
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