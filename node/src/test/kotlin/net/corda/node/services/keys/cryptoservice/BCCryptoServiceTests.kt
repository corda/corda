package net.corda.node.services.keys.cryptoservice

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.internal.div
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BCCryptoServiceTests {

    companion object {
        val clearData = "data".toByteArray()
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()
    private lateinit var signingCertificateStore: CertificateStoreSupplier

    @Before
    fun setUp() {
        val baseDirectory = temporaryFolder.root.toPath()
        val certificatesDirectory = baseDirectory / "certificates"
        signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
    }

    @Test
    fun `BCCryptoService generate key pair and sign both data and cert`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore)
        // Testing every supported scheme.
        Crypto.supportedSignatureSchemes().filter { it != Crypto.COMPOSITE_KEY }.forEach { generateKeyAndSignForScheme(cryptoService, it) }
    }

    private fun generateKeyAndSignForScheme(cryptoService: BCCryptoService, signatureScheme: SignatureScheme) {
        val alias = "signature${signatureScheme.schemeNumberID}"
        val pubKey = cryptoService.generateKeyPair(alias, signatureScheme)
        assertTrue { cryptoService.containsKey(alias) }

        val signatureData = cryptoService.sign(alias, clearData)
        assertTrue(Crypto.doVerify(pubKey, signatureData, clearData))

        // Test that getSigner can indeed sign a certificate.
        val signer = cryptoService.getSigner(alias)
        val x500Principal = X500Principal("CN=Test")
        val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, 365.days)
        val certificate = X509Utilities.createCertificate(
                CertificateType.CONFIDENTIAL_LEGAL_IDENTITY,
                x500Principal,
                pubKey,
                signer,
                x500Principal,
                pubKey,
                window)

        certificate.checkValidity()
        certificate.verify(pubKey)
    }

    @Test
    fun `When key does not exist getPublicKey, sign and getSigner should throw`() {
        val nonExistingAlias = "nonExistingAlias"
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore)
        assertFalse { cryptoService.containsKey(nonExistingAlias) }
        assertFailsWith<CryptoServiceException> { cryptoService.getPublicKey(nonExistingAlias) }
        assertFailsWith<CryptoServiceException> { cryptoService.sign(nonExistingAlias, clearData) }
        assertFailsWith<CryptoServiceException> { cryptoService.getSigner(nonExistingAlias) }
    }
}
