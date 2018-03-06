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

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.hsm.generator.CommandLineOptions
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.OptionParser
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.config.parseAs
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

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
 * Parses dev generator command line options.
 */
fun parseCommandLine(vararg args: String): CommandLineOptions? {
    val optionParser = OptionParser()
    val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .describedAs("filepath")
    val helpOption = optionParser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp()

    val optionSet = optionParser.parse(*args)
    // Print help and exit on help option.
    if (optionSet.has(helpOption)) {
        throw ShowHelpException(optionParser)
    }
    return if (optionSet.has(configFileArg)) {
        CommandLineOptions(Paths.get(optionSet.valueOf(configFileArg)).toAbsolutePath())
    } else {
        null
    }
}

/**
 * Parses a configuration file, which contains all the configuration - i.e. for the key store generator.
 */
fun parseParameters(configFile: Path?): GeneratorConfiguration {
    return if (configFile == null) {
        GeneratorConfiguration()
    } else {
        ConfigFactory
                .parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))
                .resolve()
                .parseAs()
    }
}