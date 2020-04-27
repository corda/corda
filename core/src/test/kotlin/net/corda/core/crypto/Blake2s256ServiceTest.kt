package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals

class Blake2s256DigestServiceTest {
    private val service: DigestService = BLAKE2s256DigestService

    @Test(timeout = 300_000)
    fun testBlankHash() {
        Assert.assertEquals(
                "C59F682376D137F3F255E671E207D1F2374EBE504E9314208A52D9F88D69E8C8",
                service.hash(byteArrayOf()).toString()
        )
    }

    @Test(timeout = 300_000)
    fun testHashBytes() {
        val hash = service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a))
        Assert.assertEquals("9EEA14092257E759ADAA56539A7A88DA1F68F03ABE3D9552A21D4731F4E6ECA0", hash.toString())
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        Assert.assertEquals("AB76E8F7EEA1968C183D343B756EC812E47D4BC7A3F061F4DDE8948B3E05DAF2", hash.toString())
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
    fun `Blake2s256 does not retain state between same-thread invocations`() {
        assertEquals(service.hash("abc"), service.hash("abc"))
    }
}