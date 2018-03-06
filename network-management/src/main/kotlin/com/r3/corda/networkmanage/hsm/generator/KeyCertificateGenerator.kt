/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.generator

import CryptoServerCXI.CryptoServerCXI.KEY_ALGO_ECDSA
import CryptoServerCXI.CryptoServerCXI.KeyAttributes
import CryptoServerJCE.CryptoServerProvider
import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.cleanEcdsaPublicKey
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.createIntermediateCert
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.createSelfSignedCert
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.getAndInitializeKeyStore
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.retrieveCertAndKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.isDirectory
import net.corda.core.internal.x500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.CertificateType.*
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import org.bouncycastle.asn1.x500.X500Name
import java.nio.file.Path
import java.security.Key
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * Encapsulates logic for key and certificate generation.
 *
 */
class KeyCertificateGenerator(private val parameters: GeneratorParameters) {
    companion object {
        val logger = contextLogger()
    }

    fun generate(provider: CryptoServerProvider, rootProvider: CryptoServerProvider? = null) {
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
            val certChain = if (rootProvider == null) {
                arrayOf(certConfig.generateRootCert(provider, keyPair, trustStoreDirectory, trustStorePassword))
            } else {
                val rootKeyStore = getAndInitializeKeyStore(rootProvider)
                certConfig.generateIntermediateCertChain(rootProvider, keyPair, rootKeyStore)
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
                                                          networkRootTrustStoreDirectory: Path,
                                                          networkRootTrustStorePassword: String): X509Certificate {
        val rootCert = createSelfSignedCert(
                ROOT_CA,
                CordaX500Name.parse(subject).x500Name,
                keyPair,
                validDays,
                provider,
                crlDistributionUrl,
                crlIssuer?.let { X500Name(it) })
        logger.info("Created root cert:\n$rootCert")
        val trustStorePath = networkRootTrustStoreDirectory / "truststore.jks"
        X509KeyStore.fromFile(trustStorePath, networkRootTrustStorePassword, createNew = true).update {
            setCertificate(CORDA_ROOT_CA, rootCert)
        }
        logger.info("Trust store containing the root for distribution to the nodes created in $trustStorePath")
        return rootCert
    }

    private fun CertificateConfiguration.generateIntermediateCertChain(
            provider: CryptoServerProvider,
            keyPair: KeyPair,
            rootKeyStore: KeyStore): Array<X509Certificate> {
        logger.info("Retrieving the root key pair.")
        val rootKeysAndCertChain = retrieveCertAndKeyPair(CORDA_ROOT_CA, rootKeyStore)
        val certificateAndKeyPair = createIntermediateCert(
                certificateType,
                CordaX500Name.parse(subject).x500Name,
                rootKeysAndCertChain,
                keyPair,
                validDays,
                provider,
                crlDistributionUrl,
                crlIssuer?.let { X500Name(it) })
        logger.info("Certificate for $subject created.")
        return arrayOf(certificateAndKeyPair.certificate, rootKeysAndCertChain.certificate)
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
        logger.info("Generating key $keyName.")
        provider.cryptoServer.generateKey(keyOverride, keyAttributes, keyGenMechanism)
        logger.info("$keyName key generated.")
    }

    private fun CertificateConfiguration.generateEcdsaKeyPair(keyName: String, provider: CryptoServerProvider, keyStore: KeyStore): KeyPair {
        generateECDSAKey(keyName, provider)
        val privateKey = keyStore.getKey(keyName, null) as PrivateKey
        val publicKey = keyStore.getCertificate(keyName).publicKey
        return KeyPair(cleanEcdsaPublicKey(publicKey), privateKey)
    }
}