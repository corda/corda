/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.registration

import com.r3.corda.networkmanage.registration.ToolOption.RegistrationOption
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.internal.div
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.config.parseAs
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

const val NOTARY_PRIVATE_KEY_ALIAS = "${DevIdentityGenerator.DISTRIBUTED_NOTARY_ALIAS_PREFIX}-private-key"

fun RegistrationOption.runRegistration() {
    println("**********************************************************")
    println("*                                                        *")
    println("*           Notary identity registration tool            *")
    println("*                                                        *")
    println("**********************************************************")
    println()
    println("This tool will create a notary identity certificate signing request using information found in '$configFile'")
    println()

    val config = ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(false))
            .resolve()
            .parseAs<NotaryRegistrationConfig>()

    val sslConfig = object : SSLConfiguration {
        override val keyStorePassword: String  by lazy { config.keyStorePassword ?: readPassword("Node Keystore password:") }
        override val trustStorePassword: String by lazy { config.trustStorePassword ?: readPassword("Node TrustStore password:") }
        val parent = configFile.parent
        override val certificatesDirectory: Path = if (parent != null) parent / "certificates" else Paths.get("certificates")
        override val nodeKeystore: Path get() = config.keystorePath ?: certificatesDirectory/"notaryidentitykeystore.jks"
    }

    NetworkRegistrationHelper(sslConfig,
            config.legalName,
            config.email,
            HTTPNetworkRegistrationService(config.compatibilityZoneURL),
            config.networkRootTrustStorePath,
            config.networkRootTrustStorePassword ?: readPassword("Network trust root password:"),
            NOTARY_PRIVATE_KEY_ALIAS,
            CertRole.SERVICE_IDENTITY).buildKeystore()
}

data class NotaryRegistrationConfig(val legalName: CordaX500Name,
                                    val email: String,
                                    val compatibilityZoneURL: URL,
                                    val networkRootTrustStorePath: Path,
                                    val keyStorePassword: String?,
                                    val networkRootTrustStorePassword: String?,
                                    val trustStorePassword: String?,
                                    val keystorePath: Path?)
