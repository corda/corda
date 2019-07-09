package net.corda.coretests.crypto

import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.isCRLDistributionPointBlacklisted
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.internal.DEV_INTERMEDIATE_CA
import net.corda.testing.internal.DEV_ROOT_CA
import org.junit.Test
import kotlin.test.assertTrue

class CryptoUtilsTest {
    @Test
    fun `crl distribution point is blacklisted`() {
        val intermediateCert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, DEV_ROOT_CA.certificate, DEV_ROOT_CA.keyPair, DEV_INTERMEDIATE_CA.certificate.subjectX500Principal, DEV_INTERMEDIATE_CA.keyPair.public, crlDistPoint = "http://r3-test.com/certificate-revocation-list/root")
        val nodeCA = X509Utilities.createCertificate(CertificateType.NODE_CA, intermediateCert, DEV_INTERMEDIATE_CA.keyPair, CordaX500Name("Test", "London", "GB").x500Principal, generateKeyPair().public)
        assertTrue {
            isCRLDistributionPointBlacklisted(listOf(nodeCA, intermediateCert, DEV_ROOT_CA.certificate))
        }
    }
}

