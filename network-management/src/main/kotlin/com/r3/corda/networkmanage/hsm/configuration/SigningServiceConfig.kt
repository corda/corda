/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.configuration

import com.google.common.primitives.Booleans
import com.r3.corda.networkmanage.common.utils.ArgsParser
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import joptsimple.OptionSet
import joptsimple.util.PathConverter
import joptsimple.util.PathProperties
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Configuration parameters. Those are general configuration parameters shared with both
 * network map and certificate signing requests processes.
 */
data class SigningServiceConfig(val dataSourceProperties: Properties,
                                val database: DatabaseConfig = DatabaseConfig(),
                                val device: String,
                                val keySpecifier: Int,
                                val networkMap: NetworkMapCertificateConfig? = null,
                                val doorman: DoormanCertificateConfig? = null) {
    init {
        require(Booleans.countTrue(doorman != null, networkMap != null) == 1) {
            "Exactly one networkMap or doorman configuration needs to be specified."
        }
    }
}

/**
 * Network map signing process specific parameters.
 */
data class NetworkMapCertificateConfig(val username: String,
                                       val keyGroup: String,
                                       val authParameters: AuthParametersConfig)

/**
 * Certificate signing requests process specific parameters.
 */
data class DoormanCertificateConfig(val crlDistributionPoint: URL,
                                    val crlServerSocketAddress: NetworkHostAndPort,
                                    val crlUpdatePeriod: Long,
                                    val mode: ManualMode,
                                    val keyGroup: String,
                                    val validDays: Int,
                                    val rootKeyStoreFile: Path,
                                    val rootKeyStorePassword: String,
                                    val authParameters: AuthParametersConfig) {
    fun loadRootKeyStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(rootKeyStoreFile, rootKeyStorePassword, createNew)
    }
}

enum class ManualMode {
    CRL, // Run manual mode for the certificate revocation list.
    CSR  // Run manual mode for the certificate signing requests.
}

/**
 * Authentication related parameters.
 */
data class AuthParametersConfig(val mode: AuthMode,
                                val password: String? = null, // This is either HSM password or key file password, depending on the mode.
                                val keyFilePath: Path? = null,
                                val threshold: Int)

class SigningServiceArgsParser : ArgsParser<SigningServiceCmdLineOptions>() {
    private val baseDirArg = optionParser
            .accepts("basedir", "Overriding configuration filepath, default to current directory.")
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.DIRECTORY_EXISTING))
            .defaultsTo(Paths.get("."))
    private val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))
            .required()

    override fun parse(optionSet: OptionSet): SigningServiceCmdLineOptions {
        val baseDir = optionSet.valueOf(baseDirArg)
        val configFile = optionSet.valueOf(configFileArg)
        return SigningServiceCmdLineOptions(baseDir, configFile)
    }
}

data class SigningServiceCmdLineOptions(val baseDir: Path, val configFile: Path)
