package net.corda.nodeapi.internal.cryptoservice.bouncycastle

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.cordaBouncyCastleProvider
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sha256
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.core.utilities.detailedLogger
import net.corda.core.utilities.trace
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import javax.security.auth.x500.X500Principal

/**
 * Basic implementation of a [CryptoService] that uses BouncyCastle for cryptographic operations
 * and a Java KeyStore in the form of [CertificateStore] to store private keys.
 * This service reuses the [NodeConfiguration.signingCertificateStore] to store keys.
 */
class BCCryptoService(private val legalName: X500Principal,
                      private val certificateStoreSupplier: CertificateStoreSupplier) : CryptoService() {

    private companion object {
        val detailedLogger = detailedLogger()
    }
    // TODO check if keyStore exists.
    // TODO make it private when E2ETestKeyManagementService does not require direct access to the private key.
    var certificateStore: CertificateStore = certificateStoreSupplier.get(true)

    override fun _generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
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

    override fun _containsKey(alias: String): Boolean {
        return certificateStore.contains(alias)
    }

    override fun _getPublicKey(alias: String): PublicKey {
        try {
            return certificateStore.query { getPublicKey(alias) }
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot get public key for alias $alias", e)
        }
    }

    override fun _sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
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

    override fun _getSigner(alias: String): ContentSigner {
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
}
