package net.corda.nodeapi.internal.cryptoservice.bouncycastle

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.cordaBouncyCastleProvider
import net.corda.core.internal.div
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.stubs.CertificateStoreStubs
import net.i2p.crypto.eddsa.EdDSAEngine
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.security.*
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

    @Rule
    @JvmField
    val temporaryKeystoreFolder = TemporaryFolder()

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
    fun `BCCryptoService generate key pair and sign with existing schemes`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore)
        // Testing every supported scheme.
        Crypto.supportedSignatureSchemes().filter { it != Crypto.COMPOSITE_KEY
                && it.signatureName != "SHA512WITHSPHINCS256"}.forEach {
            val alias = "signature${it.schemeNumberID}"
            val pubKey = cryptoService.generateKeyPair(alias, it)
            assertTrue { cryptoService.containsKey(alias) }
            val signatureData = cryptoService.sign(alias, clearData, it.signatureName)
            assertTrue(Crypto.doVerify(pubKey, signatureData, clearData))
        }
    }

    @Test
    fun `BCCryptoService generate key pair and sign with passed signing algorithm`() {

        assertTrue{signAndVerify(signAlgo = "NONEwithRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "MD2withRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "MD5withRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "SHA1withRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "SHA224withRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "SHA256withRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "SHA384withRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "SHA512withRSA", alias = "myKeyAlias", keyTypeAlgo = "RSA")}
        assertTrue{signAndVerify(signAlgo = "NONEwithECDSA", alias = "myKeyAlias", keyTypeAlgo = "EC")}
        assertTrue{signAndVerify(signAlgo = "SHA1withECDSA", alias = "myKeyAlias", keyTypeAlgo = "EC")}
        assertTrue{signAndVerify(signAlgo = "SHA224withECDSA", alias = "myKeyAlias", keyTypeAlgo = "EC")}
        assertTrue{signAndVerify(signAlgo = "SHA256withECDSA", alias = "myKeyAlias", keyTypeAlgo = "EC")}
        assertTrue{signAndVerify(signAlgo = "SHA384withECDSA", alias = "myKeyAlias", keyTypeAlgo = "EC")}
        assertTrue{signAndVerify(signAlgo = "SHA512withECDSA", alias = "myKeyAlias", keyTypeAlgo = "EC")}
    }

    private fun signAndVerify(signAlgo: String, alias: String, keyTypeAlgo: String): Boolean {
        val keyPairGenerator = KeyPairGenerator.getInstance(keyTypeAlgo)
        val keyPair = keyPairGenerator.genKeyPair()
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, createKeystore(alias, keyPair))
        assertTrue { cryptoService.containsKey(alias) }
        val signatureData = cryptoService.sign(alias, clearData, signAlgo)
        return verify(signAlgo, cryptoService.getPublicKey(alias), signatureData, clearData)
    }

    private fun verify(signAlgo: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        val sig = Signature.getInstance(signAlgo, BouncyCastleProvider())
        sig.initVerify(publicKey)
        sig.update(clearData)
        return sig.verify(signatureData)
    }

    private fun createKeystore(alias: String, keyPair: KeyPair) : CertificateStoreSupplier {
        val myPassword = "password"
        val keyStoreFilename = "keys-with-more-algos.jks"
        val keyStore = KeyStore.getInstance("pkcs12")
        keyStore.load(null, null)
        val baseDirectory = temporaryKeystoreFolder.root.toPath()
        val certificatesDirectory = baseDirectory / keyStoreFilename

        val x500Principal = X500Principal("CN=Test")
        val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, 365.days)
        val certificate = X509Utilities.createCertificate(
                CertificateType.TLS,
                x500Principal,
                keyPair,
                x500Principal,
                keyPair.public,
                window)

        keyStore.setKeyEntry(alias, keyPair.private, "password".toCharArray(), arrayOf(certificate))
        FileOutputStream(certificatesDirectory.toString()).use { keyStore.store(it, myPassword.toCharArray())}
        return CertificateStoreStubs.Signing.withCertificatesDirectory(
                certificatesDirectory = baseDirectory,
                password = myPassword,
                keyPassword = myPassword,
                certificateStoreFileName = keyStoreFilename)
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
