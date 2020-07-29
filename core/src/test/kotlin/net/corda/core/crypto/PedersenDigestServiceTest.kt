package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class PedersenDigestServiceTest {
    private val service: DigestService = PedersenDigestService

    @Test(timeout = 300_000)
    fun testHashBytes() {
        val m = 48058
        val hash = service.hash(m.toBigInteger().toByteArray())
        assertEquals(hash.bytes[0], 0)
        assertEquals(32, hash.size)
        assertEquals("4dfcf879a397d2e531f57282a5ef770953210f4e5030bc0d4526cf47fa2d00", BigInteger(hash.bytes).toString(16))
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("5C1DDC5496CF258ACB01D3097BE28AFB08E30FC7E9C8E8140AD87A3927666A75", hash.toString())
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
    fun `Pedersen does not retain state between same-thread invocations`() {
        assertEquals(service.hash("abc"), service.hash("abc"))
    }
}