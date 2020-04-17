package net.corda.core.crypto

import net.corda.core.utilities.hexToByteArray
import org.bouncycastle.crypto.digests.Blake2sDigest
import org.bouncycastle.jcajce.provider.digest.BCMessageDigest
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals

class Blake2s256ServiceTest {
    private val service: DigestService = DefaultDigestServiceFactory.getService(Algorithm.BLAKE2s256())

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