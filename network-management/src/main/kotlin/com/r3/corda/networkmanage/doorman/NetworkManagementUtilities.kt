/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import net.corda.nodeapi.internal.crypto.X509Utilities.createCertificate
import net.corda.nodeapi.internal.crypto.X509Utilities.createSelfSignedCACertificate
import java.nio.file.Path
import javax.security.auth.x500.X500Principal
import kotlin.system.exitProcess

// TODO The cert subjects need to be configurable
const val CORDA_X500_BASE = "O=R3 HoldCo LLC,OU=Corda,L=New York,C=US"
const val NETWORK_ROOT_TRUSTSTORE_FILENAME = "network-root-truststore.jks"

/** Read password from console, do a readLine instead if console is null (e.g. when debugging in IDE). */
internal fun readPassword(fmt: String): String {
    return if (System.console() != null) {
        String(System.console().readPassword(fmt))
    } else {
        print(fmt)
        readLine() ?: ""
    }
}

// Keygen utilities.
fun generateRootKeyPair(rootStoreFile: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?, networkRootTrustPass: String?) {
    println("Generating Root CA keypair and certificate.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePass ?: readPassword("Root Keystore Password: ")
    // Ensure folder exists.
    rootStoreFile.parent.createDirectories()
    val rootStore = X509KeyStore.fromFile(rootStoreFile, rootKeystorePassword, createNew = true)
    val rootPrivateKeyPassword = rootPrivateKeyPass ?: readPassword("Root Private Key Password: ")

    if (CORDA_ROOT_CA in rootStore) {
        println("$CORDA_ROOT_CA already exists in keystore, process will now terminate.")
        println(rootStore.getCertificate(CORDA_ROOT_CA))
        exitProcess(1)
    }

    val selfSignKey = Crypto.generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)
    // TODO Make the cert subject configurable
    val rootCert = createSelfSignedCACertificate(
            X500Principal("CN=Corda Root CA,$CORDA_X500_BASE"),
            selfSignKey)
    rootStore.update {
        setPrivateKey(CORDA_ROOT_CA, selfSignKey.private, listOf(rootCert), rootPrivateKeyPassword)
    }

    val trustStorePath = (rootStoreFile.parent / "distribute-nodes").createDirectories() / NETWORK_ROOT_TRUSTSTORE_FILENAME

    val networkRootTrustPassword = networkRootTrustPass ?: readPassword("Network Root Trust Store Password: ")

    X509KeyStore.fromFile(trustStorePath, networkRootTrustPassword, createNew = true).update {
        setCertificate(CORDA_ROOT_CA, rootCert)
    }

    println("Trust store for distribution to nodes created in $trustStorePath")
    println("Root CA keypair and certificate stored in ${rootStoreFile.toAbsolutePath()}.")
    println(rootCert)
}

fun generateSigningKeyPairs(keystoreFile: Path, rootStoreFile: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?, keystorePass: String?, caPrivateKeyPass: String?) {
    println("Generating intermediate and network map key pairs and certificates using root key store $rootStoreFile.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePass ?: readPassword("Root key store password: ")
    val rootPrivateKeyPassword = rootPrivateKeyPass ?: readPassword("Root private key password: ")
    val rootKeyStore = X509KeyStore.fromFile(rootStoreFile, rootKeystorePassword)

    val rootKeyPairAndCert = rootKeyStore.getCertificateAndKeyPair(CORDA_ROOT_CA, rootPrivateKeyPassword)

    val keyStorePassword = keystorePass ?: readPassword("Key store Password: ")
    val privateKeyPassword = caPrivateKeyPass ?: readPassword("Private key Password: ")
    // Ensure folder exists.
    keystoreFile.parent.createDirectories()
    val keyStore = X509KeyStore.fromFile(keystoreFile, keyStorePassword, createNew = true)

    fun storeCertIfAbsent(alias: String, certificateType: CertificateType, subject: X500Principal, signatureScheme: SignatureScheme) {
        if (alias in keyStore) {
            println("$alias already exists in keystore:")
            println(keyStore.getCertificate(alias))
            return
        }

        val keyPair = Crypto.generateKeyPair(signatureScheme)
        val cert = createCertificate(
                certificateType,
                rootKeyPairAndCert.certificate,
                rootKeyPairAndCert.keyPair,
                subject,
                keyPair.public
        )

        keyStore.update {
            setPrivateKey(alias, keyPair.private, listOf(cert, rootKeyPairAndCert.certificate), privateKeyPassword)
        }

        println("$certificateType key pair and certificate stored in $keystoreFile.")
        println(cert)
    }

    storeCertIfAbsent(
            CORDA_INTERMEDIATE_CA,
            CertificateType.INTERMEDIATE_CA,
            X500Principal("CN=Corda Doorman CA,$CORDA_X500_BASE"),
            DEFAULT_TLS_SIGNATURE_SCHEME)

    storeCertIfAbsent(
            CORDA_NETWORK_MAP,
            CertificateType.NETWORK_MAP,
            X500Principal("CN=Corda Network Map,$CORDA_X500_BASE"),
            Crypto.EDDSA_ED25519_SHA512)
}
