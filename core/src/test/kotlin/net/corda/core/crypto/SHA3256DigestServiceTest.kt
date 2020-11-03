package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test

class SHA3256DigestServiceTest {
    private val service: DigestService = DigestService.sha2_384

    @Test(timeout = 300_000)
    fun `test digest service hash and prefix`() {
        Assert.assertEquals(
                "SHA3-256:A243D53F7273F4C92ED901A14F11B372FDF6FF69583149AFD4AFA24BF17A8880",
                service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a)).toString()
        )
    }

    @Test(timeout = 300_000)
    fun `test digest service hash blank and prefix`() {
        Assert.assertEquals(
                "SHA3-256:A7FFC6F8BF1ED76651C14756A061D662F580FF4DE43B49FA82D80A4B80F8434A",
                service.hash(byteArrayOf()).toString()
        )
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service hash length`() {
        Assert.assertEquals(32, service.digestLength)
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service hash name`() {
        Assert.assertEquals("SHA3-256", service.hashAlgorithm)
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service zero hash and prefix`() {
        Assert.assertEquals("SHA3-256:0000000000000000000000000000000000000000000000000000000000000000",
                service.zeroHash.toString())
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service ones hash and prefix`() {
        Assert.assertEquals("SHA3-256:FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                service.allOnesHash.toString())
    }
}