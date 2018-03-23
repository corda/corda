/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm

import com.jcabi.manifests.Manifests
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.common.utils.initialiseSerialization
import com.r3.corda.networkmanage.common.utils.parseConfig
import com.r3.corda.networkmanage.hsm.configuration.SigningServiceArgsParser
import com.r3.corda.networkmanage.hsm.configuration.SigningServiceConfig
import com.r3.corda.networkmanage.hsm.processor.CrrProcessor
import com.r3.corda.networkmanage.hsm.processor.CsrProcessor
import com.r3.corda.networkmanage.hsm.processor.NetworkMapProcessor
import org.apache.logging.log4j.LogManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.crypto.Cipher
import kotlin.system.exitProcess

private val logger = LogManager.getLogger("com.r3.corda.networkmanage.hsm.Main")

fun main(args: Array<String>) {
    if (Manifests.exists("Signing-Service-Version")) {
        println("Signing Service Version: ${Manifests.read("Signing-Service-Version")}")
    }

    val cmdLineOptions = try {
        SigningServiceArgsParser().parse(*args)
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
        exitProcess(0)
    }

    val config = parseConfig<SigningServiceConfig>(cmdLineOptions.configFile)

    // Validate
    // Grabbed from https://stackoverflow.com/questions/7953567/checking-if-unlimited-cryptography-is-available
    require(Cipher.getMaxAllowedKeyLength("AES") >= 256) {
        "Unlimited Strength Jurisdiction Policy Files must be installed, see http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html"
    }

    // Ensure the BouncyCastle provider is installed
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
    }

    initialiseSerialization()
    // Create DB connection.
    val persistence = configureDatabase(config.dataSourceProperties, config.database)
    if (config.networkMap != null) {
        NetworkMapProcessor(config.networkMap, config.device, config.keySpecifier, persistence).run()
    } else if (config.doorman != null) {
        CsrProcessor(config.doorman, config.device, config.keySpecifier, persistence).showMenu()
    } else if (config.doorman != null) {
        CrrProcessor(config.doorman, config.device, config.keySpecifier).showMenu()
    }
}
