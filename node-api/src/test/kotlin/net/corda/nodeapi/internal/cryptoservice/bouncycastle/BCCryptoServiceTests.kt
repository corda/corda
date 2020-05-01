package net.corda.nodeapi.internal.cryptoservice.bouncycastle

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.cordaBouncyCastleProvider
import net.corda.core.internal.div
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.nodeapi.internal.cryptoservice.WrappedPrivateKey
import net.corda.nodeapi.internal.cryptoservice.WrappingMode
import net.corda.testing.core.ALICE_NAME
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.nio.file.Path
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.util.*
import javax.crypto.Cipher
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

    lateinit var certificatesDirectory: Path
    lateinit var wrappingKeyStorePath: Path

    @Before
    fun setUp() {
        val baseDirectory = temporaryFolder.root.toPath()
        certificatesDirectory = baseDirectory / "certificates"
        signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        wrappingKeyStorePath = certificatesDirectory / "wrappingkeystore.pkcs12"
    }

    @Test(timeout=300_000)
	fun `BCCryptoService generate key pair and sign both data and cert`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)
        // Testing every supported scheme.
        Crypto.supportedSignatureSchemes().filter { it != Crypto.COMPOSITE_KEY
                && it.signatureName != "SHA512WITHSPHINCS256"}.forEach { generateKeyAndSignForScheme(cryptoService, it) }
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

    @Test(timeout=300_000)
	fun `BCCryptoService generate key pair and sign with existing schemes`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)
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

    @Test(timeout=300_000)
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
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, createKeystore(alias, keyPair), wrappingKeyStorePath)
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

    @Test(timeout=300_000)
	fun `When key does not exist getPublicKey, sign and getSigner should throw`() {
        val nonExistingAlias = "nonExistingAlias"
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)
        assertFalse { cryptoService.containsKey(nonExistingAlias) }
        assertFailsWith<CryptoServiceException> { cryptoService.getPublicKey(nonExistingAlias) }
        assertFailsWith<CryptoServiceException> { cryptoService.sign(nonExistingAlias, clearData) }
        assertFailsWith<CryptoServiceException> { cryptoService.getSigner(nonExistingAlias) }
    }

    @Test(timeout=300_000)
	fun `cryptoService supports degraded mode of wrapping`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)
        val supportedMode = cryptoService.getWrappingMode()

        assertThat(supportedMode).isEqualTo(WrappingMode.DEGRADED_WRAPPED)
    }

    @Test(timeout=300_000)
	fun `cryptoService does not fail when requested to create same wrapping key twice with failIfExists is false`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)

        val keyAlias = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(keyAlias)
        cryptoService.createWrappingKey(keyAlias, failIfExists = false)
    }

    @Test(timeout=300_000)
	fun `cryptoService does fail when requested to create same wrapping key twice with failIfExists is true`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)

        val keyAlias = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(keyAlias)
        assertThat(cryptoService.containsKey(keyAlias)).isTrue()
        assertThatThrownBy { cryptoService.createWrappingKey(keyAlias) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("There is an existing key with the alias: $keyAlias")
    }

    @Test(timeout=300_000)
	fun `cryptoService fails when asked to generate wrapped key pair or sign, but the master key specified does not exist`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)

        val wrappingKeyAlias = UUID.randomUUID().toString()

        assertThatThrownBy {  cryptoService.generateWrappedKeyPair(wrappingKeyAlias) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("There is no master key under the alias: $wrappingKeyAlias")

        val dummyWrappedPrivateKey = WrappedPrivateKey("key".toByteArray(), Crypto.ECDSA_SECP256R1_SHA256)
        val data = "data".toByteArray()
        assertThatThrownBy {  cryptoService.sign(wrappingKeyAlias, dummyWrappedPrivateKey, data) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("There is no master key under the alias: $wrappingKeyAlias")
    }

    @Test(timeout=300_000)
	fun `cryptoService can generate wrapped key pair and sign with the private key successfully, using default algorithm`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)

        val wrappingKeyAlias = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(wrappingKeyAlias)
        generateWrappedKeyPairSignAndVerify(cryptoService, wrappingKeyAlias)
    }

    @Test(timeout=300_000)
	fun `cryptoService can generate wrapped key pair and sign with the private key successfully`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)

        val wrappingKeyAlias = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(wrappingKeyAlias)
        generateWrappedKeyPairSignAndVerify(cryptoService, wrappingKeyAlias, Crypto.ECDSA_SECP256R1_SHA256)
        generateWrappedKeyPairSignAndVerify(cryptoService, wrappingKeyAlias, Crypto.ECDSA_SECP256K1_SHA256)
        generateWrappedKeyPairSignAndVerify(cryptoService, wrappingKeyAlias, Crypto.RSA_SHA256)
    }

    private fun generateWrappedKeyPairSignAndVerify(cryptoService: CryptoService, wrappingKeyAlias: String, algorithm: SignatureScheme? = null) {
        val data = "data".toByteArray()

        val (publicKey, wrappedPrivateKey) = if (algorithm != null) {
            cryptoService.generateWrappedKeyPair(wrappingKeyAlias, algorithm)
        } else {
            cryptoService.generateWrappedKeyPair(wrappingKeyAlias)
        }

        val signature = cryptoService.sign(wrappingKeyAlias, wrappedPrivateKey, data)

        Crypto.doVerify(publicKey, signature, data)
    }

    @Test(timeout=300_000)
    fun `cryptoService can sign with previously encoded version of wrapped key`() {
        val cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore, wrappingKeyStorePath)

        val wrappingKeyAlias = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(wrappingKeyAlias)

        val wrappingKeyStore = loadOrCreateKeyStore(wrappingKeyStorePath, cryptoService.certificateStore.password, "PKCS12")
        val wrappingKey = wrappingKeyStore.getKey(wrappingKeyAlias, cryptoService.certificateStore.entryPassword.toCharArray())
        val cipher = Cipher.getInstance("AES", cordaBouncyCastleProvider)
        cipher.init(Cipher.WRAP_MODE, wrappingKey)

        val keyPairGenerator = KeyPairGenerator.getInstance("EC", cordaBouncyCastleProvider)
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKeyMaterialWrapped = cipher.wrap(keyPair.private)
        val wrappedPrivateKey = WrappedPrivateKey(privateKeyMaterialWrapped, Crypto.ECDSA_SECP256R1_SHA256, encodingVersion = null)

        val data = "data".toByteArray()
        val signature = cryptoService.sign(wrappingKeyAlias, wrappedPrivateKey, data)
        Crypto.doVerify(keyPair.public, signature, data)
    }
}
