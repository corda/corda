package net.corda.demobench.model

import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import net.corda.demobench.pty.R3Pty
import tornadofx.Controller

class NodeController : Controller() {
    private companion object {
        const val FIRST_PORT = 10000
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
    }

    private val jvm by inject<JVMConfig>()

    private var baseDir = baseDirFor(ManagementFactory.getRuntimeMXBean().startTime)
    private val pluginDir = jvm.applicationDir.resolve("plugins")

    private val bankOfCorda = pluginDir.resolve("bank-of-corda.jar").toFile()

    private val cordaPath = jvm.applicationDir.resolve("corda").resolve("corda.jar")
    private val command = jvm.commandFor(cordaPath)

    private val nodes = LinkedHashMap<String, NodeConfig>()
    private val port = AtomicInteger(FIRST_PORT)

    private var networkMapConfig: NetworkMapConfig? = null

    val activeNodes: List<NodeConfig> get() = nodes.values.filter {
        (it.state == NodeState.RUNNING) || (it.state == NodeState.STARTING)
    }

    init {
        log.info("Base directory: $baseDir")
        log.info("Corda JAR: $cordaPath")
    }

    fun validate(nodeData: NodeData): NodeConfig? {
        val config = NodeConfig(
            baseDir,
            nodeData.legalName.value.trim(),
            nodeData.artemisPort.value,
            nodeData.nearestCity.value.trim(),
            nodeData.webPort.value,
            nodeData.h2Port.value,
            nodeData.extraServices.value
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

    fun isPortAvailable(port: Int): Boolean {
        if (isPortValid(port)) {
            try {
                ServerSocket(port).close()
                return true
            } catch (e: IOException) {
                return false
            }
        } else {
            return false
        }
    }

    fun isPortValid(port: Int): Boolean = (port >= MIN_PORT) && (port <= MAX_PORT)

    fun keyExists(key: String) = nodes.keys.contains(key)

    fun nameExists(name: String) = keyExists(toKey(name))

    fun hasNetworkMap(): Boolean = networkMapConfig != null

    fun chooseNetworkMap(config: NodeConfig) {
        if (hasNetworkMap()) {
            config.networkMap = networkMapConfig
        } else {
            networkMapConfig = config
            log.info("Network map provided by: ${config.legalName}")
        }
    }

    fun runCorda(pty: R3Pty, config: NodeConfig): Boolean {
        val nodeDir = config.nodeDir.toFile()

        if (nodeDir.isDirectory || nodeDir.mkdirs()) {
            try {
                // Write this node's configuration file into its working directory.
                val confFile = nodeDir.resolve("node.conf")
                confFile.writeText(config.toText())

                // Nodes cannot issue cash unless they contain the "Bank of Corda" plugin.
                if (config.isCashIssuer && bankOfCorda.isFile) {
                    log.info("Installing 'Bank of Corda' plugin")
                    bankOfCorda.copyTo(nodeDir.resolve("plugins").resolve(bankOfCorda.name), overwrite=true)
                }

                // Execute the Corda node
                pty.run(command, System.getenv(), nodeDir.toString())
                log.info("Launched node: ${config.legalName}")
                return true
            } catch (e: Exception) {
                log.severe("Failed to launch Corda:" + e)
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

    fun register(config: NodeConfig): Boolean {
        if (nodes.putIfAbsent(config.key, config) != null) {
            return false
        }

        if ((networkMapConfig == null) && config.isNetworkMap()) {
            networkMapConfig = config
        }

        return true
    }

    fun relocate(config: NodeConfig) = config.moveTo(baseDir)

    private fun baseDirFor(time: Long) = jvm.userHome.resolve("demobench").resolve(localFor(time))
    private fun localFor(time: Long) = SimpleDateFormat("yyyyMMddHHmmss").format(Date(time))

}
