package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.Party
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Duration
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * This is a base class, containing the tests that consist the specification for [CryptoService]
 *
 * Any implementations of it can extend this class to verify the implementation conforms to the spec.
 */
abstract class CryptoServiceSpec {

    /**
     * Method used to retrieve the [CryptoService] that will be tested.
     */
    abstract fun getCryptoService(): CryptoService

    /**
     * Method used to delete any keys created during the tests.
     */
    abstract fun delete(alias: String)

    /**
     * Method used to get the schemes that are supported and should be tested.
     */
    abstract fun getSupportedSchemes(): List<SignatureScheme>

    /**
     * Method used to get the schemes that are supported and should be tested for the wrapping APIs.
     */
    abstract fun getSupportedSchemesForWrappingOperations(): List<SignatureScheme>

    /**
     * Method used to get the supported wrapping mode.
     */
    abstract fun getSupportedWrappingMode(): WrappingMode?

    @Test
    fun `When key does not exist, signing should throw`() {
        val cryptoService = getCryptoService()
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        assertFailsWith<CryptoServiceException> { cryptoService.sign(alias, data) }
    }

    @Test
    fun `When key does not exist, getPublicKey should return null`() {
        val cryptoService = getCryptoService()
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        assertNull(cryptoService.getPublicKey(alias))
    }

    @Test
    fun `When key does not exist, getContentSigner should throw`() {
        val cryptoService = getCryptoService()
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        assertFailsWith<CryptoServiceException> { cryptoService.getSigner(alias) }
    }

