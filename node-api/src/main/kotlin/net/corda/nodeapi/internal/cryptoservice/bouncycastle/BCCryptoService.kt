package net.corda.nodeapi.internal.cryptoservice.bouncycastle

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.Instances.getSignatureInstance
import net.corda.core.crypto.internal.cordaBouncyCastleProvider
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sha256
import net.corda.core.utilities.detailedLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import net.corda.nodeapi.internal.crypto.save
import net.corda.nodeapi.internal.cryptoservice.*
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import org.bouncycastle.operator.ContentSigner
import java.nio.file.Path
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.security.auth.x500.X500Principal

/**
 * Basic implementation of a [CryptoService] that uses BouncyCastle for cryptographic operations
 * and a Java KeyStore in the form of [CertificateStore] to store private keys.
 * This service reuses the [NodeConfiguration.signingCertificateStore] to store keys.
 *
 * The [wrappingKeyStorePath] must be provided in order to execute any wrapping operations (e.g. [createWrappingKey], [generateWrappedKeyPair])
 */
class BCCryptoService(private val legalName: X500Principal,
                      private val certificateStoreSupplier: CertificateStoreSupplier,
                      private val wrappingKeyStorePath: Path? = null) : CryptoService {

    private companion object {
        val detailedLogger = detailedLogger()
    }

    // TODO check if keyStore exists.
    // TODO make it private when E2ETestKeyManagementService does not require direct access to the private key.
    var certificateStore: CertificateStore = certificateStoreSupplier.get(true)

    /**
     * JKS keystore does not support storage for secret keys, so the existing keystore cannot be re-used.
     * JCEKS keystore supports storage of symmetric keys according to the spec, but there are several issues around classloaders and deserialization filtering (see links below).
     *      - https://stackoverflow.com/questions/49990904/what-is-the-cause-of-java-security-unrecoverablekeyexception-rejected-by-the-j
     *      - https://stackoverflow.com/questions/50393533/java-io-ioexception-invalid-secret-key-format-when-opening-jceks-key-store-wi
     * Thus, PKCS12 is used for storing the wrapping key.
     */
    private val wrappingKeyStore: KeyStore by lazy {
        loadOrCreateKeyStore(wrappingKeyStorePath!!, certificateStore.password, "PKCS12")
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        try {
            detailedLogger.trace { "CryptoService(action=generate_key_pair_start;alias=$alias;scheme=$scheme)" }
            val keyPair = Crypto.generateKeyPair(scheme)
            detailedLogger.trace { "CryptoService(action=generate_key_pair_end;alias=$alias;scheme=$scheme)" }
            importKey(alias, keyPair)
            return keyPair.public
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot generate key for alias $alias and signature scheme ${scheme.schemeCodeName} (id ${scheme.schemeNumberID})", e)
        }
    }

    override fun containsKey(alias: String): Boolean {
        return if (wrappingKeyStorePath == null) {
            certificateStore.contains(alias)
        } else {
            certificateStore.contains(alias) || wrappingKeyStore.containsAlias(alias)
        }

    }

    override fun getPublicKey(alias: String): PublicKey {
        try {
            return certificateStore.query { getPublicKey(alias) }
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot get public key for alias $alias", e, isRecoverable = false)
        }
    }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        try {
            return when(signAlgorithm) {
                null -> Crypto.doSign(certificateStore.query { getPrivateKey(alias, certificateStore.entryPassword) }, data)
                else -> signWithAlgorithm(alias, data, signAlgorithm)
            }
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot sign using the key with alias $alias. SHA256 of data to be signed: ${data.sha256()}", e)
        }
    }

    private fun signWithAlgorithm(alias: String, data: ByteArray, signAlgorithm: String): ByteArray {
        val privateKey = certificateStore.query { getPrivateKey(alias, certificateStore.entryPassword) }
        val signature = Signature.getInstance(signAlgorithm, cordaBouncyCastleProvider)
        detailedLogger.trace { "CryptoService(action=signing_start;alias=$alias;algorithm=$signAlgorithm)" }
        signature.initSign(privateKey, newSecureRandom())
        signature.update(data)
        detailedLogger.trace { "CryptoService(action=signing_end;alias=$alias;algorithm=$signAlgorithm)" }
        return signature.sign()
    }

    override fun getSigner(alias: String): ContentSigner {
        try {
            detailedLogger.trace { "CryptoService(action=get_signer;alias=$alias)" }
            val privateKey = certificateStore.query { getPrivateKey(alias, certificateStore.entryPassword) }
            val signatureScheme = Crypto.findSignatureScheme(privateKey)
            return ContentSignerBuilder.build(signatureScheme, privateKey, Crypto.findProvider(signatureScheme.providerName), newSecureRandom())
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot get Signer for key with alias $alias", e)
        }
    }

    override fun defaultIdentitySignatureScheme(): SignatureScheme {
        return X509Utilities.DEFAULT_IDENTITY_SIGNATURE_SCHEME
    }

    override fun defaultTLSSignatureScheme(): SignatureScheme {
        return X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
    }

    /**
     * If a node is running in [NodeConfiguration.devMode] and for backwards compatibility purposes, the same [KeyStore]
     * is reused outside [BCCryptoService] to update certificate paths. [resyncKeystore] will sync [BCCryptoService]'s
     * loaded [certificateStore] in memory with the contents of the corresponding [KeyStore] file.
     */
    fun resyncKeystore() {
        certificateStore = certificateStoreSupplier.get(true)
    }

    /** Import an already existing [KeyPair] to this [CryptoService]. */
    fun importKey(alias: String, keyPair: KeyPair) {
        try {
            // Store a self-signed certificate, as Keystore requires to store certificates instead of public keys.
            // We could probably add a null cert, but we store a self-signed cert that will be used to retrieve the public key.
            detailedLogger.trace { "CryptoService(action=key_import;alias=$alias)" }
            val cert = X509Utilities.createSelfSignedCACertificate(legalName, keyPair)
            certificateStore.query { setPrivateKey(alias, keyPair.private, listOf(cert), certificateStore.entryPassword) }
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot import key with alias $alias", e)
        }
    }

    @Synchronized
    override fun createWrappingKey(alias: String, failIfExists: Boolean) {
        if (wrappingKeyStore.containsAlias(alias)) {
            when (failIfExists) {
                true -> throw IllegalArgumentException("There is an existing key with the alias: $alias")
                false -> return
            }
        }

        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(wrappingKeySize())
        val wrappingKey = keyGenerator.generateKey()
        wrappingKeyStore.setEntry(alias, KeyStore.SecretKeyEntry(wrappingKey), KeyStore.PasswordProtection(certificateStore.entryPassword.toCharArray()))
        wrappingKeyStore.save(wrappingKeyStorePath!!, certificateStore.password)
    }

    /**
     * Using "AESWRAPPAD" cipher spec for key wrapping defined by [RFC 5649](https://tools.ietf.org/html/rfc5649).
     * "AESWRAPPAD" (same as "AESKWP" or "AESRFC5649WRAP") is implemented in [org.bouncycastle.jcajce.provider.symmetric.AES.WrapPad] using
     * [org.bouncycastle.crypto.engines.RFC5649WrapEngine]. See:
     * - https://www.bouncycastle.org/docs/docs1.5on/org/bouncycastle/crypto/engines/AESWrapPadEngine.html
     * - https://www.bouncycastle.org/docs/docs1.5on/org/bouncycastle/crypto/engines/RFC5649WrapEngine.html
     *
     * Keys encoded with "AESWRAPPAD" are stored with encodingVersion = 1. Previously used cipher spec ("AES" == "AES/ECB/PKCS5Padding")
     * corresponds to encodingVersion = null.
     */
    override fun generateWrappedKeyPair(masterKeyAlias: String, childKeyScheme: SignatureScheme): Pair<PublicKey, WrappedPrivateKey> {
        if (!wrappingKeyStore.containsAlias(masterKeyAlias)) {
            throw IllegalStateException("There is no master key under the alias: $masterKeyAlias")
        }

        val wrappingKey = wrappingKeyStore.getKey(masterKeyAlias, certificateStore.entryPassword.toCharArray())
        val cipher = Cipher.getInstance("AESWRAPPAD", cordaBouncyCastleProvider)
        cipher.init(Cipher.WRAP_MODE, wrappingKey)

        val keyPairGenerator = keyPairGeneratorFromScheme(childKeyScheme)
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKeyMaterialWrapped = cipher.wrap(keyPair.private)

        return Pair(keyPair.public, WrappedPrivateKey(privateKeyMaterialWrapped, childKeyScheme, encodingVersion = 1))
    }

    override fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): ByteArray {
        if (!wrappingKeyStore.containsAlias(masterKeyAlias)) {
            throw IllegalStateException("There is no master key under the alias: $masterKeyAlias")
        }

        val wrappingKey = wrappingKeyStore.getKey(masterKeyAlias, certificateStore.entryPassword.toCharArray())
        // Keeping backwards compatibility with previous encoding algorithms
        val algorithm = when(wrappedPrivateKey.encodingVersion) {
            1 -> "AESWRAPPAD"
            else -> "AES"
        }
        val cipher = Cipher.getInstance(algorithm, cordaBouncyCastleProvider)
        cipher.init(Cipher.UNWRAP_MODE, wrappingKey)

        val privateKey = cipher.unwrap(wrappedPrivateKey.keyMaterial, keyAlgorithmFromScheme(wrappedPrivateKey.signatureScheme), Cipher.PRIVATE_KEY) as PrivateKey

        val signature = getSignatureInstance(wrappedPrivateKey.signatureScheme.signatureName, cordaBouncyCastleProvider)
        signature.initSign(privateKey, newSecureRandom())
        signature.update(payloadToSign)
        return signature.sign()
    }

    override fun getWrappingMode(): WrappingMode? = WrappingMode.DEGRADED_WRAPPED

    private fun keyPairGeneratorFromScheme(scheme: SignatureScheme): KeyPairGenerator {
        val algorithm = keyAlgorithmFromScheme(scheme)
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm, cordaBouncyCastleProvider)
        when (scheme) {
            Crypto.ECDSA_SECP256R1_SHA256 -> keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
            Crypto.ECDSA_SECP256K1_SHA256 -> keyPairGenerator.initialize(ECGenParameterSpec("secp256k1"))
            Crypto.RSA_SHA256 -> keyPairGenerator.initialize(scheme.keySize!!)
            else -> throw IllegalArgumentException("No mapping for scheme ID ${scheme.schemeNumberID}")
        }
        return keyPairGenerator
    }

    private fun keyAlgorithmFromScheme(scheme: SignatureScheme): String = when (scheme) {
        Crypto.ECDSA_SECP256R1_SHA256, Crypto.ECDSA_SECP256K1_SHA256 -> "EC"
        Crypto.RSA_SHA256 -> "RSA"
        else -> throw IllegalArgumentException("No algorithm for scheme ID ${scheme.schemeNumberID}")
    }

}
