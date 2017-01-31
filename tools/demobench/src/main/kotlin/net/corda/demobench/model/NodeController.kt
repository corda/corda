package net.corda.demobench.model

import com.typesafe.config.ConfigRenderOptions
import java.lang.management.ManagementFactory
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import net.corda.demobench.pty.R3Pty
import tornadofx.Controller

class NodeController : Controller() {
    private val FIRST_PORT = 10000

    private val workDir = Paths.get("work", localDir).toAbsolutePath()
    private val jvm by inject<JVMConfig>()

    private val cordaPath = Paths.get("corda", "corda.jar").toAbsolutePath()
    private val command = jvm.commandFor(cordaPath)

    private val renderOptions = ConfigRenderOptions.defaults().setOriginComments(false)

    private val nodes = ConcurrentHashMap<String, NodeConfig>()
    private val port = AtomicInteger(FIRST_PORT)

    private var networkMapConfig: NetworkMapConfig? = null

    fun validate(nodeData: NodeData): NodeConfig? {
        val config = NodeConfig(
            nodeData.legalName.value.trim(),
            nodeData.artemisPort.value,
            nodeData.nearestCity.value.trim(),
            nodeData.webPort.value,
            nodeData.h2Port.value,
            nodeData.extraServices.value
        )

        if (nodes.putIfAbsent(config.key, config) != null) {
            return null
        }

        // The first node becomes our network map
        chooseNetworkMap(config)

        return config
    }

    val nextPort: Int
        get() { return port.andIncrement }

    fun keyExists(key: String): Boolean {
        return nodes.keys.contains(key)
    }

    fun nameExists(name: String): Boolean {
        return keyExists(toKey(name))
    }

    fun chooseNetworkMap(config: NodeConfig) {
        if (networkMapConfig != null) {
            config.networkMap = networkMapConfig
        } else {
            networkMapConfig = config
            log.info("Network map provided by: " + config.legalName)
        }
    }

    fun runCorda(pty: R3Pty, config: NodeConfig): Boolean {
        val nodeDir = workDir.resolve(config.key).toFile()

        if (nodeDir.mkdirs()) {
            try {
                // Write this nodes configuration file into its working directory.
                val confFile = nodeDir.resolve("node.conf")
                val fileData = config.toFileConfig
                confFile.writeText(fileData.root().render(renderOptions))

                pty.run(command, System.getenv(), nodeDir.toString())
                log.info("Launched node: " + config.legalName)
                return true
            } catch (e: Exception) {
                log.severe("Failed to launch Corda:" + e)
                return false
            }
        } else {
            return false
        }
    }

    private val localDir: String
        get() = SimpleDateFormat("yyyyMMddHHmmss")
                    .format(Date(ManagementFactory.getRuntimeMXBean().startTime))

    init {
        log.info("Working directory: " + workDir)
        log.info("Corda JAR: " + cordaPath)
    }
}
