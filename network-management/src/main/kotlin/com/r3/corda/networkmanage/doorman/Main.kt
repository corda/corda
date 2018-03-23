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

import com.jcabi.manifests.Manifests
import com.r3.corda.networkmanage.common.utils.*
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("com.r3.corda.networkmanage.doorman")

fun main(args: Array<String>) {
    if (Manifests.exists("Doorman-Version")) {
        println("Version: ${Manifests.read("Doorman-Version")}")
    }

    initialiseSerialization()
    val cmdLineOptions = try {
        DoormanArgsParser().parse(*args)
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
        exitProcess(0)
    }

    val config = parseConfig<NetworkManagementServerConfig>(cmdLineOptions.configFile)

    logger.info("Running in ${cmdLineOptions.mode} mode")
    when (cmdLineOptions.mode) {
        Mode.ROOT_KEYGEN -> rootKeyGenMode(cmdLineOptions, config)
        Mode.CA_KEYGEN -> caKeyGenMode(config)
        Mode.DOORMAN -> doormanMode(cmdLineOptions, config)
    }
}

data class NetworkMapStartParams(val signer: LocalSigner?, val config: NetworkMapConfig)

data class NetworkManagementServerStatus(var serverStartTime: Instant = Instant.now(), var lastRequestCheckTime: Instant? = null)

private fun processKeyStore(config: NetworkManagementServerConfig): Pair<CertPathAndKey, LocalSigner>? {
    if (config.keystorePath == null) return null

    // Get password from console if not in config.
    val keyStorePassword = config.keystorePassword ?: readPassword("Key store password: ")
    val privateKeyPassword = config.caPrivateKeyPassword ?: readPassword("Private key password: ")
    val keyStore = X509KeyStore.fromFile(config.keystorePath, keyStorePassword)
    val csrCertPathAndKey = keyStore.getCertPathAndKey(X509Utilities.CORDA_INTERMEDIATE_CA, privateKeyPassword)
    val networkMapSigner = LocalSigner(keyStore.getCertificateAndKeyPair(CORDA_NETWORK_MAP, privateKeyPassword))
    return Pair(csrCertPathAndKey, networkMapSigner)
}

private fun rootKeyGenMode(cmdLineOptions: DoormanCmdLineOptions, config: NetworkManagementServerConfig) {
    generateRootKeyPair(
            config.rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
            config.rootKeystorePassword,
            config.rootPrivateKeyPassword,
            cmdLineOptions.trustStorePassword
    )
}

private fun caKeyGenMode(config: NetworkManagementServerConfig) {
    generateSigningKeyPairs(
            config.keystorePath ?: throw IllegalArgumentException("The 'keystorePath' parameter must be specified when generating keys!"),
            config.rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
            config.rootKeystorePassword,
            config.rootPrivateKeyPassword,
            config.keystorePassword,
            config.caPrivateKeyPassword
    )
}

private fun doormanMode(cmdLineOptions: DoormanCmdLineOptions, config: NetworkManagementServerConfig) {
    val networkManagementServer = NetworkManagementServer(config.dataSourceProperties, config.database)

    if (cmdLineOptions.networkParametersCmd == null) {
        // TODO: move signing to signing server.
        val csrAndNetworkMap = processKeyStore(config)
        if (csrAndNetworkMap != null) {
            logger.info("Starting network management services with local signing")
        }

        val networkMapStartParams = config.networkMap?.let {
            NetworkMapStartParams(csrAndNetworkMap?.second, it)
        }

        networkManagementServer.start(
                config.address,
                csrAndNetworkMap?.first,
                config.doorman,
                config.revocation,
                networkMapStartParams)

        Runtime.getRuntime().addShutdownHook(object : Thread("ShutdownHook") {
            override fun run() {
                networkManagementServer.close()
            }
        })
    } else {
        networkManagementServer.use {
            it.processNetworkParameters(cmdLineOptions.networkParametersCmd)
        }
    }
}
