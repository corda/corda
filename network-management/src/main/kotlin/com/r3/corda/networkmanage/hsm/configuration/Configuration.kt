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

import com.r3.corda.networkmanage.common.utils.toConfigWithOptions
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.div
import net.corda.core.internal.isRegularFile
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Configuration parameters. Those are general configuration parameters shared with both
 * network map and certificate signing requests processes.
 */
data class Parameters(val dataSourceProperties: Properties,
                      val database: DatabaseConfig = DatabaseConfig(),
                      val device: String,
                      val keySpecifier: Int,
                      val networkMap: NetworkMapCertificateParameters? = null,
                      val doorman: DoormanCertificateParameters? = null)

/**
 * Network map signing process specific parameters.
 */
data class NetworkMapCertificateParameters(val username: String,
                                           val keyGroup: String,
                                           val authParameters: AuthenticationParameters)

/**
 * Certificate signing requests process specific parameters.
 */
data class DoormanCertificateParameters(val crlDistributionPoint: String,
                                        val keyGroup:String,
                                        val validDays: Int,
                                        val rootKeyStoreFile: Path,
                                        val rootKeyStorePassword: String,
                                        val authParameters: AuthenticationParameters) {
    fun loadRootKeyStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(rootKeyStoreFile, rootKeyStorePassword, createNew)
    }
}

/**
 * Authentication related parameters.
 */
data class AuthenticationParameters(val mode: AuthMode,
                                    val password: String? = null, // This is either HSM password or key file password, depending on the mode.
                                    val keyFilePath: Path? = null,
                                    val threshold: Int)

/**
 * Parses the list of arguments and produces an instance of [Parameters].
 * @param args list of strings corresponding to program arguments
 * @return instance of Parameters produced from [args]
 */
fun parseParameters(vararg args: String): Parameters {
    val argConfig = args.toConfigWithOptions {
        accepts("basedir", "Overriding configuration filepath, default to current directory.").withRequiredArg().defaultsTo(".").describedAs("filepath")
        accepts("config-file", "Overriding configuration file.").withRequiredArg().describedAs("filepath")
    }

    // The config-file option is changed to configFile
    val configFile = if (argConfig.hasPath("configFile")) {
        Paths.get(argConfig.getString("configFile"))
    } else {
        Paths.get(argConfig.getString("basedir")) / "signing_service.conf"
    }
    require(configFile.isRegularFile()) { "Config file $configFile does not exist" }

    val config = argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))).resolve()
    return config.parseAs(false)
}