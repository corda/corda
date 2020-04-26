package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals

class Blake2b256ServiceTest {
    private val service: DigestService = BLAKE2b256DigestService

    @Test(timeout = 300_000)
    fun testHashBytes() {
        val hash = service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a))
        Assert.assertEquals("B79E85986C249B4CAAF61D4308DC4A7BF1D07F684D7A42A6B17D9F3D9F2962E4", hash.toString())
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("928B20366943E2AFD11EBC0EAE2E53A93BF177A4FCF35BCC64D503704E65E202", hash.toString())
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
    fun `Blake2b256 does not retain state between same-thread invocations`() {
        assertEquals(service.hash("abc"), service.hash("abc"))
    }
}