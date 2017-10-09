package net.corda.signing.generator

import CryptoServerCXI.CryptoServerCXI
import CryptoServerJCE.CryptoServerProvider
import net.corda.node.utilities.addOrReplaceKey
import net.corda.signing.authentication.Authenticator
import net.corda.signing.utils.X509Utilities.createIntermediateCert
import net.corda.signing.utils.X509Utilities.createSelfSignedCACert
import net.corda.signing.utils.X509Utilities.getAndInitializeKeyStore
import net.corda.signing.utils.X509Utilities.getCleanEcdsaKeyPair
import net.corda.signing.utils.X509Utilities.retrieveCertificateAndKeys
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey

/**
 * Encapsulates logic for root and intermediate key/certificate generation.
 */
class KeyCertificateGenerator(private val authenticator: Authenticator,
                              private val keySpecifier: Int,
                              private val keyGroup: String) {


    /**
     * Generates root and intermediate key and certificates and stores them in the key store given by provider.
     * If the keys and certificates already exists they will be overwritten.
     * @param keyStorePassword password to the key store
     * @param certificateKeyName name of the intermediate key/certificate
     * @param privateKeyPassword password for the intermediate private key
     * @param parentCertificateName name of the parent key/certificate
     * @param parentPrivateKeyPassword password for the parent private key
     * @param validDays days of certificate validity
     */
    fun generateAllCertificates(keyStorePassword: String?,
                                certificateKeyName: String,
                                privateKeyPassword: String,
                                parentCertificateName: String,
                                parentPrivateKeyPassword: String,
                                validDays: Int) {
        authenticator.connectAndAuthenticate { provider, signers ->
            val keyStore = getAndInitializeKeyStore(provider, keyStorePassword)
            generateRootCertificate(provider, keyStore, parentCertificateName, parentPrivateKeyPassword, validDays)
            generateIntermediateCertificate(provider, keyStore, certificateKeyName, privateKeyPassword, parentCertificateName, parentPrivateKeyPassword, validDays)
        }
    }

    /**
     * Generates a root certificate
     */
    private fun generateRootCertificate(provider: CryptoServerProvider,
                                        keyStore: KeyStore,
                                        certificateKeyName: String,
                                        privateKeyPassword: String,
                                        validDays: Int) {
        val keyPair = generateEcdsaKeyPair(provider, keyStore, certificateKeyName, privateKeyPassword)
        val selfSignedRootCertificate = createSelfSignedCACert("R3", keyPair, validDays, provider).certificate
        keyStore.addOrReplaceKey(certificateKeyName, keyPair.private, privateKeyPassword.toCharArray(), arrayOf(selfSignedRootCertificate))
        println("New certificate and key pair named $certificateKeyName have been generated")
    }

    /**
     * Generates an intermediate certificate
     */
    private fun generateIntermediateCertificate(provider: CryptoServerProvider,
                                                keyStore: KeyStore,
                                                certificateKeyName: String,
                                                privateKeyPassword: String,
                                                parentCertificateName: String,
                                                parentPrivateKeyPassword: String,
                                                validDays: Int) {
        val parentCACertKey = retrieveCertificateAndKeys(parentCertificateName, parentPrivateKeyPassword, keyStore)
        val keyPair = generateEcdsaKeyPair(provider, keyStore, certificateKeyName, privateKeyPassword)
        val intermediateCertificate = createIntermediateCert("R3 Intermediate", parentCACertKey, keyPair, validDays, provider)
        keyStore.addOrReplaceKey(certificateKeyName, keyPair.private, privateKeyPassword.toCharArray(), arrayOf(intermediateCertificate.certificate))
        println("New certificate and key pair named $certificateKeyName have been generated")
    }

    private fun generateECDSAKey(keySpecifier: Int, keyName: String, keyGroup: String, provider: CryptoServerProvider, overwrite: Boolean = true) {
        val generateFlag = if (overwrite) {
            println("!!! WARNING: OVERWRITING KEY NAMED $keyName !!!")
            CryptoServerCXI.FLAG_OVERWRITE
        } else {
            0
        }
        val keyAttributes = CryptoServerCXI.KeyAttributes()
        keyAttributes.apply {
            algo = CryptoServerCXI.KEY_ALGO_ECDSA
            group = keyGroup
            specifier = keySpecifier
            export = 0 // deny export
            name = keyName
            setCurve("NIST-P256")
        }
        println("Generating key...")
        val mechanismFlag = CryptoServerCXI.MECH_RND_REAL or CryptoServerCXI.MECH_KEYGEN_UNCOMP
        provider.cryptoServer.generateKey(generateFlag, keyAttributes, mechanismFlag)
    }

    private fun generateEcdsaKeyPair(provider: CryptoServerProvider, keyStore: KeyStore, keyName: String, privateKeyPassword: String): KeyPair {
        generateECDSAKey(keySpecifier, keyName, keyGroup, provider)
        val privateKey = keyStore.getKey(keyName, privateKeyPassword.toCharArray()) as PrivateKey
        val publicKey = keyStore.getCertificate(keyName).publicKey
        return getCleanEcdsaKeyPair(publicKey, privateKey)
    }
}