package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.noneOrSingle
import net.corda.core.utilities.contextLogger
import java.nio.file.Path
import java.security.AccessController.doPrivileged
import java.security.PrivilegedAction

class CordappConfigFileProvider(cordappDirectories: List<Path>) : CordappConfigProvider {
    companion object {
        private val logger = contextLogger()
    }

    private val configDirectories = cordappDirectories.map { (it / "config").createDirectories() }

    override fun getConfigByName(name: String): Config {
        return doPrivileged(PrivilegedAction {
            // TODO There's nothing stopping the same CorDapp jar from occuring in different directories and thus causing
            // conflicts. The cordappDirectories list config option should just be a single cordappDirectory
            val configFile = configDirectories.map { it / "$name.conf" }.noneOrSingle { it.exists() }
            if (configFile != null) {
                logger.info("Found config for cordapp $name in $configFile")
                ConfigFactory.parseFile(configFile.toFile())
            } else {
                logger.info("No config found for cordapp $name in $configDirectories")
                ConfigFactory.empty()
            }
        })
    }
}