    @Test
    fun `Content signer works with X509Utilities`() {
        val cryptoService = getCryptoService()
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        val signer = cryptoService.getSigner(alias)
        val otherAlias = UUID.randomUUID().toString()
        val otherPubKey = cryptoService.generateKeyPair(otherAlias, cryptoService.defaultIdentitySignatureScheme())
        val issuer = Party(DUMMY_BANK_A_NAME, pubKey)
        val partyAndCert = getTestPartyAndCertificate(issuer)
        val issuerCert = partyAndCert.certificate
        val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, 3650.days, issuerCert)
        val ourCertificate = X509Utilities.createCertificate(
                CertificateType.CONFIDENTIAL_LEGAL_IDENTITY,
                issuerCert.subjectX500Principal,
                issuerCert.publicKey,
                signer,
                partyAndCert.name.x500Principal,
                otherPubKey,
                window)
        ourCertificate.checkValidity()
        delete(alias)
    }

    @Test
    fun `Generate key with the default legal identity scheme, then sign and verify data`() {
        val scheme = getCryptoService().defaultIdentitySignatureScheme()

        generateKeySignVerifyAndCleanup(scheme)
    }

    @Test
    fun `Generate ECDSA key with r1 curve, then sign and verify data`() {
        val scheme = Crypto.ECDSA_SECP256R1_SHA256

        assumeTrue(scheme in getSupportedSchemes())
        generateKeySignVerifyAndCleanup(scheme)
    }

    @Test
    fun `Generate ECDSA key with k1 curve, then sign and verify data`() {
        val scheme = Crypto.ECDSA_SECP256K1_SHA256

        assumeTrue(scheme in getSupportedSchemes())
        generateKeySignVerifyAndCleanup(scheme)
    }

    @Test
    fun `Generate RSA key, then sign and verify data`() {
        val scheme = Crypto.RSA_SHA256

        assumeTrue(scheme in getSupportedSchemes())
        generateKeySignVerifyAndCleanup(scheme)
    }

    @Test
    fun `cryptoService supports the specified mode of wrapping`() {
        val cryptoService = getCryptoService()
        val supportedMode = cryptoService.getWrappingMode()

        assertThat(supportedMode).isEqualTo(getSupportedWrappingMode())
    }

    @Test
    fun `cryptoService does not fail when requested to create same wrapping key twice with failIfExists is false`() {
        val cryptoService = getCryptoService()
        assumeTrue(cryptoService.getWrappingMode() != null)

        val keyAlias = UUID.randomUUID().toString()
        withCleanup(keyAlias, cryptoService) {
            cryptoService.createWrappingKey(keyAlias)
            cryptoService.createWrappingKey(keyAlias, failIfExists = false)
        }
    }

    @Test
    fun `cryptoService does fail when requested to create same wrapping key twice with failIfExists is true`() {
        val cryptoService = getCryptoService()
        assumeTrue(cryptoService.getWrappingMode() != null)

        val keyAlias = UUID.randomUUID().toString()

        withCleanup(keyAlias, cryptoService) {
            cryptoService.createWrappingKey(keyAlias)
            assertThatThrownBy { cryptoService.createWrappingKey(keyAlias) }
                    .isInstanceOf(IllegalArgumentException::class.java)
                    .hasMessage("There is an existing key with the alias: $keyAlias")
        }
    }

    @Test
    fun `cryptoService fails when asked to generate wrapped key pair or sign, but the master key specified does not exist`() {
        val cryptoService = getCryptoService()
        assumeTrue(cryptoService.getWrappingMode() != null)

        val wrappingKeyAlias = UUID.randomUUID().toString()

        assertThatThrownBy { cryptoService.generateWrappedKeyPair(wrappingKeyAlias) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("There is no master key under the alias: $wrappingKeyAlias")

        val dummyWrappedPrivateKey = WrappedPrivateKey("key".toByteArray(), Crypto.ECDSA_SECP256R1_SHA256)
        val data = "data".toByteArray()
        assertThatThrownBy { cryptoService.sign(wrappingKeyAlias, dummyWrappedPrivateKey, data) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("There is no master key under the alias: $wrappingKeyAlias")
    }

    @Test
    fun `cryptoService can generate wrapped key pair and sign with the private key successfully, using default algorithm`() {
        val cryptoService = getCryptoService()
        assumeTrue(cryptoService.getWrappingMode() != null)

        generateWrappedKeyPairSignAndVerify(cryptoService)
    }

    @Test
    fun `cryptoService can generate ECDSA wrapped key pair with r1 curve and sign with the private key successfully`() {
        val scheme = Crypto.ECDSA_SECP256R1_SHA256

        assumeTrue(scheme in getSupportedSchemesForWrappingOperations())
        val cryptoService = getCryptoService()

        generateWrappedKeyPairSignAndVerify(cryptoService, scheme)
    }

    @Test
    fun `cryptoService can generate ECDSA wrapped key pair with k1 curve and sign with the private key successfully`() {
        val scheme = Crypto.ECDSA_SECP256K1_SHA256

        assumeTrue(scheme in getSupportedSchemesForWrappingOperations())
        val cryptoService = getCryptoService()

        generateWrappedKeyPairSignAndVerify(cryptoService, scheme)
    }

    @Test
    fun `cryptoService can generate RSA wrapped key pair and sign with the private key successfully`() {
        val scheme = Crypto.RSA_SHA256

        assumeTrue(scheme in getSupportedSchemesForWrappingOperations())
        val cryptoService = getCryptoService()

        generateWrappedKeyPairSignAndVerify(cryptoService, scheme)
    }

    private fun generateKeySignVerifyAndCleanup(scheme: SignatureScheme) {
        val cryptoService = getCryptoService()
        val alias = UUID.randomUUID().toString()

        withCleanup(alias, cryptoService) {
            val pubKey = cryptoService.generateKeyPair(alias, scheme)
            assertTrue { cryptoService.containsKey(alias) }
            val data = UUID.randomUUID().toString().toByteArray()
            val signed = cryptoService.sign(alias, data)
            assertTrue{ Crypto.doVerify(pubKey, signed, data) }
        }
    }

    private fun generateWrappedKeyPairSignAndVerify(cryptoService: CryptoService, algorithm: SignatureScheme? = null) {
        val wrappingKeyAlias = "wrapping-key"

        withCleanup(wrappingKeyAlias, cryptoService) {
            cryptoService.createWrappingKey(wrappingKeyAlias)

            val data = "data".toByteArray()

            val (publicKey, wrappedPrivateKey) = if (algorithm != null) {
                cryptoService.generateWrappedKeyPair(wrappingKeyAlias, algorithm)
            } else {
                cryptoService.generateWrappedKeyPair(wrappingKeyAlias)
            }

            val signature = cryptoService.sign(wrappingKeyAlias, wrappedPrivateKey, data)

            assertTrue{ Crypto.doVerify(publicKey, signature, data) }
        }
    }

    private fun withCleanup(alias: String, cryptoService: CryptoService, test: () -> Unit) {
        try {
            test()
        } finally {
            if (cryptoService.containsKey(alias))
                delete(alias)
        }
    }

}