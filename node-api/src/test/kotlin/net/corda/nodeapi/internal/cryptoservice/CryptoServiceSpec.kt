package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.Party
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
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

    private fun generateKeySignVerifyAndCleanup(scheme: SignatureScheme) {
        val cryptoService = getCryptoService()
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, scheme)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        assertTrue{ Crypto.doVerify(pubKey, signed, data) }

        delete(alias)
    }

}