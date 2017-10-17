package net.corda.demobench.model

import com.typesafe.config.Config
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.config.parseAs
import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class InstallFactory : Controller() {
    private val nodeController by inject<NodeController>()

    @Throws(IOException::class)
    fun toInstallConfig(config: Config, baseDir: Path): InstallConfig {
        fun NetworkHostAndPort.checkPort() {
            require(nodeController.isPortValid(port)) { "Invalid port $port" }
        }

        val nodeConfig = config.parseAs<NodeConfig>()
        nodeConfig.p2pAddress.checkPort()
        nodeConfig.rpcAddress.checkPort()
        nodeConfig.webAddress.checkPort()

        val tempDir = Files.createTempDirectory(baseDir, ".node")

        if (nodeConfig.isNetworkMap) {
            log.info("Node '${nodeConfig.myLegalName}' is the network map")
        }

        return InstallConfig(tempDir, NodeConfigWrapper(tempDir, nodeConfig))
    }
}

/**
 * Wraps the configuration information for a Node
 * which isn't ready to be instantiated yet.
 */
class InstallConfig internal constructor(val baseDir: Path, private val config: NodeConfigWrapper) : HasCordapps {
    val key = config.key
    override val cordappsDir: Path = baseDir / "cordapps"

    fun deleteBaseDir(): Boolean = baseDir.toFile().deleteRecursively()
    fun installTo(installDir: Path) = config.copy(baseDir = installDir)
}
