package net.corda.core.crypto

import org.bouncycastle.crypto.digests.Blake2sDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.test.assertEquals

class Blake2s256DigestServiceTest {
    private class BLAKE2s256DigestService : CustomMessageDigest {
        override val digestLength = 32

        override fun digest(bytes: ByteArray): ByteArray {
            val blake2s256 = Blake2sDigest(null, digestLength, null, "12345678".toByteArray())
            blake2s256.reset()
            blake2s256.update(bytes, 0, bytes.size)
            val hash = ByteArray(digestLength)
            blake2s256.doFinal(hash, 0)
            return hash
        }
    }

    private val service = DigestService.create(BLAKE2s256DigestService::class.java.name, hashTwiceNonce = false, hashTwiceComponent = false)

    @Test(timeout = 300_000)
    fun testBlankHash() {
        println("Class name ${BLAKE2s256DigestService::class.java.name}")
        assertEquals(
                "C59F682376D137F3F255E671E207D1F2374EBE504E9314208A52D9F88D69E8C8",
                service.hash(byteArrayOf()).toHexString()
        )
    }

    @Test(timeout = 300_000)
    fun testHashBytes() {
        val hash = service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a))
        assertEquals("9EEA14092257E759ADAA56539A7A88DA1F68F03ABE3D9552A21D4731F4E6ECA0", hash.toHexString())
    }

    @Test(timeout = 300_000)
    fun testHashString() {
        val hash = service.hash("test")
        assertEquals("AB76E8F7EEA1968C183D343B756EC812E47D4BC7A3F061F4DDE8948B3E05DAF2", hash.toHexString())
    }

    @Test(timeout = 300_000)
    fun testGetAllOnesHash() {
        assertArrayEquals(service.allOnesHash.bytes, ByteArray(32) { 0xFF.toByte() })
    }

    @Test(timeout = 300_000)
    fun testGetZeroHash() {
        assertArrayEquals(service.zeroHash.bytes, ByteArray(32))
    }

    @Test(timeout = 300_000)
    fun `Blake2s256 does not retain state between same-thread invocations`() {
        assertEquals(service.hash("abc"), service.hash("abc"))
    }
}