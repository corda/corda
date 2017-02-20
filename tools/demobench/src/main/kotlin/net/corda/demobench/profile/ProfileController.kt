package net.corda.demobench.profile

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.function.BiPredicate
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import net.corda.demobench.model.*
import tornadofx.Controller

class ProfileController : Controller() {

    private companion object ConfigAcceptor : BiPredicate<Path, BasicFileAttributes> {
        override fun test(p: Path?, attr: BasicFileAttributes?) = "node.conf" == p?.fileName.toString()
    }

    private val jvm by inject<JVMConfig>()
    private val baseDir = jvm.userHome.resolve("demobench")
    private val nodeController by inject<NodeController>()
    private val serviceController by inject<ServiceController>()
    private val chooser = FileChooser()

    init {
        chooser.title = "DemoBench Profiles"
        chooser.initialDirectory = baseDir.toFile()
        chooser.extensionFilters.add(ExtensionFilter("DemoBench profiles (*.zip)", "*.zip", "*.ZIP"))
    }

    fun saveProfile(): Boolean {
        var target = chooser.showSaveDialog(null) ?: return false
        if (target.extension.isEmpty()) {
            target = File(target.parent, target.name + ".zip")
        }

        log.info("Save profile as: $target")

        val configs = nodeController.activeNodes

        FileSystems.newFileSystem(URI.create("jar:" + target.toURI()), mapOf("create" to "true")).use {
            fs -> configs.forEach { it ->
                val nodeDir = Files.createDirectories(fs.getPath(it.key))
                val conf = Files.write(nodeDir.resolve("node.conf"), it.toText().toByteArray(UTF_8))
                log.info("Wrote: $conf")
            }
        }

        return true
    }

    fun openProfile(): List<NodeConfig>? {
        val chosen = chooser.showOpenDialog(null) ?: return null
        log.info("Selected profile: $chosen")

        val configs = LinkedList<NodeConfig>()

        FileSystems.newFileSystem(chosen.toPath(), null).use {
            fs -> fs.rootDirectories.forEach {
                root -> Files.find(root, 2, ConfigAcceptor).forEach {
                    try {
                        // Java seems to "walk" through the ZIP file backwards.
                        // So add new config to the front of the list, so that
                        // our final list is ordered to match the file.
                        configs.addFirst(toNodeConfig(parse(it)))
                        log.info("Loaded: $it")
                    } catch (e: Exception) {
                        log.severe("Failed to parse '$it': ${e.message}")
                        throw e
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