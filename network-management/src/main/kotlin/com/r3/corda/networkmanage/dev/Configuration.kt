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

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.config.parseAs
import java.io.File
import java.nio.file.Path

/**
 * Holds configuration necessary for generating DEV key store and trust store.
 */
data class GeneratorConfiguration(val privateKeyPass: String = DEV_CA_PRIVATE_KEY_PASS,
                                  val keyStorePass: String = DEV_CA_KEY_STORE_PASS,
                                  val keyStoreFileName: String = DEV_CA_KEY_STORE_FILE,
                                  val trustStorePass: String = DEV_CA_TRUST_STORE_PASS,
                                  val trustStoreFileName: String = DEV_CA_TRUST_STORE_FILE,
                                  val directory: Path = DEFAULT_DIRECTORY) {
    companion object {
        val DEFAULT_DIRECTORY = File("./certificates").toPath()
    }
}

/**
 * Parses a configuration file, which contains all the configuration - i.e. for the key store generator.
 */
fun parseParameters(configFile: Path?): GeneratorConfiguration {
    return if (configFile == null) {
        GeneratorConfiguration()
    } else {
        ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))
                .resolve()
                .parseAs()
    }
}