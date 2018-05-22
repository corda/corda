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

import com.r3.corda.networkmanage.common.configuration.ConfigFilePathArgsParser
import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.utils.mapCryptoServerException
import net.corda.nodeapi.internal.crypto.CertificateType.ROOT_CA
import org.apache.logging.log4j.LogManager

private val logger = LogManager.getLogger("com.r3.corda.networkmanage.hsm.generator.Main")

fun main(args: Array<String>) {
    run(parseParameters(ConfigFilePathArgsParser().parseOrExit(*args)))
}

fun run(parameters: GeneratorParameters) {
    parameters.run {
        val providerConfig = CryptoServerProviderConfig(
                Device = "$hsmPort@$hsmHost",
                KeySpecifier = certConfig.keySpecifier,
                KeyGroup = certConfig.keyGroup,
                StoreKeysExternal = certConfig.storeKeysExternal)
        try {
            AutoAuthenticator(providerConfig, userConfigs).connectAndAuthenticate { provider ->
                val generator = KeyCertificateGenerator(this)
                logger.info("Generating ${certConfig.certificateType.name} certificate.")
                if (certConfig.certificateType == ROOT_CA) {
                    generator.generate(provider)
                } else {
                    requireNotNull(certConfig.rootKeyGroup)
                    val rootProviderConfig = CryptoServerProviderConfig(
                            Device = "$hsmPort@$hsmHost",
                            KeySpecifier = certConfig.keySpecifier,
                            KeyGroup = requireNotNull(certConfig.rootKeyGroup) { "rootKeyGroup needs to be specified." },
                            StoreKeysExternal = certConfig.storeKeysExternal)
                    AutoAuthenticator(rootProviderConfig, userConfigs).connectAndAuthenticate { rootProvider ->
                        generator.generate(provider, rootProvider)
                        rootProvider.logoff()
                    }
                }
                provider.logoff()
            }
        } catch (e: Exception) {
            logger.error("HSM certificate generation error.", mapCryptoServerException(e))
        }
    }
}