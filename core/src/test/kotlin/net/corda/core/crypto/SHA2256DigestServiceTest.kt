package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test

class SHA2256DigestServiceTest {
    private val service: DigestService = DigestService.sha2_256

    @Test(timeout = 300_000)
    fun `test digest service hash and no prefix`() {
        Assert.assertEquals(
                "6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F",
                service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a)).toString()
        )
    }

    @Test(timeout = 300_000)
    fun `test digest service hash blank and no prefix`() {
        Assert.assertEquals(
                "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
                service.hash(byteArrayOf()).toString()
        )
    }

    @Test(timeout = 300_000)
    fun `test sha2-256 digest service hash length`() {
        Assert.assertEquals(32, service.digestLength)
    }

    @Test(timeout = 300_000)
    fun `test sha2-256 digest service hash name`() {
        Assert.assertEquals("SHA-256", service.hashAlgorithm)
    }

    @Test(timeout = 300_000)
    fun `test sha2-256 digest service zero hash and no prefix`() {
        Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
                service.zeroHash.toString())
    }

    @Test(timeout = 300_000)
    fun `test sha2-256 digest service ones hash and no prefix`() {
        Assert.assertEquals("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                service.allOnesHash.toString())
    }
}