package net.corda.demobench.model

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import org.bouncycastle.asn1.x500.X500Name

import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class InstallFactory : Controller() {

    private val nodeController by inject<NodeController>()
    private val serviceController by inject<ServiceController>()

    @Throws(IOException::class)
    fun toInstallConfig(config: Config, baseDir: Path): InstallConfig {
        val p2pPort = config.parsePort("p2pAddress")
        val rpcPort = config.parsePort("rpcAddress")
        val webPort = config.parsePort("webAddress")
        val h2Port = config.getInt("h2port")
        val extraServices = config.parseExtraServices("extraAdvertisedServiceIds")
        val tempDir = Files.createTempDirectory(baseDir, ".node")

        val nodeConfig = NodeConfig(
                tempDir,
                X500Name(config.getString("myLegalName")),
                p2pPort,
                rpcPort,
                webPort,
                h2Port,
                extraServices,
                config.getObjectList("rpcUsers").map { toUser(it.unwrapped()) }.toList()
        )

        if (config.hasPath("networkMapService")) {
            val nmap = config.getConfig("networkMapService")
            nodeConfig.networkMap = NetworkMapConfig(X500Name(nmap.getString("legalName")), nmap.parsePort("address"))
        } else {
            log.info("Node '${nodeConfig.legalName}' is the network map")
        }

        return InstallConfig(tempDir, nodeConfig)
    }

    private fun Config.parsePort(path: String): Int {
        val address = this.getString(path)
        val port = HostAndPort.fromString(address).port
        require(nodeController.isPortValid(port), { "Invalid port $port from '$path'." })
        return port
    }

    private fun Config.parseExtraServices(path: String): List<String> {
        val services = serviceController.services.toSortedSet()
        return this.getStringList(path)
                .filter { !it.isNullOrEmpty() }
                .map { svc ->
                    require(svc in services, { "Unknown service '$svc'." })
                    svc
                }.toList()
    }

}

/**
 * Wraps the configuration information for a Node
 * which isn't ready to be instantiated yet.
 */
class InstallConfig internal constructor(val baseDir: Path, private val config: NodeConfig) : HasPlugins {
    val key = config.key
    override val pluginDir: Path = baseDir.resolve("plugins")

    fun deleteBaseDir(): Boolean = baseDir.toFile().deleteRecursively()
    fun installTo(installDir: Path) = config.moveTo(installDir)
}
