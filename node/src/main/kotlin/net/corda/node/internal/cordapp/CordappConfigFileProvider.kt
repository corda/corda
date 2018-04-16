package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.utilities.loggerFor
import java.io.File

class CordappConfigFileProvider(val configDir: File = DEFAULT_CORDAPP_CONFIG_DIR) : CordappConfigProvider {
    companion object {
        val DEFAULT_CORDAPP_CONFIG_DIR = File("cordapps/config")
        val CONFIG_EXT = ".conf"
        val logger = loggerFor<CordappConfigFileProvider>()
    }

    init {
        configDir.mkdirs()
    }

    override fun getConfigByName(name: String): Config {
        val configFile = File(configDir, name + CONFIG_EXT)
        return if (configFile.exists()) {
            if (configFile.isDirectory) {
                throw IllegalStateException("File at ${configFile.absolutePath} is a directory, expected a config file")
            } else {
                logger.info("Found config for cordapp $name in ${configFile.absolutePath}")
                ConfigFactory.parseFile(configFile)
            }
        } else {
            logger.info("No config found for cordapp $name in ${configFile.absolutePath}")
            ConfigFactory.empty()
        }
    }

}