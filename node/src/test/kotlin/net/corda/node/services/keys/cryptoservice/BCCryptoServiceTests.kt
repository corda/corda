package net.corda.node.services.keys.cryptoservice

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.internal.div
import net.corda.core.utilities.days
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.rigorousMock
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
        const val alias = "keyAlias"
        val clearData = "data".toByteArray()
        val signatureScheme = Crypto.ECDSA_SECP256K1_SHA256
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var config: NodeConfiguration

    @Before
    fun setUp() {
        abstract class AbstractNodeConfiguration : NodeConfiguration

        val baseDirectory = temporaryFolder.root.toPath()
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)

        config = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(ALICE_NAME).whenever(it).myLegalName
        }
    }

    @Test
    fun `BCCryptoService generate key pair and sign both data and cert`() {
        val cryptoService = BCCryptoService(config)
        Crypto.supportedSignatureSchemes().filter { it != Crypto.COMPOSITE_KEY}.forEach { generateKeyAndSignForScheme(cryptoService, it) }
    }

    private fun generateKeyAndSignForScheme(cryptoService: BCCryptoService, signatureScheme: SignatureScheme) {
        val schemeNumberID = signatureScheme.schemeNumberID
        val alias = "signature$schemeNumberID"
        val pubKey = cryptoService.generateKeyPair(alias, schemeNumberID)
        assertTrue { cryptoService.containsKey(alias) }

        val signatureData = cryptoService.sign(alias, clearData)
        assertTrue(Crypto.doVerify(pubKey, signatureData, clearData))

        // Test that getSigner can indeed sign a certificate.
        val signer = cryptoService.getSigner(alias)
        val x500Principal = X500Principal("CN=Test")
        val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, 3650.days)
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
        val cryptoService = BCCryptoService(config)
        assertFalse { cryptoService.containsKey(nonExistingAlias) }
        assertFailsWith<CryptoServiceException> { cryptoService.getPublicKey(nonExistingAlias) }
        assertFailsWith<CryptoServiceException> { cryptoService.sign(nonExistingAlias, clearData) }
        assertFailsWith<CryptoServiceException> { cryptoService.getSigner(nonExistingAlias) }
    }
}
