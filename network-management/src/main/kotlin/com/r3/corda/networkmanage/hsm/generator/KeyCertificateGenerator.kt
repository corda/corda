package com.r3.corda.networkmanage.hsm.generator

import CryptoServerCXI.CryptoServerCXI.KEY_ALGO_ECDSA
import CryptoServerCXI.CryptoServerCXI.KeyAttributes
import CryptoServerJCE.CryptoServerProvider
import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.createIntermediateCert
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.createSelfSignedCACert
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.getAndInitializeKeyStore
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.getCleanEcdsaKeyPair
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.retrieveKeysAndCertificateChain
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.isDirectory
import net.corda.core.internal.x500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.*
import net.corda.nodeapi.internal.crypto.CertificateType.*
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import java.nio.file.Path
import java.security.Key
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate

data class CertificateNameAndPass(val certificateName: String, val privateKeyPassword: String)
/**
 * Encapsulates logic for key and certificate generation.
 *
 */
class KeyCertificateGenerator(private val parameters: GeneratorParameters) {
    companion object {
        val logger = contextLogger()
    }

    fun generate(provider: CryptoServerProvider) {
        parameters.run {
            require(trustStoreDirectory.isDirectory()) { "trustStoreDirectory must point to a directory." }
            val keyName = when (certConfig.certificateType) {
                ROOT_CA -> CORDA_ROOT_CA
                INTERMEDIATE_CA -> CORDA_INTERMEDIATE_CA
                NETWORK_MAP -> CORDA_NETWORK_MAP
                else -> throw IllegalArgumentException("Invalid certificate type. ${certConfig.certificateType}")
            }
            val keyStore = getAndInitializeKeyStore(provider)
            val keyPair = certConfig.generateEcdsaKeyPair(keyName, provider, keyStore)
            val certChain = if (certConfig.certificateType == ROOT_CA) {
                certConfig.generateRootCert(provider, keyPair, trustStoreDirectory, trustStorePassword)
            } else {
                certConfig.generateIntermediateCert(provider, keyPair, keyStore)
            }
            keyStore.addOrReplaceKey(keyName, keyPair.private, null, certChain)
            logger.info("New certificate and key pair named $keyName have been generated and stored in HSM")
        }
    }

    // TODO remove this and modify the node-api internal version of this method - nullable password
    fun KeyStore.addOrReplaceKey(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>) {
        if (containsAlias(alias)) {
            this.deleteEntry(alias)
        }
        this.setKeyEntry(alias, key, password, chain)
    }

    private fun CertificateConfiguration.generateRootCert(provider: CryptoServerProvider,
                                                          keyPair: KeyPair,
                                                          trustStoreDirectory: Path,
                                                          trustStorePassword: String): Array<X509Certificate> {
        val certificate = createSelfSignedCACert(ROOT_CA,
                CordaX500Name.parse(subject).x500Name,
                keyPair,
                validDays,
                provider,
                crlDistributionUrl,
                crlIssuer).certificate
        val trustStorePath = trustStoreDirectory / "truststore.jks"
        val trustStore = loadOrCreateKeyStore(trustStorePath, trustStorePassword)
        logger.info("Trust store for distribution to nodes created in $trustStore")
        trustStore.addOrReplaceCertificate(CORDA_ROOT_CA, certificate)
        logger.info("Certificate $CORDA_ROOT_CA has been added to $trustStore")
        trustStore.save(trustStorePath, trustStorePassword)
        logger.info("Trust store has been persisted. Ready for distribution.")
        return arrayOf(certificate)
    }

    private fun CertificateConfiguration.generateIntermediateCert(
            provider: CryptoServerProvider,
            keyPair: KeyPair,
            keyStore: KeyStore): Array<X509Certificate> {
        val rootKeysAndCertChain = retrieveKeysAndCertificateChain(CORDA_ROOT_CA, keyStore)
        val certificateAndKeyPair = createIntermediateCert(
                certificateType,
                CordaX500Name.parse(subject).x500Name,
                CertificateAndKeyPair(rootKeysAndCertChain.certificateChain.first(), rootKeysAndCertChain.keyPair),
                keyPair,
                validDays,
                provider,
                crlDistributionUrl,
                crlIssuer)
        return arrayOf(certificateAndKeyPair.certificate, *rootKeysAndCertChain.certificateChain)
    }

    private fun CertificateConfiguration.generateECDSAKey(keyName: String, provider: CryptoServerProvider) {
        val keyAttributes = KeyAttributes()
        keyAttributes.apply {
            algo = KEY_ALGO_ECDSA
            group = keyGroup
            specifier = keySpecifier
            export = keyExport
            name = keyName
            setCurve(keyCurve)
        }
        logger.info("Generating key $keyName")
        provider.cryptoServer.generateKey(keyOverride, keyAttributes, keyGenMechanism)
    }

    private fun CertificateConfiguration.generateEcdsaKeyPair(keyName: String, provider: CryptoServerProvider, keyStore: KeyStore): KeyPair {
        generateECDSAKey(keyName, provider)
        val privateKey = keyStore.getKey(keyName, null) as PrivateKey
        val publicKey = keyStore.getCertificate(keyName).publicKey
        return getCleanEcdsaKeyPair(publicKey, privateKey)
    }
}