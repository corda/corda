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
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.*
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (Manifests.exists("Doorman-Version")) {
        println("Version: ${Manifests.read("Doorman-Version")}")
    }

    val parameters = try {
        parseParameters(*args)
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
        exitProcess(0)
    }

    // TODO Use the logger for this and elsewhere in this file.
    println("Running in ${parameters.mode} mode")
    when (parameters.mode) {
        Mode.ROOT_KEYGEN -> parameters.rootKeyGenMode()
        Mode.CA_KEYGEN -> parameters.caKeyGenMode()
        Mode.DOORMAN -> parameters.doormanMode()
    }
}

data class NetworkMapStartParams(val signer: LocalSigner?, val updateNetworkParameters: NetworkParameters?, val config: NetworkMapConfig)

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

private fun NetworkManagementServerConfig.rootKeyGenMode() {
    generateRootKeyPair(
            rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
            rootKeystorePassword,
            rootPrivateKeyPassword,
            trustStorePassword
    )
}

private fun NetworkManagementServerConfig.caKeyGenMode() {
    generateSigningKeyPairs(
            keystorePath ?: throw IllegalArgumentException("The 'keystorePath' parameter must be specified when generating keys!"),
            rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
            rootKeystorePassword,
            rootPrivateKeyPassword,
            keystorePassword,
            caPrivateKeyPassword
    )
}

private fun NetworkManagementServerConfig.doormanMode() {
    initialiseSerialization()
    val persistence = configureDatabase(dataSourceProperties, database)
    // TODO: move signing to signing server.
    val csrAndNetworkMap = processKeyStore(this)

    if (csrAndNetworkMap != null) {
        println("Starting network management services with local signing")
    }

    val networkManagementServer = NetworkManagementServer()
    val networkParameters = updateNetworkParameters?.let {
        // TODO This check shouldn't be needed. Fix up the config design.
        requireNotNull(networkMap) { "'networkMap' config is required for applying network parameters" }
        println("Parsing network parameters from '${it.toAbsolutePath()}'...")
        parseNetworkParametersConfig(it).toNetworkParameters(modifiedTime = Instant.now(), epoch = 1)
    }
    val networkMapStartParams = networkMap?.let {
        NetworkMapStartParams(csrAndNetworkMap?.second, networkParameters, it)
    }

    networkManagementServer.start(NetworkHostAndPort(host, port), persistence, csrAndNetworkMap?.first, doorman, networkMapStartParams)

    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        networkManagementServer.close()
    })
}
