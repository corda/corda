package net.corda.node.internal.cryptoservice

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.newSecureRandom
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.KeyStore
import java.security.PublicKey

/**
 * Basic implementation of a [CryptoService] that uses BouncyCastle for cryptographic operations
 * and a Java KeyStore in the form of [CertificateStore] to store private keys.
 * This service reuses the [NodeConfiguration.signingCertificateStore] to store keys.
 */
class BCCryptoService(private val nodeConf: NodeConfiguration) : CryptoService {

    // TODO check if keyStore exists.
    // TODO make it private when E2ETestKeyManagementService does not require direct access to the private key.
    internal var certificateStore: CertificateStore = nodeConf.signingCertificateStore.get(true)

    override fun generateKeyPair(alias: String, schemeNumberID: Int): PublicKey {
        val keyPair = Crypto.generateKeyPair(Crypto.findSignatureScheme(schemeNumberID))
        importKey(alias, keyPair)
        return keyPair.public
    }

    override fun containsKey(alias: String): Boolean {
        return certificateStore.contains(alias)
    }

    override fun getPublicKey(alias: String): PublicKey {
        return certificateStore.query { getPublicKey(alias) }
    }

    override fun sign(alias: String, data: ByteArray): ByteArray {
        return Crypto.doSign(certificateStore.query { getPrivateKey(alias, certificateStore.entryPassword) } , data)
    }

    override fun getSigner(alias: String): ContentSigner {
        val privateKey = certificateStore.query { getPrivateKey(alias, certificateStore.entryPassword) }
        val signatureScheme = Crypto.findSignatureScheme(privateKey)
        return ContentSignerBuilder.build(signatureScheme, privateKey, Crypto.findProvider(signatureScheme.providerName), newSecureRandom())
    }

    /**
     * If a node is running in [NodeConfiguration.devMode] and for backwards compatibility purposes, the same [KeyStore]
     * is reused outside [BCCryptoService] to update certificate paths. [resyncKeystore] will sync [BCCryptoService]'s
     * loaded [certificateStore] in memory with the contents of the corresponding [KeyStore] file.
     */
    fun resyncKeystore() {
        certificateStore = nodeConf.signingCertificateStore.get(true)
    }

    /** Import an already existing [KeyPair] to this [CryptoService]. */
    fun importKey(alias: String, keyPair: KeyPair) {
        // Store a self-signed certificate, as Keystore requires to store certificates instead of public keys.
        // We could probably add a null cert, but we store a self-signed cert that will be used to retrieve the public key.
        val cert = X509Utilities.createSelfSignedCACertificate(nodeConf.myLegalName.x500Principal, keyPair)
        certificateStore.query { setPrivateKey(alias, keyPair.private, listOf(cert), certificateStore.entryPassword) }
    }
}
