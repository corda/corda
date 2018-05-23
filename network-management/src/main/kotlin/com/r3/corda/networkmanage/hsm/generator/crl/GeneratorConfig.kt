/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.generator.crl

import com.r3.corda.networkmanage.common.utils.SupportedCrlReasons
import com.r3.corda.networkmanage.hsm.generator.UserAuthenticationParameters
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.net.URL
import java.nio.file.Path

/**
 * Holds generator parameters.
 */
data class GeneratorConfig(val hsmHost: String,
                           val hsmPort: Int,
                           val userConfigs: List<UserAuthenticationParameters>,
                           val trustStoreFile: Path,
                           val trustStorePassword: String,
                           val crl: CrlConfig) {
    fun loadTrustStore(): X509KeyStore {
        return X509KeyStore.fromFile(trustStoreFile, trustStorePassword, false)
    }
}

/**
 * Holds CRL specific configuration.
 */
data class CrlConfig(val keyGroup: String,
                     val keySpecifier: Int,
                     val validDays: Long,
                     val crlEndpoint: URL,
                     val indirectIssuer: Boolean,
                     val filePath: Path,
                     val revocations: List<RevocationConfig> = emptyList())

/**
 * Supported revocation reasons:
 * UNSPECIFIED,
 * KEY_COMPROMISE,
 * CA_COMPROMISE,
 * AFFILIATION_CHANGED,
 * SUPERSEDED,
 * CESSATION_OF_OPERATION,
 * PRIVILEGE_WITHDRAWN
 */
data class RevocationConfig(val certificateSerialNumber: String, val dateInMillis: Long, val reason: String) {

    companion object {
        val reasonErrorMessage = "Error when parsing the revocation reason. Allowed values: ${SupportedCrlReasons.values()}"
    }

    init {
        try {
            SupportedCrlReasons.valueOf(reason)
        } catch (e: Exception) {
            throw IllegalArgumentException(reasonErrorMessage)
        }
    }
}

/**
 * Parses a configuration file, which contains all the configuration - i.e. for user and certificate parameters.
 */
fun parseParameters(configFile: Path): GeneratorConfig {
    return ConfigFactory
            .parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))
            .resolve()
            .parseAs(UnknownConfigKeysPolicy.IGNORE::handle)
}