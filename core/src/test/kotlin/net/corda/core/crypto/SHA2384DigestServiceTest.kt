package net.corda.core.crypto

import org.junit.Assert
import org.junit.Test

class SHA2384DigestServiceTest {
    private val service: DigestService = DigestService.sha2_384

    @Test(timeout = 300_000)
    fun `test digest service hash and prefix`() {
        Assert.assertEquals(
                "SHA-384:5E3DBD33BEC467F625E28D4C5DF90CAACEA722F2DBB2AE9EF9C59EF4FB0FA31A070F5911156713F6AA0FCB09186B78FF",
                service.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a)).toString()
        )
    }

    @Test(timeout = 300_000)
    fun `test digest service hash blank and prefix`() {
        Assert.assertEquals(
                "SHA-384:38B060A751AC96384CD9327EB1B1E36A21FDB71114BE07434C0CC7BF63F6E1DA274EDEBFE76F65FBD51AD2F14898B95B",
                service.hash(byteArrayOf()).toString()
        )
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service hash length`() {
        Assert.assertEquals(48, service.digestLength)
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service hash name`() {
        Assert.assertEquals("SHA-384", service.hashAlgorithm)
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service zero hash and prefix`() {
        Assert.assertEquals("SHA-384:000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                service.zeroHash.toString())
    }

    @Test(timeout = 300_000)
    fun `test sha3-256 digest service ones hash and prefix`() {
        Assert.assertEquals("SHA-384:FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                service.allOnesHash.toString())
    }
}