/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.dev

import com.r3.corda.networkmanage.common.configuration.ConfigFilePathArgsParser
import com.r3.corda.networkmanage.doorman.CORDA_X500_BASE
import net.corda.core.crypto.Crypto
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.apache.logging.log4j.LogManager
import java.io.File
import javax.security.auth.x500.X500Principal
import kotlin.system.exitProcess

private val logger = LogManager.getLogger("com.r3.corda.networkmanage.dev.Main")

/**
 * This is an internal utility method used to generate a DEV certificate store file containing both root and doorman keys/certificates.
 * Additionally, it generates a trust file containing the root certificate.
 *
 * Note: It can be quickly executed in IntelliJ by right-click on the main method. It will generate the keystore and the trustore
 * with settings expected by node running in the dev mode. The files will be generated in the root project directory.
 * Look for the 'certificates' directory.
 */
fun main(args: Array<String>) {
    run(parseParameters(ConfigFilePathArgsParser().parseOrExit(*args)))
}

fun run(configuration: GeneratorConfiguration) {
    configuration.run {
        val keyStoreFile = File("$directory/$keyStoreFileName").toPath()
        keyStoreFile.parent.createDirectories()
        val keyStore = X509KeyStore.fromFile(keyStoreFile, keyStorePass, createNew = true)

        checkCertificateNotInKeyStore(X509Utilities.CORDA_ROOT_CA, keyStore) { exitProcess(1) }
        checkCertificateNotInKeyStore(X509Utilities.CORDA_INTERMEDIATE_CA, keyStore) { exitProcess(1) }

        val rootKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCert = X509Utilities.createSelfSignedCACertificate(
                X500Principal("CN=Corda Root CA,$CORDA_X500_BASE"),
                rootKeyPair)
        keyStore.update {
            setPrivateKey(X509Utilities.CORDA_ROOT_CA, rootKeyPair.private, listOf(rootCert), privateKeyPass)
        }
        logger.info("Root CA keypair and certificate stored in ${keyStoreFile.toAbsolutePath()}.")
        logger.info(rootCert)

        val trustStorePath = directory / trustStoreFileName
        X509KeyStore.fromFile(trustStorePath, trustStorePass, createNew = true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
        }
        logger.info("Trust store for distribution to nodes created in $trustStorePath")

        val doormanKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val cert = X509Utilities.createCertificate(
                CertificateType.INTERMEDIATE_CA,
                rootCert,
                rootKeyPair,
                X500Principal("CN=Corda Doorman CA,$CORDA_X500_BASE"),
                doormanKeyPair.public
        )
        keyStore.update {
            setPrivateKey(X509Utilities.CORDA_INTERMEDIATE_CA, doormanKeyPair.private, listOf(cert, rootCert), privateKeyPass)
        }
        logger.info("Doorman CA keypair and certificate stored in ${keyStoreFile.toAbsolutePath()}.")
        logger.info(cert)
    }
}

private fun checkCertificateNotInKeyStore(certAlias: String, keyStore: X509KeyStore, onFail: () -> Unit) {
    if (certAlias in keyStore) {
        logger.info("$certAlias already exists in keystore, process will now terminate.")
        logger.info(keyStore.getCertificate(certAlias))
        onFail.invoke()
    }
}