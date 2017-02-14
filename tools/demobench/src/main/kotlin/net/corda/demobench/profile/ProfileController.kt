package net.corda.demobench.profile

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import net.corda.demobench.model.*
import tornadofx.Controller

class ProfileController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val baseDir = jvm.userHome.resolve("demobench")
    private val nodeController by inject<NodeController>()
    private val serviceController by inject<ServiceController>()
    private val chooser = FileChooser()

    init {
        chooser.initialDirectory = baseDir.toFile()
        chooser.extensionFilters.add(ExtensionFilter("DemoBench profiles (*.zip)", "*.zip", "*.ZIP"))
    }

    fun saveAs() {
        log.info("Save as")
    }

    fun save() {
        log.info("Save")
    }

    fun openProfile(): List<NodeConfig>? {
        val chosen = chooser.showOpenDialog(null) ?: return null
        log.info("Selected profile: ${chosen}")

        val configs = LinkedList<NodeConfig>()

        FileSystems.newFileSystem(chosen.toPath(), null).use {
            fs -> fs.rootDirectories.forEach {
                root -> Files.walk(root).forEach {
                    if ((it.nameCount == 2) && ("node.conf" == it.fileName.toString())) {
                        try {
                            configs.add(toNodeConfig(parse(it)))
                        } catch (e: Exception) {
                            log.severe("Failed to parse '$it': ${e.message}")
                            throw e
                        }
                    }
                }
            }
        }

        return configs
    }

    private fun toNodeConfig(config: Config): NodeConfig {
        val artemisPort = config.parsePort("artemisAddress")
        val webPort = config.parsePort("webAddress")
        val h2Port = config.getInt("h2port")
        val extraServices = config.parseExtraServices("extraAdvertisedServiceIds")

        val nodeConfig = NodeConfig(
            baseDir, // temporary value
            config.getString("myLegalName"),
            artemisPort,
            config.getString("nearestCity"),
            webPort,
            h2Port,
            extraServices,
            config.getObjectList("rpcUsers").map { it.unwrapped() }.toList()
        )

        if (config.hasPath("networkMapService")) {
            val nmap = config.getConfig("networkMapService")
            nodeConfig.networkMap = NetworkMapConfig(nmap.getString("legalName"), nmap.parsePort("address"))
        }

        return nodeConfig
    }

    private fun parse(path: Path): Config = Files.newBufferedReader(path).use {
        return ConfigFactory.parseReader(it)
    }

    private fun Config.parsePort(path: String): Int {
        val address = this.getString(path)
        val port = HostAndPort.fromString(address).port
        if (!nodeController.isPortValid(port)) {
            throw IllegalArgumentException("Invalid port $port from '$path'.")
        }
        return port
    }

    private fun Config.parseExtraServices(path: String): List<String> {
        val services = serviceController.services.toSortedSet()
        return this.getString(path).split(",").filter {
            !it.isNullOrEmpty()
        }.map {
            if (!services.contains(it)) {
                throw IllegalArgumentException("Unknown service '$it'.")
            } else {
                it
            }
        }.toList()
    }
}