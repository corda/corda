package net.corda.node.services.keys.cryptoservice.gemalto

import com.safenetinc.luna.provider.LunaProvider
import net.corda.core.crypto.Crypto
import net.corda.core.identity.Party
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import org.junit.Ignore
import org.junit.Test
import java.security.KeyStore
import java.time.Duration
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Gemalto does not provide a simulator, which means that this test has to be run against
 * one of their cloud HSMs or the box they loaned to us. The latter is not accessible
 * from TeamCity, so the only option would be the cloud HSM. I currently don't know
 * the credentials to that, so it's not an option. Also, running automated tests against
 * their cloud HSMs might turn out to be a bit flaky. For this reason, this test is not
 * enabled. To run it locally, you need to set up the Gemalto JCA provider on your machine.
 * If you don't want to or can't install the client and JCA provider system-wide, you can
 * provide the location of your Chrystoki.conf via an environment variable:
 *     `ChrystokiConfigurationPath=/path/to/your/config`,
 * and the location of the libChrystoki2 via the java library path
 *    `-Djava.library.path=/your/jsp/lib`.
 *
 */
@Ignore
class GemaltoLunaCryptoServiceTest {

    private val provider = LunaProvider.getInstance()
    private val keyStore = KeyStore.getInstance(GemaltoLunaCryptoService.KEYSTORE_TYPE, provider)

    private val config = GemaltoLunaCryptoService.GemaltoLunaConfiguration("tokenlabel:somepartition", "somepassword")
    

    @Test
    fun `Generate ECDSA key with r1 curve, then sign and verify data`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `Generate key with the default legal identity scheme, then sign and verify data`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `Generate ECDSA key with k1 curve, then sign and verify data`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `Generate RSA key, then sign and verify data`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `When key does not exist, signing should throw`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        assertFailsWith<CryptoServiceException> { cryptoService.sign(alias, data) }
    }

    @Test
    fun `When key does not exist, getPublicKey should return null`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        assertNull(cryptoService.getPublicKey(alias))
    }

    @Test
    fun `When key does not exist, getContentSigner should throw`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        assertFailsWith<CryptoServiceException> { cryptoService.getSigner(alias) }
    }

    @Test
    fun `Content signer works with X509Utilities`() {
        val cryptoService = GemaltoLunaCryptoService(keyStore, provider) { config }
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
    }
}
