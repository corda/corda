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
        assertEquals(32, hash.size)
        assertEquals("1442d41699e4206a8b5da70c2211925d25cad29f5b2709399fed5db14a11d401", BigInteger(hash.bytes).toString(16))
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("09A5A5D1BD4878CD0D0D2376155A6EE51AA29FB8F74D7110C25053CA207F5F4E", hash.toString())
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