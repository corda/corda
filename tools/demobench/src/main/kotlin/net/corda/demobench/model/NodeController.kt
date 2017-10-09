package net.corda.demobench.model

import net.corda.core.identity.CordaX500Name
import net.corda.demobench.plugin.PluginController
import net.corda.demobench.pty.R3Pty
import tornadofx.*
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

class NodeController(check: atRuntime = ::checkExists) : Controller() {
    companion object {
        const val firstPort = 10000
        const val minPort = 1024
        const val maxPort = 65535
    }

    private val jvm by inject<JVMConfig>()
    private val pluginController by inject<PluginController>()
    private val serviceController by inject<ServiceController>()

    private var baseDir: Path = baseDirFor(ManagementFactory.getRuntimeMXBean().startTime)
    private val cordaPath: Path = jvm.applicationDir.resolve("corda").resolve("corda.jar")
    private val command = jvm.commandFor(cordaPath).toTypedArray()

    private val nodes = LinkedHashMap<String, NodeConfig>()
    private val port = AtomicInteger(firstPort)

    private var networkMapConfig: NetworkMapConfig? = null

    val activeNodes: List<NodeConfig>
        get() = nodes.values.filter {
            (it.state == NodeState.RUNNING) || (it.state == NodeState.STARTING)
        }

    init {
        log.info("Base directory: $baseDir")
        log.info("Corda JAR: $cordaPath")

        // Check that the Corda capsule is available.
        // We do NOT want to do this during unit testing!
        check(cordaPath, "Cannot find Corda JAR.")
    }

    /**
     * Validate a Node configuration provided by [net.corda.demobench.views.NodeTabView].
     */
    fun validate(nodeData: NodeData): NodeConfig? {
        val location = nodeData.nearestCity.value
        val config = NodeConfig(
                baseDir,
                CordaX500Name(
                        organisation = nodeData.legalName.value.trim(),
                        locality = location.description,
                        country = location.countryCode
                ),
                nodeData.p2pPort.value,
                nodeData.rpcPort.value,
                nodeData.webPort.value,
                nodeData.h2Port.value,
                nodeData.extraServices.map { serviceController.services[it]!! }.toMutableList()
        )

        if (nodes.putIfAbsent(config.key, config) != null) {
            log.warning("Node with key '${config.key}' already exists.")
            return null
        }

        // The first node becomes our network map
        chooseNetworkMap(config)

        return config
    }

    fun dispose(config: NodeConfig) {
        config.state = NodeState.DEAD

        if (config.networkMap == null) {
            log.warning("Network map service (Node '${config.legalName}') has exited.")
        }
    }

    val nextPort: Int get() = port.andIncrement

    fun isPortValid(port: Int) = (port >= minPort) && (port <= maxPort)

    fun keyExists(key: String) = nodes.keys.contains(key)

    fun nameExists(name: String) = keyExists(name.toKey())

    fun hasNetworkMap(): Boolean = networkMapConfig != null

    private fun chooseNetworkMap(config: NodeConfig) {
        if (hasNetworkMap()) {
            config.networkMap = networkMapConfig
        } else {
            networkMapConfig = config
            log.info("Network map provided by: ${config.legalName}")
        }
    }

    fun runCorda(pty: R3Pty, config: NodeConfig): Boolean {
        val nodeDir = config.nodeDir.toFile()

        if (nodeDir.forceDirectory()) {
            try {
                // Install any built-in plugins into the working directory.
                pluginController.populate(config)

                // Write this node's configuration file into its working directory.
                val confFile = nodeDir.resolve("node.conf")
                confFile.writeText(config.toText())

                // Execute the Corda node
                val cordaEnv = System.getenv().toMutableMap().apply {
                    jvm.setCapsuleCacheDir(this)
                }
                pty.run(command, cordaEnv, nodeDir.toString())
                log.info("Launched node: ${config.legalName}")
                return true
            } catch (e: Exception) {
                log.log(Level.SEVERE, "Failed to launch Corda: ${e.message}", e)
                return false
            }
        } else {
            return false
        }
    }

    fun reset() {
        baseDir = baseDirFor(System.currentTimeMillis())
        log.info("Changed base directory: $baseDir")

        // Wipe out any knowledge of previous nodes.
        networkMapConfig = null
        nodes.clear()
    }

    /**
     * Add a [NodeConfig] object that has been loaded from a profile.
     */
    fun register(config: NodeConfig): Boolean {
        if (nodes.putIfAbsent(config.key, config) != null) {
            return false
        }

        updatePort(config)

        if ((networkMapConfig == null) && config.isNetworkMap()) {
            networkMapConfig = config
        }

        return true
    }

    /**
     * Creates a node directory that can host a running instance of Corda.
     */
    @Throws(IOException::class)
    fun install(config: InstallConfig): NodeConfig {
        val installed = config.installTo(baseDir)

        pluginController.userPluginsFor(config).forEach {
            val pluginDir = Files.createDirectories(installed.pluginDir)
            val plugin = Files.copy(it, pluginDir.resolve(it.fileName.toString()))
            log.info("Installed: $plugin")
        }

        if (!config.deleteBaseDir()) {
            log.warning("Failed to remove '${config.baseDir}'")
        }

        return installed
    }

    private fun updatePort(config: NodeConfig) {
        val nextPort = 1 + arrayOf(config.p2pPort, config.rpcPort, config.webPort, config.h2Port).max() as Int
        port.getAndUpdate { Math.max(nextPort, it) }
    }

    private fun baseDirFor(time: Long): Path = jvm.dataHome.resolve(localFor(time))
    private fun localFor(time: Long) = SimpleDateFormat("yyyyMMddHHmmss").format(Date(time))

}
