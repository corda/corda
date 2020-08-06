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
        assertEquals(19, hash.bytes[0])
        assertEquals(32, hash.size)
        assertEquals("13a97f97e342ffec9cef470b8abad91197d762de9a590c166ee6d4200ea54e7a", BigInteger(hash.bytes).toString(16))
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("0B0F9A07F48EA22CFF421AD9C46830AC3C659550335F48B9FCB2042B75E1AC17", hash.toString())
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