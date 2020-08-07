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
        assertEquals(32, hash.bytes[0])
        assertEquals(32, hash.size)
        assertEquals("20318970dc0db96855ce8e8ac6c358b304c1565b6207b447bf1be66557d89c09", BigInteger(hash.bytes).toString(16))
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("1EF7E9FCD0878D27F78DE9E583475649685B9F881D9C5544D5CF150E6BC47649", hash.toString())
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