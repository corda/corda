package net.corda.coretests.crypto.internal

import net.corda.coretesting.internal.DEV_ROOT_CA
import net.corda.testing.core.createCRL
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.Test

class ProviderMapTest {
    // https://github.com/corda/corda/pull/3997
    @Test(timeout = 300_000)
    fun `verify CRL algorithms`() {
        val crl = createCRL(
                issuer = DEV_ROOT_CA,
                revokedCerts = emptyList(),
                signatureAlgorithm = "SHA256withECDSA"
        )
        // This should pass.
        crl.verify(DEV_ROOT_CA.keyPair.public)

        // Try changing the algorithm to EC will fail.
        assertThatIllegalArgumentException().isThrownBy {
            createCRL(
                    issuer = DEV_ROOT_CA,
                    revokedCerts = emptyList(),
                    signatureAlgorithm = "EC"
            )
        }.withMessage("Unknown signature type requested: EC")
    }
}
