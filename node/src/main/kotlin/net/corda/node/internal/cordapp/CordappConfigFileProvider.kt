/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.isDirectory
import net.corda.core.utilities.contextLogger
import java.nio.file.Path
import java.nio.file.Paths

class CordappConfigFileProvider(private val configDir: Path = DEFAULT_CORDAPP_CONFIG_DIR) : CordappConfigProvider {
    companion object {
        val DEFAULT_CORDAPP_CONFIG_DIR = Paths.get("cordapps") / "config"
        const val CONFIG_EXT = ".conf"
        val logger = contextLogger()
    }

    init {
        configDir.createDirectories()
    }

    override fun getConfigByName(name: String): Config {
        val configFile = configDir / "$name$CONFIG_EXT"
        return if (configFile.exists()) {
            check(!configFile.isDirectory()) { "${configFile.toAbsolutePath()} is a directory, expected a config file" }
            logger.info("Found config for cordapp $name in ${configFile.toAbsolutePath()}")
            ConfigFactory.parseFile(configFile.toFile())
        } else {
            logger.info("No config found for cordapp $name in ${configFile.toAbsolutePath()}")
            ConfigFactory.empty()
        }
    }
}
